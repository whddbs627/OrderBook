package com.orderbook.ledger.repository

import com.orderbook.common.events.DomainEvent

interface EventStoreRepository {
    fun append(events: List<DomainEvent>)
}
