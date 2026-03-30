package com.orderbook.ledger.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.orderbook.common.events.DomainEvent
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Table
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JooqEventStoreRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper
) : EventStoreRepository {
    override fun append(events: List<DomainEvent>) {
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

    private companion object {
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
