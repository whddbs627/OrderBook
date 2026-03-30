package com.orderbook.query.kafka

import com.orderbook.common.events.BalanceUpdated
import com.orderbook.common.events.OrderAccepted
import com.orderbook.common.events.OrderAmended
import com.orderbook.common.events.OrderCanceled
import com.orderbook.common.events.OrderFilled
import com.orderbook.common.events.OrderPartiallyFilled
import com.orderbook.common.events.OrderReceived
import com.orderbook.common.events.OrderRejected
import com.orderbook.common.events.ProjectionUpdate
import com.orderbook.common.events.TradeExecuted
import com.orderbook.common.kafka.EventEnvelopeMapper
import com.orderbook.common.kafka.KafkaTopics
import com.orderbook.query.service.ProjectionStore
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class QueryEventsConsumer(
    private val projectionStore: ProjectionStore,
    private val envelopeMapper: EventEnvelopeMapper,
    private val metrics: com.orderbook.query.service.QueryMetrics
) {
    @KafkaListener(topics = [KafkaTopics.ORDERS_RECEIVED, KafkaTopics.ORDERS_ACCEPTED, KafkaTopics.ORDERS_REJECTED])
    fun onOrderEvent(message: String) {
        metrics.incrementConsumed("order-events")
        when (val event = envelopeMapper.deserialize(message)) {
            is OrderReceived -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderAccepted -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderRejected -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderCanceled -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderAmended -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderPartiallyFilled -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderFilled -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            else -> Unit
        }
    }

    @KafkaListener(topics = [KafkaTopics.TRADES_EXECUTED])
    fun onTradeEvent(message: String) {
        metrics.incrementConsumed(KafkaTopics.TRADES_EXECUTED)
        val event = envelopeMapper.deserialize(message) as? TradeExecuted ?: return
        projectionStore.apply(ProjectionUpdate(trades = listOf(event.trade)))
    }

    @KafkaListener(topics = [KafkaTopics.BALANCES_UPDATED])
    fun onBalanceUpdated(message: String) {
        metrics.incrementConsumed(KafkaTopics.BALANCES_UPDATED)
        val event = envelopeMapper.deserialize(message) as? BalanceUpdated ?: return
        projectionStore.apply(
            ProjectionUpdate(
                balances = listOf(event.balance),
                positions = event.positions
            )
        )
    }
}
