package com.orderbook.ledger

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.orderbook.common.events.AccountId
import com.orderbook.common.events.BalanceSnapshot
import com.orderbook.common.events.DomainEvent
import com.orderbook.common.events.LedgerAppended
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.PositionSnapshot
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.Trade
import com.orderbook.common.events.TradeExecuted
import com.orderbook.common.events.TradeId
import com.orderbook.common.kafka.DomainEventPublisher
import com.orderbook.common.kafka.EventEnvelopeMapper
import com.orderbook.common.kafka.KafkaTopics
import com.orderbook.ledger.kafka.LedgerEventsConsumer
import com.orderbook.ledger.kafka.LedgerMetrics
import com.orderbook.ledger.repository.EventStoreRepository
import com.orderbook.ledger.service.LedgerService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class LedgerEventsConsumerTest {
    @Test
    fun `trade execution updates ledger and publishes follow-up events`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val trade = Trade(
            tradeId = TradeId("TRD-1"),
            symbol = Symbol("AAPL"),
            buyOrderId = OrderId("BUY-1"),
            sellOrderId = OrderId("SELL-1"),
            buyAccountId = AccountId("ACC-001"),
            sellAccountId = AccountId("ACC-002"),
            price = BigDecimal("100.00"),
            quantity = 10,
            occurredAt = Instant.parse("2026-03-23T00:00:00Z")
        )
        val balanceOne = BalanceSnapshot(AccountId("ACC-001"), BigDecimal("999000.00"), trade.occurredAt)
        val balanceTwo = BalanceSnapshot(AccountId("ACC-002"), BigDecimal("1001000.00"), trade.occurredAt)
        val positions = listOf(
            PositionSnapshot(AccountId("ACC-001"), Symbol("AAPL"), 310),
            PositionSnapshot(AccountId("ACC-002"), Symbol("AAPL"), 140)
        )
        val ledgerService = object : LedgerService {
            override fun append(trades: List<Trade>, traceId: String) =
                com.orderbook.common.events.LedgerAppendResult(listOf(balanceOne, balanceTwo), positions)

            override fun balance(accountId: AccountId) = error("unused")

            override fun positions(accountId: AccountId) = error("unused")
        }
        val published = mutableListOf<Pair<String, DomainEvent>>()
        val publisher = object : DomainEventPublisher {
            override fun publish(topic: String, key: String, event: DomainEvent) {
                published += topic to event
            }
        }
        val eventStoreRepository = object : EventStoreRepository {
            override fun append(events: List<DomainEvent>) = Unit
        }

        val consumer = LedgerEventsConsumer(
            ledgerService = ledgerService,
            eventPublisher = publisher,
            envelopeMapper = EventEnvelopeMapper(mapper),
            eventStoreRepository = eventStoreRepository,
            metrics = LedgerMetrics(SimpleMeterRegistry())
        )

        consumer.onTradeExecuted(
            EventEnvelopeMapper(mapper).serialize(
                TradeExecuted(
                    aggregateId = trade.tradeId.value,
                    symbol = trade.symbol,
                    traceId = "trace-1",
                    trade = trade
                )
            )
        )

        assertEquals(3, published.size)
        assertEquals(KafkaTopics.LEDGER_APPENDED, published[0].first)
        assertTrue(published[0].second is LedgerAppended)
        assertEquals(KafkaTopics.BALANCES_UPDATED, published[1].first)
        assertEquals(KafkaTopics.BALANCES_UPDATED, published[2].first)
    }

    @Test
    fun `duplicate trade execution does not publish follow-up events`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val trade = Trade(
            tradeId = TradeId("TRD-1"),
            symbol = Symbol("AAPL"),
            buyOrderId = OrderId("BUY-1"),
            sellOrderId = OrderId("SELL-1"),
            buyAccountId = AccountId("ACC-001"),
            sellAccountId = AccountId("ACC-002"),
            price = BigDecimal("100.00"),
            quantity = 10,
            occurredAt = Instant.parse("2026-03-23T00:00:00Z")
        )
        val ledgerService = object : LedgerService {
            override fun append(trades: List<Trade>, traceId: String) =
                com.orderbook.common.events.LedgerAppendResult(emptyList(), emptyList())

            override fun balance(accountId: AccountId) = error("unused")

            override fun positions(accountId: AccountId) = error("unused")
        }
        val published = mutableListOf<Pair<String, DomainEvent>>()
        val publisher = object : DomainEventPublisher {
            override fun publish(topic: String, key: String, event: DomainEvent) {
                published += topic to event
            }
        }
        val eventStoreRepository = object : EventStoreRepository {
            override fun append(events: List<DomainEvent>) = Unit
        }

        val consumer = LedgerEventsConsumer(
            ledgerService = ledgerService,
            eventPublisher = publisher,
            envelopeMapper = EventEnvelopeMapper(mapper),
            eventStoreRepository = eventStoreRepository,
            metrics = LedgerMetrics(SimpleMeterRegistry())
        )

        consumer.onTradeExecuted(
            EventEnvelopeMapper(mapper).serialize(
                TradeExecuted(
                    aggregateId = trade.tradeId.value,
                    symbol = trade.symbol,
                    traceId = "trace-1",
                    trade = trade
                )
            )
        )

        assertTrue(published.isEmpty())
    }
}
