package com.orderbook.common.kafka

import com.orderbook.common.events.DomainEvent

interface DomainEventPublisher {
    fun publish(topic: String, key: String, event: DomainEvent)
}
