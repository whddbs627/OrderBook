package com.orderbook.matching

import com.orderbook.common.events.AcceptedOrderCommand
import com.orderbook.common.events.AccountId
import com.orderbook.common.events.ClientOrderId
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.OrderSide
import com.orderbook.common.events.OrderStatus
import com.orderbook.common.events.Symbol
import com.orderbook.matching.service.OrderBookMatchingService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class OrderBookMatchingServiceTest {
    private val service = OrderBookMatchingService()

    @Test
    fun `matches at best price then by time`() = runBlocking {
        service.process(
            AcceptedOrderCommand(
                orderId = OrderId("sell-1"),
                accountId = AccountId("seller-1"),
                clientOrderId = ClientOrderId("sell-1"),
                symbol = Symbol("AAPL"),
                side = OrderSide.SELL,
                price = BigDecimal("101"),
                quantity = 20,
                traceId = UUID.randomUUID().toString()
            )
        )
        service.process(
            AcceptedOrderCommand(
                orderId = OrderId("sell-2"),
                accountId = AccountId("seller-2"),
                clientOrderId = ClientOrderId("sell-2"),
                symbol = Symbol("AAPL"),
                side = OrderSide.SELL,
                price = BigDecimal("100"),
                quantity = 20,
                traceId = UUID.randomUUID().toString()
            )
        )

        val result = service.process(
            AcceptedOrderCommand(
                orderId = OrderId("buy-1"),
                accountId = AccountId("buyer-1"),
                clientOrderId = ClientOrderId("buy-1"),
                symbol = Symbol("AAPL"),
                side = OrderSide.BUY,
                price = BigDecimal("101"),
                quantity = 20,
                traceId = UUID.randomUUID().toString()
            )
        )

        assertEquals(1, result.trades.size)
        assertEquals(BigDecimal("100"), result.trades.first().price)
        assertEquals(OrderStatus.FILLED, result.order.status)
    }

    @Test
    fun `leaves remainder on book after partial fill`() = runBlocking {
        service.process(
            AcceptedOrderCommand(
                orderId = OrderId("sell-3"),
                accountId = AccountId("seller-3"),
                clientOrderId = ClientOrderId("sell-3"),
                symbol = Symbol("MSFT"),
                side = OrderSide.SELL,
                price = BigDecimal("200"),
                quantity = 10,
                traceId = UUID.randomUUID().toString()
            )
        )

        val result = service.process(
            AcceptedOrderCommand(
                orderId = OrderId("buy-2"),
                accountId = AccountId("buyer-2"),
                clientOrderId = ClientOrderId("buy-2"),
                symbol = Symbol("MSFT"),
                side = OrderSide.BUY,
                price = BigDecimal("200"),
                quantity = 15,
                traceId = UUID.randomUUID().toString()
            )
        )

        assertEquals(OrderStatus.PARTIALLY_FILLED, result.order.status)
        assertEquals(10, result.order.filledQuantity)
        assertEquals(5, result.order.remainingQuantity)
    }
}
