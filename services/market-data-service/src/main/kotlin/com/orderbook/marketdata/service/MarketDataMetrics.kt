package com.orderbook.marketdata.service

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class MarketDataMetrics(
    private val meterRegistry: MeterRegistry
) {
    fun incrementConsumed(topic: String) {
        meterRegistry.counter("orderbook.kafka.events.consumed", "service", "market-data-service", "topic", topic).increment()
    }

    fun incrementReplay(eventType: String) {
        meterRegistry.counter("orderbook.replay.events", "service", "market-data-service", "eventType", eventType).increment()
    }

    fun incrementSnapshotWrite() {
        meterRegistry.counter("orderbook.marketdata.snapshots.updated").increment()
    }
}
