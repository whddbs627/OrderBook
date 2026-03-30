package com.orderbook.ledger.kafka

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class LedgerMetrics(
    private val meterRegistry: MeterRegistry
) {
    fun incrementConsumed(topic: String) {
        meterRegistry.counter("orderbook.kafka.events.consumed", "service", "ledger-service", "topic", topic).increment()
    }

    fun incrementProduced(topic: String) {
        meterRegistry.counter("orderbook.kafka.events.published", "service", "ledger-service", "topic", topic).increment()
    }
}
