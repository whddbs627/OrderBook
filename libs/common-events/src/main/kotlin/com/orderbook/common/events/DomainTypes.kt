package com.orderbook.common.events

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import java.time.Instant

@JvmInline
value class AccountId(val value: String)

@JvmInline
value class OrderId(val value: String)

@JvmInline
value class TradeId(val value: String)

@JvmInline
value class ClientOrderId(val value: String)

@JvmInline
value class Symbol(val value: String)

enum class OrderSide {
    BUY,
    SELL
}

enum class OrderStatus {
    RECEIVED,
    REJECTED,
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    AMENDED
}

enum class RiskRejectReason {
    ACCOUNT_NOT_FOUND,
    INSUFFICIENT_CASH,
    INSUFFICIENT_POSITION,
    INVALID_PRICE,
    INVALID_QUANTITY,
    ACCOUNT_BLOCKED
}

enum class LedgerEntryType {
    CASH_DEBIT,
    CASH_CREDIT,
    POSITION_DEBIT,
    POSITION_CREDIT
}

data class PlaceOrderCommand(
    val accountId: AccountId,
    val clientOrderId: ClientOrderId,
    val symbol: Symbol,
    val side: OrderSide,
    val price: BigDecimal,
    val quantity: Long,
    val traceId: String
)

data class CancelOrderCommand(
    val orderId: OrderId,
    val traceId: String
)

data class AmendOrderCommand(
    val orderId: OrderId,
    val newPrice: BigDecimal,
    val newQuantity: Long,
    val traceId: String
)

data class AcceptedOrderCommand(
    val orderId: OrderId,
    val accountId: AccountId,
    val clientOrderId: ClientOrderId,
    val symbol: Symbol,
    val side: OrderSide,
    val price: BigDecimal,
    val quantity: Long,
    val filledQuantity: Long = 0,
    val occurredAt: Instant = Instant.now(),
    val traceId: String
)

data class Order(
    val orderId: OrderId,
    val accountId: AccountId,
    val clientOrderId: ClientOrderId,
    val symbol: Symbol,
    val side: OrderSide,
    val price: BigDecimal,
    val quantity: Long,
    val filledQuantity: Long,
    val status: OrderStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 0
) {
    @get:JsonIgnore
    val remainingQuantity: Long
        get() = quantity - filledQuantity
}

data class Trade(
    val tradeId: TradeId,
    val symbol: Symbol,
    val buyOrderId: OrderId,
    val sellOrderId: OrderId,
    val buyAccountId: AccountId,
    val sellAccountId: AccountId,
    val price: BigDecimal,
    val quantity: Long,
    val occurredAt: Instant
)

data class PositionSnapshot(
    val accountId: AccountId,
    val symbol: Symbol,
    val quantity: Long
)

data class AccountSnapshot(
    val accountId: AccountId,
    val cashBalance: BigDecimal,
    val blocked: Boolean,
    val positions: Map<Symbol, Long>,
    val lockedCash: BigDecimal = BigDecimal.ZERO,
    val lockedPositions: Map<Symbol, Long> = emptyMap()
) {
    @get:JsonIgnore
    val availableCash: BigDecimal
        get() = cashBalance.subtract(lockedCash)

    fun availablePosition(symbol: Symbol): Long =
        (positions[symbol] ?: 0L) - (lockedPositions[symbol] ?: 0L)
}

data class BalanceSnapshot(
    val accountId: AccountId,
    val cashBalance: BigDecimal,
    val updatedAt: Instant
)

data class LedgerEntryCommand(
    val accountId: AccountId,
    val symbol: Symbol?,
    val entryType: LedgerEntryType,
    val amount: BigDecimal,
    val quantity: Long,
    val referenceId: String,
    val occurredAt: Instant
)

sealed interface RiskDecision {
    data class Approved(val accountSnapshot: AccountSnapshot) : RiskDecision
    data class Rejected(val reason: RiskRejectReason, val message: String) : RiskDecision
}

data class PriceLevel(
    val price: BigDecimal,
    val quantity: Long
)

data class OrderBookSnapshot(
    val symbol: Symbol,
    val bids: List<PriceLevel>,
    val asks: List<PriceLevel>,
    val lastUpdatedAt: Instant
)

data class MatchingResult(
    val order: Order,
    val trades: List<Trade>,
    val orderBookSnapshot: OrderBookSnapshot
)
