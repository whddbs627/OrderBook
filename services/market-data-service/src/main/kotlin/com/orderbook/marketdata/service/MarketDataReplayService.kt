package com.orderbook.marketdata.service

import com.orderbook.common.events.DomainEvent
import com.orderbook.common.events.MarketDataUpdate
import com.orderbook.common.events.OrderBookUpdated
import com.orderbook.marketdata.repository.EventStoreReader
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

@Service
class MarketDataReplayService(
    private val eventStoreReader: EventStoreReader,
    private val marketDataStore: MarketDataStore,
    private val metrics: MarketDataMetrics
) {
    @PostConstruct
    fun replay() {
        marketDataStore.reset()
        eventStoreReader.events().forEach(::applyEvent)
    }

    fun applyEvent(event: DomainEvent) {
        metrics.incrementReplay(event.javaClass.simpleName)
        when (event) {
            is OrderBookUpdated -> marketDataStore.apply(MarketDataUpdate(event.orderBookSnapshot, event.trades))
            else -> Unit
        }
    }
}
