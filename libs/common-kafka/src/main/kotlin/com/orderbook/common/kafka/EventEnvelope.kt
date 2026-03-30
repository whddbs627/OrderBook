package com.orderbook.common.kafka

data class EventEnvelope(
    val eventType: String,
    val payload: String
)
