package com.orderbook.query.repository

import com.orderbook.common.events.DomainEvent

interface EventStoreReader {
    fun events(): List<DomainEvent>
}
