package com.orderbook.marketdata

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.orderbook.common.events.OrderBookSnapshot
import com.orderbook.common.events.OrderBookUpdated
import com.orderbook.common.events.PriceLevel
import com.orderbook.common.events.Symbol
import com.orderbook.common.kafka.EventEnvelopeMapper
import com.orderbook.marketdata.kafka.MarketDataEventsConsumer
import com.orderbook.marketdata.service.MarketDataMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class MarketDataEventsConsumerTest {
    @Test
    fun `market data topic updates store snapshot`() {
        val store = FakeMarketDataStore()
        val mapper = EventEnvelopeMapper(jacksonObjectMapper().findAndRegisterModules())
        val consumer = MarketDataEventsConsumer(store, mapper, MarketDataMetrics(SimpleMeterRegistry()))
        val snapshot = OrderBookSnapshot(
            symbol = Symbol("AAPL"),
            bids = listOf(PriceLevel(BigDecimal("100.00"), 10)),
            asks = listOf(PriceLevel(BigDecimal("101.00"), 5)),
            lastUpdatedAt = Instant.parse("2026-03-23T00:00:00Z")
        )

        consumer.onMarketDataUpdated(
            mapper.serialize(
                OrderBookUpdated(
                    aggregateId = "AAPL",
                    symbol = Symbol("AAPL"),
                    traceId = "trace-1",
                    orderBookSnapshot = snapshot
                )
            )
        )

        assertEquals(snapshot, store.orderBook(Symbol("AAPL")))
    }
}
