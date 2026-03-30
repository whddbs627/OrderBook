package com.orderbook.common.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.orderbook.common.events.DomainEvent

class EventEnvelopeMapper(
    private val objectMapper: ObjectMapper
) {
    fun serialize(event: DomainEvent): String =
        objectMapper.writeValueAsString(
            EventEnvelope(
                eventType = event.javaClass.name,
                payload = objectMapper.writeValueAsString(event)
            )
        )

    fun deserialize(message: String): DomainEvent {
        val envelope = objectMapper.readValue(message, EventEnvelope::class.java)
        val eventClass = Class.forName(envelope.eventType).asSubclass(DomainEvent::class.java)
        return objectMapper.readValue(envelope.payload, eventClass)
    }
}
