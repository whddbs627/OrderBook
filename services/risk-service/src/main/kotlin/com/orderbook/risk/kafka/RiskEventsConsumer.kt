package com.orderbook.risk.kafka

import com.orderbook.common.events.BalanceUpdated
import com.orderbook.common.events.OrderAccepted
import com.orderbook.common.events.OrderAmended
import com.orderbook.common.events.OrderCanceled
import com.orderbook.common.events.OrderFilled
import com.orderbook.common.events.OrderPartiallyFilled
import com.orderbook.common.events.OrderRejected
import com.orderbook.common.kafka.EventEnvelopeMapper
import com.orderbook.common.kafka.KafkaTopics
import com.orderbook.risk.service.InMemoryRiskEvaluator
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RiskEventsConsumer(
    private val evaluator: InMemoryRiskEvaluator,
    private val envelopeMapper: EventEnvelopeMapper
) {
    @KafkaListener(topics = [KafkaTopics.BALANCES_UPDATED])
    fun onBalanceUpdated(message: String) {
        val event = envelopeMapper.deserialize(message) as? BalanceUpdated ?: return
        evaluator.applyBalanceUpdate(event.balance, event.positions)
    }

    @KafkaListener(topics = [KafkaTopics.ORDERS_ACCEPTED, KafkaTopics.ORDERS_REJECTED])
    fun onOrderEvent(message: String) {
        when (val event = envelopeMapper.deserialize(message)) {
            is OrderAccepted -> evaluator.applyOrder(event.order)
            is OrderAmended -> evaluator.applyOrder(event.order)
            is OrderCanceled -> evaluator.applyOrder(event.order)
            is OrderFilled -> evaluator.applyOrder(event.order)
            is OrderPartiallyFilled -> evaluator.applyOrder(event.order)
            is OrderRejected -> evaluator.applyOrder(event.order)
            else -> Unit
        }
    }
}
