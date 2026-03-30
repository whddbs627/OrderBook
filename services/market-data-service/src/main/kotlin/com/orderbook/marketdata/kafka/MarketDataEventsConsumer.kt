package com.orderbook.marketdata.kafka

import com.orderbook.common.events.MarketDataUpdate
import com.orderbook.common.events.OrderBookUpdated
import com.orderbook.common.kafka.EventEnvelopeMapper
import com.orderbook.common.kafka.KafkaTopics
import com.orderbook.marketdata.service.MarketDataStore
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class MarketDataEventsConsumer(
    private val marketDataStore: MarketDataStore,
    private val envelopeMapper: EventEnvelopeMapper,
    private val metrics: com.orderbook.marketdata.service.MarketDataMetrics
) {
    @KafkaListener(topics = [KafkaTopics.MARKET_DATA_UPDATED])
    fun onMarketDataUpdated(message: String) {
        metrics.incrementConsumed(KafkaTopics.MARKET_DATA_UPDATED)
        val event = envelopeMapper.deserialize(message) as? OrderBookUpdated ?: return
        marketDataStore.apply(MarketDataUpdate(event.orderBookSnapshot, event.trades))
    }
}
