package com.orderbook.order.service

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component

@Component
class OrderMetrics(
    private val meterRegistry: MeterRegistry
) {
    fun recordPlaceOrder(status: String, runnable: () -> Unit) {
        Timer.builder("orderbook.order.place.duration")
            .tag("status", status)
            .register(meterRegistry)
            .record(runnable)
    }

    fun incrementPublished(topic: String) {
        meterRegistry.counter("orderbook.kafka.events.published", "topic", topic).increment()
    }

    fun incrementAction(action: String, outcome: String) {
        meterRegistry.counter("orderbook.order.actions", "action", action, "outcome", outcome).increment()
    }
}
