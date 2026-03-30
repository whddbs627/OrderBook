package com.orderbook.marketdata.repository

import com.orderbook.common.events.DomainEvent

interface EventStoreReader {
    fun events(): List<DomainEvent>
}
