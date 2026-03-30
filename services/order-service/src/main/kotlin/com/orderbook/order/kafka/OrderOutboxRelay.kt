package com.orderbook.order.kafka

import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class OrderOutboxRelay(
    private val dsl: DSLContext,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${orderbook.outbox.fixed-delay-ms:500}")
    fun relay() {
        val events = pendingEvents()
        if (events.isEmpty()) return
        log.debug("Relaying {} outbox events", events.size)
        events.forEach { event ->
            try {
                kafkaTemplate.send(event.topic, event.key, event.payload).get()
                dsl.update(OUTBOX_EVENTS)
                    .set(OUTBOX_PUBLISHED_AT, Instant.now())
                    .where(OUTBOX_ID.eq(event.id))
                    .and(OUTBOX_PUBLISHED_AT.isNull)
                    .execute()
            } catch (ex: Exception) {
                log.error("Failed to relay outbox event: id={}, topic={}", event.id, event.topic, ex)
                throw ex
            }
        }
        log.info("Relayed {} outbox events", events.size)
    }

    private fun pendingEvents(limit: Int = 100): List<PendingOutboxEvent> =
        dsl.select(OUTBOX_ID, OUTBOX_TOPIC, OUTBOX_KEY, OUTBOX_PAYLOAD)
            .from(OUTBOX_EVENTS)
            .where(OUTBOX_PUBLISHED_AT.isNull)
            .orderBy(OUTBOX_CREATED_AT.asc(), OUTBOX_ID.asc())
            .limit(limit)
            .fetch(::toPendingOutboxEvent)

    private fun toPendingOutboxEvent(record: Record): PendingOutboxEvent = PendingOutboxEvent(
        id = record[OUTBOX_ID]!!,
        topic = record[OUTBOX_TOPIC]!!,
        key = record[OUTBOX_KEY]!!,
        payload = record[OUTBOX_PAYLOAD]!!
    )

    private data class PendingOutboxEvent(
        val id: String,
        val topic: String,
        val key: String,
        val payload: String
    )

    private companion object {
        val OUTBOX_EVENTS: Table<*> = table(name("outbox_events"))
        val OUTBOX_ID: Field<String> = field(name("outbox_id"), String::class.java)
        val OUTBOX_TOPIC: Field<String> = field(name("topic"), String::class.java)
        val OUTBOX_KEY: Field<String> = field(name("event_key"), String::class.java)
        val OUTBOX_PAYLOAD: Field<String> = field(name("payload"), String::class.java)
        val OUTBOX_CREATED_AT: Field<Instant> = field(name("created_at"), Instant::class.java)
        val OUTBOX_PUBLISHED_AT: Field<Instant> = field(name("published_at"), Instant::class.java)
    }
}
