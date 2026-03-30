package com.orderbook.query.service

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class QueryMetrics(
    private val meterRegistry: MeterRegistry
) {
    fun incrementConsumed(topic: String) {
        meterRegistry.counter("orderbook.kafka.events.consumed", "service", "query-service", "topic", topic).increment()
    }

    fun incrementReplay(eventType: String) {
        meterRegistry.counter("orderbook.replay.events", "service", "query-service", "eventType", eventType).increment()
    }
}
