package com.orderbook.query

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.BalanceSnapshot
import com.orderbook.common.events.Order
import com.orderbook.common.events.OrderAccepted
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.OrderSide
import com.orderbook.common.events.OrderStatus
import com.orderbook.common.events.PositionSnapshot
import com.orderbook.common.events.ProjectionUpdate
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.Trade
import com.orderbook.common.events.TradeExecuted
import com.orderbook.common.events.TradeId
import com.orderbook.query.repository.EventStoreReader
import com.orderbook.query.service.ProjectionReplayService
import com.orderbook.query.service.QueryMetrics
import com.orderbook.query.service.ProjectionStore
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class ProjectionReplayServiceTest {
    @Test
    fun `replay resets store then rebuilds from event sequence`() {
        val applied = mutableListOf<ProjectionUpdate>()
        var resetCount = 0
        val store = object : ProjectionStore {
            override fun apply(update: ProjectionUpdate) {
                applied += update
            }

            override fun reset() {
                resetCount += 1
            }

            override fun order(orderId: OrderId): Order? = error("unused")
            override fun balances(accountId: AccountId): BalanceSnapshot? = error("unused")
            override fun positions(accountId: AccountId): List<PositionSnapshot> = error("unused")
            override fun trades(symbol: String?): List<Trade> = error("unused")
        }
        val order = Order(
            orderId = OrderId("ORD-1"),
            accountId = AccountId("ACC-001"),
            clientOrderId = com.orderbook.common.events.ClientOrderId("CO-1"),
            symbol = Symbol("AAPL"),
            side = OrderSide.BUY,
            price = BigDecimal("100.00"),
            quantity = 10,
            filledQuantity = 0,
            status = OrderStatus.ACCEPTED,
            createdAt = Instant.parse("2026-03-23T00:00:00Z"),
            updatedAt = Instant.parse("2026-03-23T00:00:00Z")
        )
        val trade = Trade(
            tradeId = TradeId("TRD-1"),
            symbol = Symbol("AAPL"),
            buyOrderId = OrderId("BUY-1"),
            sellOrderId = OrderId("SELL-1"),
            buyAccountId = AccountId("ACC-001"),
            sellAccountId = AccountId("ACC-002"),
            price = BigDecimal("100.00"),
            quantity = 10,
            occurredAt = Instant.parse("2026-03-23T00:00:00Z")
        )
        val reader = object : EventStoreReader {
            override fun events() = listOf(
                OrderAccepted(
                    aggregateId = order.orderId.value,
                    symbol = order.symbol,
                    accountId = order.accountId,
                    traceId = "trace-1",
                    order = order
                ),
                TradeExecuted(
                    aggregateId = trade.tradeId.value,
                    symbol = trade.symbol,
                    traceId = "trace-1",
                    trade = trade
                )
            )
        }

        ProjectionReplayService(reader, store, QueryMetrics(SimpleMeterRegistry())).replay()

        assertEquals(1, resetCount)
        assertEquals(OrderId("ORD-1"), applied[0].orders.single().orderId)
        assertEquals(TradeId("TRD-1"), applied[1].trades.single().tradeId)
    }
}
