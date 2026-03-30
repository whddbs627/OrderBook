package com.orderbook.order.service

import com.orderbook.common.events.AcceptedOrderCommand
import com.orderbook.common.events.AccountId
import com.orderbook.common.events.AmendOrderCommand
import com.orderbook.common.events.CancelOrderCommand
import com.orderbook.common.events.ClientOrderId
import com.orderbook.common.events.Order
import com.orderbook.common.events.OrderAccepted
import com.orderbook.common.events.OrderAmended
import com.orderbook.common.events.OrderBookUpdated
import com.orderbook.common.events.OrderCanceled
import com.orderbook.common.events.OrderFilled
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.OrderPartiallyFilled
import com.orderbook.common.events.OrderReceived
import com.orderbook.common.events.OrderRejected
import com.orderbook.common.events.OrderResponse
import com.orderbook.common.events.OrderSide
import com.orderbook.common.events.OrderStatus
import com.orderbook.common.events.PlaceOrderCommand
import com.orderbook.common.events.PlaceOrderRequest
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.TradeExecuted
import com.orderbook.common.events.toResponse
import com.orderbook.order.client.MatchingClient
import com.orderbook.order.client.RiskClient
import com.orderbook.common.kafka.DomainEventPublisher
import com.orderbook.common.kafka.KafkaTopics
import com.orderbook.order.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class OrderWorkflowService(
    private val orderRepository: OrderRepository,
    private val riskClient: RiskClient,
    private val matchingClient: MatchingClient,
    private val eventPublisher: DomainEventPublisher,
    private val metrics: OrderMetrics
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun placeOrder(request: PlaceOrderRequest): OrderResponse {
        var response: OrderResponse? = null
        var outcome = "accepted"
        metrics.recordPlaceOrder("all") {
            val accountId = AccountId(request.accountId)
            val clientOrderId = ClientOrderId(request.clientOrderId)

            // Phase 1: Check for duplicate and persist initial order (transactional)
            val existingOrder = orderRepository.findByAccountAndClientOrderId(accountId, clientOrderId)
            if (existingOrder != null) {
                outcome = "duplicate"
                response = existingOrder.toResponse()
                return@recordPlaceOrder
            }

            val traceId = UUID.randomUUID().toString()
            val now = Instant.now()
            val initialOrder = Order(
                orderId = OrderId(UUID.randomUUID().toString()),
                accountId = accountId,
                clientOrderId = clientOrderId,
                symbol = Symbol(request.symbol),
                side = request.side,
                price = request.price,
                quantity = request.quantity,
                filledQuantity = 0,
                status = OrderStatus.RECEIVED,
                createdAt = now,
                updatedAt = now,
                version = 0
            )
            try {
                orderRepository.save(initialOrder)
            } catch (_: DataIntegrityViolationException) {
                outcome = "duplicate"
                response = requireNotNull(orderRepository.findByAccountAndClientOrderId(accountId, clientOrderId)) {
                    "Duplicate client order id detected but existing order could not be loaded"
                }.toResponse()
                return@recordPlaceOrder
            }
            log.info("Order received: orderId={}, account={}, symbol={}, side={}, qty={}, traceId={}",
                initialOrder.orderId.value, accountId.value, request.symbol, request.side, request.quantity, traceId)

            // Phase 2: External service calls (outside transaction)
            val riskResponse = riskClient.evaluate(
                PlaceOrderCommand(
                    accountId = initialOrder.accountId,
                    clientOrderId = initialOrder.clientOrderId,
                    symbol = initialOrder.symbol,
                    side = initialOrder.side,
                    price = initialOrder.price,
                    quantity = initialOrder.quantity,
                    traceId = traceId
                )
            )

            val receivedEvent = OrderReceived(
                aggregateId = initialOrder.orderId.value,
                symbol = initialOrder.symbol,
                accountId = initialOrder.accountId,
                traceId = traceId,
                order = initialOrder
            )

            if (!riskResponse.approved) {
                log.info("Order rejected by risk: orderId={}, reason={}, traceId={}",
                    initialOrder.orderId.value, riskResponse.reason, traceId)
                val rejected = initialOrder.copy(
                    status = OrderStatus.REJECTED,
                    updatedAt = Instant.now(),
                    version = initialOrder.version + 1
                )
                // Phase 3a: Persist rejection (transactional)
                persistRejection(rejected, receivedEvent, traceId, riskResponse.reason!!, riskResponse.message ?: "Rejected by risk service")
                outcome = "rejected"
                response = rejected.toResponse()
                return@recordPlaceOrder
            }

            val matchingResult = matchingClient.process(
                AcceptedOrderCommand(
                    orderId = initialOrder.orderId,
                    accountId = initialOrder.accountId,
                    clientOrderId = initialOrder.clientOrderId,
                    symbol = initialOrder.symbol,
                    side = initialOrder.side,
                    price = initialOrder.price,
                    quantity = initialOrder.quantity,
                    traceId = traceId
                )
            ).result

            // Phase 3b: Persist acceptance + matching result (transactional)
            val finalOrder = persistAcceptance(initialOrder, matchingResult, receivedEvent, traceId)
            log.info("Order processed: orderId={}, status={}, filledQty={}, trades={}, traceId={}",
                finalOrder.orderId.value, finalOrder.status, finalOrder.filledQuantity,
                matchingResult.trades.size, traceId)
            response = finalOrder.toResponse()
        }
        metrics.incrementAction("place", outcome)
        return response!!
    }

    @Transactional
    fun persistRejection(rejected: Order, receivedEvent: OrderReceived, traceId: String, reason: com.orderbook.common.events.RiskRejectReason, message: String) {
        orderRepository.save(rejected)
        val rejectedEvent = OrderRejected(
            aggregateId = rejected.orderId.value,
            symbol = rejected.symbol,
            accountId = rejected.accountId,
            traceId = traceId,
            reason = reason,
            message = message,
            order = rejected
        )
        orderRepository.appendEvents(listOf(receivedEvent, rejectedEvent))
        publish(KafkaTopics.ORDERS_RECEIVED, receivedEvent.aggregateId, receivedEvent)
        publish(KafkaTopics.ORDERS_REJECTED, rejectedEvent.aggregateId, rejectedEvent)
    }

    @Transactional
    fun persistAcceptance(
        initialOrder: Order,
        matchingResult: com.orderbook.common.events.MatchingResult,
        receivedEvent: OrderReceived,
        traceId: String
    ): Order {
        val finalOrder = orderRepository.save(
            matchingResult.order.copy(version = initialOrder.version + 1)
        )
        val domainEvents = mutableListOf(
            receivedEvent,
            OrderAccepted(
                aggregateId = finalOrder.orderId.value,
                symbol = finalOrder.symbol,
                accountId = finalOrder.accountId,
                traceId = traceId,
                order = finalOrder
            )
        )
        when (finalOrder.status) {
            OrderStatus.PARTIALLY_FILLED -> domainEvents += OrderPartiallyFilled(
                aggregateId = finalOrder.orderId.value,
                symbol = finalOrder.symbol,
                accountId = finalOrder.accountId,
                traceId = traceId,
                order = finalOrder
            )
            OrderStatus.FILLED -> domainEvents += OrderFilled(
                aggregateId = finalOrder.orderId.value,
                symbol = finalOrder.symbol,
                accountId = finalOrder.accountId,
                traceId = traceId,
                order = finalOrder
            )
            else -> Unit
        }
        domainEvents += matchingResult.trades.map {
            TradeExecuted(aggregateId = it.tradeId.value, symbol = it.symbol, traceId = traceId, trade = it)
        }
        val marketDataUpdated = OrderBookUpdated(
            aggregateId = finalOrder.symbol.value,
            symbol = finalOrder.symbol,
            traceId = traceId,
            orderBookSnapshot = matchingResult.orderBookSnapshot,
            trades = matchingResult.trades
        )
        domainEvents += marketDataUpdated
        orderRepository.appendEvents(domainEvents)
        publish(KafkaTopics.ORDERS_RECEIVED, receivedEvent.aggregateId, receivedEvent)
        domainEvents.filter { it is OrderAccepted || it is OrderPartiallyFilled || it is OrderFilled }
            .forEach { publish(KafkaTopics.ORDERS_ACCEPTED, it.aggregateId, it) }
        domainEvents.filterIsInstance<TradeExecuted>()
            .forEach { publish(KafkaTopics.TRADES_EXECUTED, it.aggregateId, it) }
        publish(KafkaTopics.MARKET_DATA_UPDATED, marketDataUpdated.aggregateId, marketDataUpdated)
        return finalOrder
    }

    @Transactional
    fun cancelOrder(orderId: String): OrderResponse {
        val existing = requireNotNull(orderRepository.find(OrderId(orderId))) { "Order not found: $orderId" }
        log.info("Canceling order: orderId={}", orderId)
        val canceled = requireNotNull(matchingClient.cancel(CancelOrderCommand(existing.orderId, UUID.randomUUID().toString()))) {
            "Order cannot be canceled: $orderId"
        }
        val order = existing.copy(
            status = OrderStatus.CANCELED,
            updatedAt = Instant.now(),
            version = existing.version + 1
        )
        orderRepository.save(order)
        val orderCanceled = OrderCanceled(
            aggregateId = order.orderId.value,
            symbol = order.symbol,
            accountId = order.accountId,
            traceId = UUID.randomUUID().toString(),
            order = order
        )
        val marketDataUpdated = OrderBookUpdated(
            aggregateId = order.symbol.value,
            symbol = order.symbol,
            traceId = orderCanceled.traceId,
            orderBookSnapshot = matchingClient.snapshot(order.symbol.value),
            trades = emptyList()
        )
        orderRepository.appendEvents(listOf(orderCanceled, marketDataUpdated))
        publish(KafkaTopics.ORDERS_ACCEPTED, orderCanceled.aggregateId, orderCanceled)
        publish(KafkaTopics.MARKET_DATA_UPDATED, marketDataUpdated.aggregateId, marketDataUpdated)
        metrics.incrementAction("cancel", "success")
        log.info("Order canceled: orderId={}", orderId)
        return order.toResponse()
    }

    @Transactional
    fun amendOrder(orderId: String, price: java.math.BigDecimal, quantity: Long): OrderResponse {
        val existing = requireNotNull(orderRepository.find(OrderId(orderId))) { "Order not found: $orderId" }
        val traceId = UUID.randomUUID().toString()
        log.info("Amending order: orderId={}, newPrice={}, newQty={}, traceId={}", orderId, price, quantity, traceId)
        val result = requireNotNull(
            matchingClient.amend(AmendOrderCommand(existing.orderId, price, quantity, traceId))
        ) { "Order cannot be amended: $orderId" }.result

        val amended = result.order.copy(updatedAt = Instant.now(), version = existing.version + 1)
        orderRepository.save(amended)
        val orderAmended = OrderAmended(
            aggregateId = amended.orderId.value,
            symbol = amended.symbol,
            accountId = amended.accountId,
            traceId = traceId,
            order = amended
        )
        val tradeEvents = result.trades.map {
            TradeExecuted(
                aggregateId = it.tradeId.value,
                symbol = it.symbol,
                traceId = traceId,
                trade = it
            )
        }
        val marketDataUpdated = OrderBookUpdated(
            aggregateId = amended.symbol.value,
            symbol = amended.symbol,
            traceId = traceId,
            orderBookSnapshot = result.orderBookSnapshot,
            trades = result.trades
        )
        orderRepository.appendEvents(listOf(orderAmended) + tradeEvents + marketDataUpdated)
        publish(KafkaTopics.ORDERS_ACCEPTED, orderAmended.aggregateId, orderAmended)
        tradeEvents.forEach { publish(KafkaTopics.TRADES_EXECUTED, it.aggregateId, it) }
        publish(KafkaTopics.MARKET_DATA_UPDATED, marketDataUpdated.aggregateId, marketDataUpdated)
        metrics.incrementAction("amend", "success")
        log.info("Order amended: orderId={}, status={}, traceId={}", orderId, amended.status, traceId)
        return amended.toResponse()
    }

    fun order(orderId: String): OrderResponse? = orderRepository.find(OrderId(orderId))?.toResponse()

    fun events() = orderRepository.events()

    private fun publish(topic: String, key: String, event: com.orderbook.common.events.DomainEvent) {
        eventPublisher.publish(topic, key, event)
        metrics.incrementPublished(topic)
    }
}
