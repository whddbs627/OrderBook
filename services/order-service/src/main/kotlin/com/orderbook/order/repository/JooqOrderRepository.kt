package com.orderbook.order.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.orderbook.common.events.AccountId
import com.orderbook.common.events.ClientOrderId
import com.orderbook.common.events.DomainEvent
import com.orderbook.common.events.Order
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.OrderSide
import com.orderbook.common.events.OrderStatus
import com.orderbook.common.events.Symbol
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant

@Repository
class JooqOrderRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper
) : OrderRepository {
    override fun save(order: Order): Order {
        val affectedRows = if (order.version == 0L) {
            dsl.insertInto(ORDERS)
                .set(order.toAssignments())
                .onConflict(ORDER_ID)
                .doNothing()
                .execute()
        } else {
            dsl.update(ORDERS)
                .set(order.toAssignments().minus(ORDER_ID))
                .where(ORDER_ID.eq(order.orderId.value))
                .and(VERSION.eq(order.version - 1))
                .execute()
        }

        if (affectedRows == 0) {
            throw OptimisticLockingFailureException(
                "Order ${order.orderId.value} has been modified concurrently"
            )
        }
        return order
    }

    override fun find(orderId: OrderId): Order? =
        dsl.selectFrom(ORDERS)
            .where(ORDER_ID.eq(orderId.value))
            .fetchOne(::toOrder)

    override fun findByAccountAndClientOrderId(accountId: AccountId, clientOrderId: ClientOrderId): Order? =
        dsl.selectFrom(ORDERS)
            .where(ACCOUNT_ID.eq(accountId.value))
            .and(CLIENT_ORDER_ID.eq(clientOrderId.value))
            .fetchOne(::toOrder)

    override fun appendEvents(events: List<DomainEvent>) {
        if (events.isEmpty()) {
            return
        }

        events.forEach { event ->
            dsl.insertInto(EVENT_STORE)
                .set(
                    mapOf(
                        EVENT_ID to event.eventId,
                        EVENT_TYPE to event.javaClass.name,
                        EVENT_AGGREGATE_ID to event.aggregateId,
                        EVENT_SYMBOL to event.symbol?.value,
                        EVENT_ACCOUNT_ID to event.accountId?.value,
                        EVENT_TRACE_ID to event.traceId,
                        EVENT_OCCURRED_AT to event.occurredAt,
                        EVENT_PAYLOAD to JSONB.valueOf(objectMapper.writeValueAsString(event))
                    )
                )
                .onConflict(EVENT_ID)
                .doNothing()
                .execute()
        }
    }

    override fun events(): List<DomainEvent> =
        dsl.select(EVENT_TYPE, EVENT_PAYLOAD)
            .from(EVENT_STORE)
            .orderBy(EVENT_OCCURRED_AT.asc(), EVENT_ID.asc())
            .fetch { record ->
                val type = record[EVENT_TYPE] ?: error("Missing event type")
                val payload = record[EVENT_PAYLOAD]?.data() ?: error("Missing event payload")
                val eventClass = Class.forName(type).asSubclass(DomainEvent::class.java)
                objectMapper.readValue(payload, eventClass)
            }

    private fun toOrder(record: Record): Order = Order(
        orderId = OrderId(record[ORDER_ID]!!),
        accountId = com.orderbook.common.events.AccountId(record[ACCOUNT_ID]!!),
        clientOrderId = ClientOrderId(record[CLIENT_ORDER_ID]!!),
        symbol = Symbol(record[SYMBOL]!!),
        side = OrderSide.valueOf(record[SIDE]!!),
        price = record[PRICE]!!,
        quantity = record[QUANTITY]!!,
        filledQuantity = record[FILLED_QUANTITY]!!,
        status = OrderStatus.valueOf(record[STATUS]!!),
        createdAt = record[CREATED_AT]!!,
        updatedAt = record[UPDATED_AT]!!,
        version = record[VERSION]!!
    )

    private fun Order.toAssignments(): Map<Field<*>, Any?> = mapOf(
        ORDER_ID to orderId.value,
        ACCOUNT_ID to accountId.value,
        CLIENT_ORDER_ID to clientOrderId.value,
        SYMBOL to symbol.value,
        SIDE to side.name,
        PRICE to price,
        QUANTITY to quantity,
        FILLED_QUANTITY to filledQuantity,
        STATUS to status.name,
        CREATED_AT to createdAt,
        UPDATED_AT to updatedAt,
        VERSION to version
    )

    private companion object {
        val ORDERS: Table<*> = table(name("orders"))
        val ORDER_ID: Field<String> = field(name("order_id"), String::class.java)
        val ACCOUNT_ID: Field<String> = field(name("account_id"), String::class.java)
        val CLIENT_ORDER_ID: Field<String> = field(name("client_order_id"), String::class.java)
        val SYMBOL: Field<String> = field(name("symbol"), String::class.java)
        val SIDE: Field<String> = field(name("side"), String::class.java)
        val PRICE: Field<BigDecimal> = field(name("price"), BigDecimal::class.java)
        val QUANTITY: Field<Long> = field(name("quantity"), Long::class.java)
        val FILLED_QUANTITY: Field<Long> = field(name("filled_quantity"), Long::class.java)
        val STATUS: Field<String> = field(name("status"), String::class.java)
        val CREATED_AT: Field<Instant> = field(name("created_at"), Instant::class.java)
        val UPDATED_AT: Field<Instant> = field(name("updated_at"), Instant::class.java)
        val VERSION: Field<Long> = field(name("version"), Long::class.java)

        val EVENT_STORE: Table<*> = table(name("event_store"))
        val EVENT_ID: Field<String> = field(name("event_id"), String::class.java)
        val EVENT_TYPE: Field<String> = field(name("event_type"), String::class.java)
        val EVENT_AGGREGATE_ID: Field<String> = field(name("aggregate_id"), String::class.java)
        val EVENT_SYMBOL: Field<String> = field(name("symbol"), String::class.java)
        val EVENT_ACCOUNT_ID: Field<String> = field(name("account_id"), String::class.java)
        val EVENT_TRACE_ID: Field<String> = field(name("trace_id"), String::class.java)
        val EVENT_OCCURRED_AT: Field<Instant> = field(name("occurred_at"), Instant::class.java)
        val EVENT_PAYLOAD: Field<JSONB> = field(name("payload"), JSONB::class.java)
    }
}
