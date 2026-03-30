package com.orderbook.matching.service

import com.orderbook.common.events.AcceptedOrderCommand
import com.orderbook.common.events.AmendOrderCommand
import com.orderbook.common.events.CancelOrderCommand
import com.orderbook.common.events.MatchingResult
import com.orderbook.common.events.Order
import com.orderbook.common.events.OrderBookSnapshot
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.OrderSide
import com.orderbook.common.events.OrderStatus
import com.orderbook.common.events.PriceLevel
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.Trade
import com.orderbook.common.events.TradeId
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class OrderBookMatchingService {
    private val books = ConcurrentHashMap<Symbol, SymbolOrderBook>()

    suspend fun process(command: AcceptedOrderCommand): MatchingResult =
        book(command.symbol).process(command)

    suspend fun cancel(command: CancelOrderCommand): Order? =
        books.values.firstNotNullOfOrNull { it.cancel(command.orderId) }

    suspend fun amend(command: AmendOrderCommand): MatchingResult? =
        books.values.firstNotNullOfOrNull { it.amend(command) }

    fun snapshot(symbol: Symbol): OrderBookSnapshot = book(symbol).snapshot()

    private fun book(symbol: Symbol): SymbolOrderBook = books.computeIfAbsent(symbol) { SymbolOrderBook(symbol) }
}

private class SymbolOrderBook(
    private val symbol: Symbol
) {
    private val buyOrders = mutableListOf<Order>()
    private val sellOrders = mutableListOf<Order>()

    @Synchronized
    fun process(command: AcceptedOrderCommand): MatchingResult {
        var incoming = Order(
            orderId = command.orderId,
            accountId = command.accountId,
            clientOrderId = command.clientOrderId,
            symbol = command.symbol,
            side = command.side,
            price = command.price,
            quantity = command.quantity,
            filledQuantity = command.filledQuantity,
            status = OrderStatus.ACCEPTED,
            createdAt = command.occurredAt,
            updatedAt = Instant.now()
        )
        val trades = mutableListOf<Trade>()
        val oppositeBook = if (command.side == OrderSide.BUY) sellOrders else buyOrders

        while (incoming.remainingQuantity > 0) {
            val resting = bestMatchFor(incoming.side, incoming.price, oppositeBook) ?: break
            val tradeQty = minOf(incoming.remainingQuantity, resting.remainingQuantity)
            val trade = Trade(
                tradeId = TradeId(UUID.randomUUID().toString()),
                symbol = incoming.symbol,
                buyOrderId = if (incoming.side == OrderSide.BUY) incoming.orderId else resting.orderId,
                sellOrderId = if (incoming.side == OrderSide.SELL) incoming.orderId else resting.orderId,
                buyAccountId = if (incoming.side == OrderSide.BUY) incoming.accountId else resting.accountId,
                sellAccountId = if (incoming.side == OrderSide.SELL) incoming.accountId else resting.accountId,
                price = resting.price,
                quantity = tradeQty,
                occurredAt = Instant.now()
            )
            trades += trade
            incoming = incoming.copy(
                filledQuantity = incoming.filledQuantity + tradeQty,
                status = nextStatus(incoming.quantity, incoming.filledQuantity + tradeQty),
                updatedAt = trade.occurredAt
            )

            val restingUpdated = resting.copy(
                filledQuantity = resting.filledQuantity + tradeQty,
                status = nextStatus(resting.quantity, resting.filledQuantity + tradeQty),
                updatedAt = trade.occurredAt
            )
            oppositeBook.removeIf { it.orderId == resting.orderId }
            if (restingUpdated.remainingQuantity > 0) {
                oppositeBook += restingUpdated
            }
        }

        if (incoming.remainingQuantity > 0) {
            restingBookFor(incoming.side).removeIf { it.orderId == incoming.orderId }
            restingBookFor(incoming.side) += incoming
        }

        return MatchingResult(
            order = incoming,
            trades = trades,
            orderBookSnapshot = snapshot()
        )
    }

    @Synchronized
    fun cancel(orderId: OrderId): Order? {
        val buy = buyOrders.firstOrNull { it.orderId == orderId }
        if (buy != null) {
            buyOrders.removeIf { it.orderId == orderId }
            return buy.copy(status = OrderStatus.CANCELED, updatedAt = Instant.now())
        }

        val sell = sellOrders.firstOrNull { it.orderId == orderId }
        if (sell != null) {
            sellOrders.removeIf { it.orderId == orderId }
            return sell.copy(status = OrderStatus.CANCELED, updatedAt = Instant.now())
        }

        return null
    }

    @Synchronized
    fun amend(command: AmendOrderCommand): MatchingResult? {
        val current = buyOrders.firstOrNull { it.orderId == command.orderId }
            ?: sellOrders.firstOrNull { it.orderId == command.orderId }
            ?: return null
        if (command.newQuantity < current.filledQuantity || command.newPrice <= BigDecimal.ZERO) {
            return null
        }
        cancel(command.orderId)
        return process(
            AcceptedOrderCommand(
                orderId = current.orderId,
                accountId = current.accountId,
                clientOrderId = current.clientOrderId,
                symbol = current.symbol,
                side = current.side,
                price = command.newPrice,
                quantity = command.newQuantity,
                filledQuantity = current.filledQuantity,
                traceId = command.traceId
            )
        )
    }

    @Synchronized
    fun snapshot(): OrderBookSnapshot =
        OrderBookSnapshot(
            symbol = symbol,
            bids = buyOrders
                .filter { it.remainingQuantity > 0 }
                .groupBy { it.price }
                .entries
                .sortedByDescending { it.key }
                .map { PriceLevel(it.key, it.value.sumOf(Order::remainingQuantity)) },
            asks = sellOrders
                .filter { it.remainingQuantity > 0 }
                .groupBy { it.price }
                .entries
                .sortedBy { it.key }
                .map { PriceLevel(it.key, it.value.sumOf(Order::remainingQuantity)) },
            lastUpdatedAt = Instant.now()
        )

    private fun bestMatchFor(side: OrderSide, price: BigDecimal, oppositeBook: List<Order>): Order? {
        val candidates = oppositeBook.filter {
            when (side) {
                OrderSide.BUY -> it.price <= price
                OrderSide.SELL -> it.price >= price
            }
        }
        return when (side) {
            OrderSide.BUY -> candidates.sortedWith(compareBy<Order> { it.price }.thenBy { it.createdAt }).firstOrNull()
            OrderSide.SELL -> candidates.sortedWith(compareByDescending<Order> { it.price }.thenBy { it.createdAt }).firstOrNull()
        }
    }

    private fun nextStatus(quantity: Long, filledQuantity: Long): OrderStatus =
        when {
            filledQuantity <= 0 -> OrderStatus.ACCEPTED
            filledQuantity < quantity -> OrderStatus.PARTIALLY_FILLED
            else -> OrderStatus.FILLED
        }

    private fun restingBookFor(side: OrderSide): MutableList<Order> =
        if (side == OrderSide.BUY) buyOrders else sellOrders
}
