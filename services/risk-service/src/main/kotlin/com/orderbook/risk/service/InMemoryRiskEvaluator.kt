package com.orderbook.risk.service

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.AccountSnapshot
import com.orderbook.common.events.BalanceSnapshot
import com.orderbook.common.events.ClientOrderId
import com.orderbook.common.events.Order
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.OrderSide
import com.orderbook.common.events.OrderStatus
import com.orderbook.common.events.PlaceOrderCommand
import com.orderbook.common.events.PositionSnapshot
import com.orderbook.common.events.RiskDecision
import com.orderbook.common.events.RiskRejectReason
import com.orderbook.common.events.Symbol
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class InMemoryRiskEvaluator {
    private val accounts = ConcurrentHashMap<AccountId, RiskAccountState>(
        mapOf(
            AccountId("ACC-001") to seededAccountState(
                accountId = AccountId("ACC-001"),
                cashBalance = BigDecimal("1000000"),
                positions = mapOf(Symbol("AAPL") to 300, Symbol("MSFT") to 200)
            ),
            AccountId("ACC-002") to seededAccountState(
                accountId = AccountId("ACC-002"),
                cashBalance = BigDecimal("1000000"),
                positions = mapOf(Symbol("AAPL") to 150, Symbol("TSLA") to 50)
            )
        )
    )

    @Synchronized
    fun evaluate(command: PlaceOrderCommand): RiskDecision {
        if (command.quantity <= 0) {
            return RiskDecision.Rejected(RiskRejectReason.INVALID_QUANTITY, "Quantity must be positive")
        }
        if (command.price <= BigDecimal.ZERO) {
            return RiskDecision.Rejected(RiskRejectReason.INVALID_PRICE, "Price must be positive")
        }

        val state = accounts[command.accountId] ?: return RiskDecision.Rejected(
            RiskRejectReason.ACCOUNT_NOT_FOUND,
            "Account ${command.accountId.value} not found"
        )

        if (state.blocked) {
            return RiskDecision.Rejected(RiskRejectReason.ACCOUNT_BLOCKED, "Account is blocked")
        }

        state.pendingReservations[command.clientOrderId]?.let {
            return RiskDecision.Approved(state.snapshot())
        }

        return when (command.side) {
            OrderSide.BUY -> {
                val requiredCash = command.price.multiply(BigDecimal.valueOf(command.quantity))
                if (state.snapshot().availableCash < requiredCash) {
                    RiskDecision.Rejected(RiskRejectReason.INSUFFICIENT_CASH, "Insufficient available cash balance")
                } else {
                    state.pendingReservations[command.clientOrderId] = PendingReservation(
                        clientOrderId = command.clientOrderId,
                        symbol = command.symbol,
                        side = command.side,
                        reservedCash = requiredCash,
                        reservedQuantity = 0,
                        createdAt = Instant.now()
                    )
                    RiskDecision.Approved(state.snapshot())
                }
            }

            OrderSide.SELL -> {
                if (state.snapshot().availablePosition(command.symbol) < command.quantity) {
                    RiskDecision.Rejected(RiskRejectReason.INSUFFICIENT_POSITION, "Insufficient available position")
                } else {
                    state.pendingReservations[command.clientOrderId] = PendingReservation(
                        clientOrderId = command.clientOrderId,
                        symbol = command.symbol,
                        side = command.side,
                        reservedCash = BigDecimal.ZERO,
                        reservedQuantity = command.quantity,
                        createdAt = Instant.now()
                    )
                    RiskDecision.Approved(state.snapshot())
                }
            }
        }
    }

    @Synchronized
    fun applyBalanceUpdate(balance: BalanceSnapshot, positions: List<PositionSnapshot>) {
        val state = accounts.computeIfAbsent(balance.accountId) { seededAccountState(balance.accountId, BigDecimal.ZERO, emptyMap()) }
        if (state.balanceUpdatedAt != null && state.balanceUpdatedAt!!.isAfter(balance.updatedAt)) {
            return
        }

        state.cashBalance = balance.cashBalance
        state.balanceUpdatedAt = balance.updatedAt
        positions.forEach { position ->
            if (position.quantity == 0L) {
                state.positions.remove(position.symbol)
            } else {
                state.positions[position.symbol] = position.quantity
            }
        }
    }

    @Synchronized
    fun applyOrder(order: Order) {
        val state = accounts.computeIfAbsent(order.accountId) { seededAccountState(order.accountId, BigDecimal.ZERO, emptyMap()) }
        val current = state.orderReservations[order.orderId]
        if (current != null && order.version <= current.version) {
            return
        }

        state.pendingReservations.remove(order.clientOrderId)
        val nextReservation = reservationFor(order)
        if (nextReservation == null) {
            state.orderReservations.remove(order.orderId)
            return
        }

        state.orderReservations[order.orderId] = nextReservation
    }

    @Synchronized
    fun snapshot(accountId: AccountId): AccountSnapshot? = accounts[accountId]?.snapshot()

    private fun reservationFor(order: Order): OrderReservation? {
        if (order.remainingQuantity <= 0 || order.status in terminalStatuses) {
            return null
        }

        return when (order.side) {
            OrderSide.BUY -> OrderReservation(
                orderId = order.orderId,
                clientOrderId = order.clientOrderId,
                symbol = order.symbol,
                side = order.side,
                reservedCash = order.price.multiply(BigDecimal.valueOf(order.remainingQuantity)),
                reservedQuantity = 0,
                version = order.version
            )

            OrderSide.SELL -> OrderReservation(
                orderId = order.orderId,
                clientOrderId = order.clientOrderId,
                symbol = order.symbol,
                side = order.side,
                reservedCash = BigDecimal.ZERO,
                reservedQuantity = order.remainingQuantity,
                version = order.version
            )
        }
    }

    private fun seededAccountState(
        accountId: AccountId,
        cashBalance: BigDecimal,
        positions: Map<Symbol, Long>
    ): RiskAccountState = RiskAccountState(
        accountId = accountId,
        cashBalance = cashBalance,
        positions = ConcurrentHashMap(positions),
        balanceUpdatedAt = Instant.now()
    )

    private companion object {
        val terminalStatuses = setOf(
            OrderStatus.REJECTED,
            OrderStatus.FILLED,
            OrderStatus.CANCELED
        )
    }
}

private data class RiskAccountState(
    val accountId: AccountId,
    var cashBalance: BigDecimal,
    var blocked: Boolean = false,
    var balanceUpdatedAt: Instant? = null,
    val positions: MutableMap<Symbol, Long> = ConcurrentHashMap(),
    val pendingReservations: MutableMap<ClientOrderId, PendingReservation> = ConcurrentHashMap(),
    val orderReservations: MutableMap<OrderId, OrderReservation> = ConcurrentHashMap()
) {
    fun snapshot(): AccountSnapshot = AccountSnapshot(
        accountId = accountId,
        cashBalance = cashBalance,
        blocked = blocked,
        positions = positions.toMap(),
        lockedCash = pendingReservations.values.fold(BigDecimal.ZERO) { acc, reservation -> acc + reservation.reservedCash }
            .add(orderReservations.values.fold(BigDecimal.ZERO) { acc, reservation -> acc + reservation.reservedCash }),
        lockedPositions = buildMap {
            pendingReservations.values
                .filter { it.side == OrderSide.SELL }
                .forEach { reservation ->
                    put(reservation.symbol, (get(reservation.symbol) ?: 0L) + reservation.reservedQuantity)
                }
            orderReservations.values
                .filter { it.side == OrderSide.SELL }
                .forEach { reservation ->
                    put(reservation.symbol, (get(reservation.symbol) ?: 0L) + reservation.reservedQuantity)
                }
        }
    )
}

private data class PendingReservation(
    val clientOrderId: ClientOrderId,
    val symbol: Symbol,
    val side: OrderSide,
    val reservedCash: BigDecimal,
    val reservedQuantity: Long,
    val createdAt: Instant
)

private data class OrderReservation(
    val orderId: OrderId,
    val clientOrderId: ClientOrderId,
    val symbol: Symbol,
    val side: OrderSide,
    val reservedCash: BigDecimal,
    val reservedQuantity: Long,
    val version: Long
)
