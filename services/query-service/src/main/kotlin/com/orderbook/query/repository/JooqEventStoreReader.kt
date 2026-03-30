package com.orderbook.query.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.orderbook.common.events.DomainEvent
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JooqEventStoreReader(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper
) : EventStoreReader {
    override fun events(): List<DomainEvent> =
        dsl.select(EVENT_TYPE, EVENT_PAYLOAD)
            .from(name("event_store"))
            .orderBy(EVENT_OCCURRED_AT.asc(), EVENT_ID.asc())
            .fetch { record ->
                val type = record[EVENT_TYPE] ?: error("Missing event type")
                val payload = record[EVENT_PAYLOAD]?.data() ?: error("Missing event payload")
                val eventClass = Class.forName(type).asSubclass(DomainEvent::class.java)
                objectMapper.readValue(payload, eventClass)
            }

    private companion object {
        val EVENT_ID: Field<String> = field(name("event_id"), String::class.java)
        val EVENT_TYPE: Field<String> = field(name("event_type"), String::class.java)
        val EVENT_OCCURRED_AT: Field<Instant> = field(name("occurred_at"), Instant::class.java)
        val EVENT_PAYLOAD: Field<JSONB> = field(name("payload"), JSONB::class.java)
    }
}
