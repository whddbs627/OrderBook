package com.orderbook.query.service

import com.orderbook.common.events.BalanceUpdated
import com.orderbook.common.events.DomainEvent
import com.orderbook.common.events.OrderAccepted
import com.orderbook.common.events.OrderAmended
import com.orderbook.common.events.OrderCanceled
import com.orderbook.common.events.OrderFilled
import com.orderbook.common.events.OrderPartiallyFilled
import com.orderbook.common.events.OrderReceived
import com.orderbook.common.events.OrderRejected
import com.orderbook.common.events.ProjectionUpdate
import com.orderbook.common.events.TradeExecuted
import com.orderbook.query.repository.EventStoreReader
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

@Service
class ProjectionReplayService(
    private val eventStoreReader: EventStoreReader,
    private val projectionStore: ProjectionStore,
    private val metrics: QueryMetrics
) {
    @PostConstruct
    fun replay() {
        projectionStore.reset()
        eventStoreReader.events().forEach(::applyEvent)
    }

    fun applyEvent(event: DomainEvent) {
        metrics.incrementReplay(event.javaClass.simpleName)
        when (event) {
            is OrderReceived -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderAccepted -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderRejected -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderCanceled -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderAmended -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderPartiallyFilled -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is OrderFilled -> projectionStore.apply(ProjectionUpdate(orders = listOf(event.order)))
            is TradeExecuted -> projectionStore.apply(ProjectionUpdate(trades = listOf(event.trade)))
            is BalanceUpdated -> projectionStore.apply(
                ProjectionUpdate(
                    balances = listOf(event.balance),
                    positions = event.positions
                )
            )

            else -> Unit
        }
    }
}
