package com.orderbook.marketdata

import com.orderbook.common.events.OrderBookSnapshot
import com.orderbook.common.events.OrderBookUpdated
import com.orderbook.common.events.PriceLevel
import com.orderbook.common.events.Symbol
import com.orderbook.marketdata.repository.EventStoreReader
import com.orderbook.marketdata.service.MarketDataMetrics
import com.orderbook.marketdata.service.MarketDataReplayService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class MarketDataReplayServiceTest {
    @Test
    fun `replay resets store then rebuilds latest market data`() {
        val store = FakeMarketDataStore()
        val snapshot = OrderBookSnapshot(
            symbol = Symbol("AAPL"),
            bids = listOf(PriceLevel(BigDecimal("100.00"), 10)),
            asks = listOf(PriceLevel(BigDecimal("101.00"), 5)),
            lastUpdatedAt = Instant.parse("2026-03-23T00:00:00Z")
        )
        val reader = object : EventStoreReader {
            override fun events() = listOf(
                OrderBookUpdated(
                    aggregateId = "AAPL",
                    symbol = Symbol("AAPL"),
                    traceId = "trace-1",
                    orderBookSnapshot = snapshot
                )
            )
        }

        MarketDataReplayService(reader, store, MarketDataMetrics(SimpleMeterRegistry())).replay()

        assertEquals(snapshot, store.orderBook(Symbol("AAPL")))
    }
}
