package com.orderbook.order.kafka

import com.orderbook.common.events.DomainEvent
import com.orderbook.common.kafka.DomainEventPublisher
import com.orderbook.common.kafka.EventEnvelopeMapper
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Table
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class KafkaDomainEventPublisher(
    private val dsl: DSLContext,
    private val envelopeMapper: EventEnvelopeMapper
) : DomainEventPublisher {
    override fun publish(topic: String, key: String, event: DomainEvent) {
        dsl.insertInto(OUTBOX_EVENTS)
            .set(
                mapOf(
                    OUTBOX_ID to event.eventId,
                    OUTBOX_TOPIC to topic,
                    OUTBOX_KEY to key,
                    OUTBOX_PAYLOAD to envelopeMapper.serialize(event),
                    OUTBOX_CREATED_AT to Instant.now()
                )
            )
            .onConflict(OUTBOX_ID)
            .doNothing()
            .execute()
    }

    private companion object {
        val OUTBOX_EVENTS: Table<*> = table(name("outbox_events"))
        val OUTBOX_ID: Field<String> = field(name("outbox_id"), String::class.java)
        val OUTBOX_TOPIC: Field<String> = field(name("topic"), String::class.java)
        val OUTBOX_KEY: Field<String> = field(name("event_key"), String::class.java)
        val OUTBOX_PAYLOAD: Field<String> = field(name("payload"), String::class.java)
        val OUTBOX_CREATED_AT: Field<Instant> = field(name("created_at"), Instant::class.java)
    }
}
