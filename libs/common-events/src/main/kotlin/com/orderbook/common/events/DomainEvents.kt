package com.orderbook.common.events

import java.time.Instant
import java.util.UUID

sealed interface DomainEvent {
    val eventId: String
    val occurredAt: Instant
    val aggregateId: String
    val symbol: Symbol?
    val accountId: AccountId?
    val traceId: String
}

data class OrderReceived(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol,
    override val accountId: AccountId,
    override val traceId: String,
    val order: Order
) : DomainEvent

data class OrderValidated(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol,
    override val accountId: AccountId,
    override val traceId: String,
    val order: Order
) : DomainEvent

data class OrderRejected(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol,
    override val accountId: AccountId,
    override val traceId: String,
    val reason: RiskRejectReason,
    val message: String,
    val order: Order
) : DomainEvent

data class OrderAccepted(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol,
    override val accountId: AccountId,
    override val traceId: String,
    val order: Order
) : DomainEvent

data class OrderPartiallyFilled(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol,
    override val accountId: AccountId,
    override val traceId: String,
    val order: Order
) : DomainEvent

data class OrderFilled(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol,
    override val accountId: AccountId,
    override val traceId: String,
    val order: Order
) : DomainEvent

data class OrderCanceled(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol,
    override val accountId: AccountId,
    override val traceId: String,
    val order: Order
) : DomainEvent

data class OrderAmended(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol,
    override val accountId: AccountId,
    override val traceId: String,
    val order: Order
) : DomainEvent

data class TradeExecuted(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol,
    override val accountId: AccountId? = null,
    override val traceId: String,
    val trade: Trade
) : DomainEvent

data class LedgerAppended(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol? = null,
    override val accountId: AccountId? = null,
    override val traceId: String,
    val entries: List<LedgerEntryCommand>
) : DomainEvent

data class BalanceUpdated(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol? = null,
    override val accountId: AccountId,
    override val traceId: String,
    val balance: BalanceSnapshot,
    val positions: List<PositionSnapshot>
) : DomainEvent

data class OrderBookUpdated(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    override val aggregateId: String,
    override val symbol: Symbol,
    override val accountId: AccountId? = null,
    override val traceId: String,
    val orderBookSnapshot: OrderBookSnapshot,
    val trades: List<Trade> = emptyList()
) : DomainEvent
