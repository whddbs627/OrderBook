package com.orderbook.query

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.BalanceSnapshot
import com.orderbook.common.events.ClientOrderId
import com.orderbook.common.events.Order
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.OrderSide
import com.orderbook.common.events.OrderStatus
import com.orderbook.common.events.PositionSnapshot
import com.orderbook.common.events.ProjectionUpdate
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.Trade
import com.orderbook.query.service.ProjectionStore
import com.orderbook.query.web.QueryController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class QueryControllerTest {
    private val store = FakeProjectionStore()
    private val controller = QueryController(store)

    @Test
    fun `order lookup delegates through reactive wrapper`() {
        store.order = sampleOrder()

        val response = controller.order(store.order!!.orderId.value).block()

        assertEquals(store.order!!.orderId.value, response!!.orderId)
    }

    @Test
    fun `missing order completes without payload`() {
        val response = controller.order("missing").blockOptional()

        assertFalse(response.isPresent)
    }

    @Test
    fun `balances positions and trades delegate through reactive wrappers`() {
        val balance = BalanceSnapshot(AccountId("ACC-001"), BigDecimal("1000.00"), Instant.parse("2026-03-25T00:00:00Z"))
        val position = PositionSnapshot(AccountId("ACC-001"), Symbol("AAPL"), 10)
        val trade = Trade(
            tradeId = com.orderbook.common.events.TradeId("TRD-1"),
            symbol = Symbol("AAPL"),
            buyOrderId = OrderId("BUY-1"),
            sellOrderId = OrderId("SELL-1"),
            buyAccountId = AccountId("ACC-001"),
            sellAccountId = AccountId("ACC-002"),
            price = BigDecimal("100.00"),
            quantity = 1,
            occurredAt = Instant.parse("2026-03-25T00:00:00Z")
        )
        store.balance = balance
        store.positions = listOf(position)
        store.trades = listOf(trade)

        assertEquals(balance, controller.balances("ACC-001").block())
        assertEquals(listOf(position), controller.positions("ACC-001").block())
        assertEquals(listOf(trade), controller.trades("AAPL").block())
    }

    @Test
    fun `apply completes successfully`() {
        controller.apply(ProjectionUpdate()).block()

        assertTrue(store.applyCalled)
    }

    private fun sampleOrder(): Order = Order(
        orderId = OrderId("ORD-1"),
        accountId = AccountId("ACC-001"),
        clientOrderId = ClientOrderId("CLIENT-1"),
        symbol = Symbol("AAPL"),
        side = OrderSide.BUY,
        price = BigDecimal("100.00"),
        quantity = 10,
        filledQuantity = 0,
        status = OrderStatus.ACCEPTED,
        createdAt = Instant.parse("2026-03-25T00:00:00Z"),
        updatedAt = Instant.parse("2026-03-25T00:00:00Z"),
        version = 1
    )

    private class FakeProjectionStore : ProjectionStore {
        var applyCalled = false
        var order: Order? = null
        var balance: BalanceSnapshot? = null
        var positions: List<PositionSnapshot> = emptyList()
        var trades: List<Trade> = emptyList()

        override fun apply(update: ProjectionUpdate) {
            applyCalled = true
        }

        override fun reset() = Unit

        override fun order(orderId: OrderId): Order? = order

        override fun balances(accountId: AccountId): BalanceSnapshot? = balance

        override fun positions(accountId: AccountId): List<PositionSnapshot> = positions

        override fun trades(symbol: String?): List<Trade> = trades
    }
}
