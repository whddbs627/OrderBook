package com.orderbook.marketdata

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.MarketDataUpdate
import com.orderbook.common.events.OrderBookSnapshot
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.PriceLevel
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.Trade
import com.orderbook.common.events.TradeId
import com.orderbook.marketdata.service.MarketDataStore
import com.orderbook.marketdata.web.MarketDataController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class MarketDataControllerTest {
    private val store = FakeMarketDataStore()
    private val controller = MarketDataController(store)

    @Test
    fun `orderbook and trades delegate through reactive wrappers`() {
        val symbol = Symbol("AAPL")
        val snapshot = OrderBookSnapshot(
            symbol = symbol,
            bids = listOf(PriceLevel(BigDecimal("100.00"), 10)),
            asks = listOf(PriceLevel(BigDecimal("101.00"), 5)),
            lastUpdatedAt = Instant.parse("2026-03-25T00:00:00Z")
        )
        val trade = Trade(
            tradeId = TradeId("TRD-1"),
            symbol = symbol,
            buyOrderId = OrderId("BUY-1"),
            sellOrderId = OrderId("SELL-1"),
            buyAccountId = AccountId("ACC-001"),
            sellAccountId = AccountId("ACC-002"),
            price = BigDecimal("100.00"),
            quantity = 1,
            occurredAt = Instant.parse("2026-03-25T00:00:00Z")
        )
        store.snapshot = snapshot
        store.tradeList = listOf(trade)

        assertEquals(snapshot, controller.orderBook("AAPL").block())
        assertEquals(listOf(trade), controller.trades("AAPL").block())
    }

    @Test
    fun `apply completes successfully`() {
        val update = MarketDataUpdate(
            orderBookSnapshot = OrderBookSnapshot(
                symbol = Symbol("AAPL"),
                bids = emptyList(),
                asks = emptyList(),
                lastUpdatedAt = Instant.parse("2026-03-25T00:00:00Z")
            ),
            recentTrades = emptyList()
        )

        controller.apply(update).block()

        assertTrue(store.applyCalled)
    }

    private class FakeMarketDataStore : MarketDataStore {
        var applyCalled = false
        var snapshot: OrderBookSnapshot? = null
        var tradeList: List<Trade> = emptyList()

        override fun apply(update: MarketDataUpdate) {
            applyCalled = true
        }

        override fun orderBook(symbol: Symbol): OrderBookSnapshot? = snapshot

        override fun reset() = Unit

        override fun trades(symbol: Symbol): List<Trade> = tradeList

        override fun stream(channel: String) = reactor.core.publisher.Flux.empty<String>()
    }
}
