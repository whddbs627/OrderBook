package com.orderbook.common.events

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class PlaceOrderRequest(
    @field:NotBlank(message = "accountId is required")
    val accountId: String,
    @field:NotBlank(message = "clientOrderId is required")
    val clientOrderId: String,
    @field:NotBlank(message = "symbol is required")
    val symbol: String,
    @field:NotNull(message = "side is required")
    val side: OrderSide,
    @field:DecimalMin(value = "0.01", message = "price must be positive")
    val price: BigDecimal,
    @field:Min(value = 1, message = "quantity must be at least 1")
    val quantity: Long
)

data class AmendOrderRequest(
    @field:DecimalMin(value = "0.01", message = "price must be positive")
    val price: BigDecimal,
    @field:Min(value = 1, message = "quantity must be at least 1")
    val quantity: Long
)

data class OrderResponse(
    val orderId: String,
    val accountId: String,
    val clientOrderId: String,
    val symbol: String,
    val side: OrderSide,
    val price: BigDecimal,
    val quantity: Long,
    val filledQuantity: Long,
    val remainingQuantity: Long,
    val status: OrderStatus,
    val version: Long
)

data class RiskEvaluationRequest(
    val command: PlaceOrderCommand
)

data class RiskEvaluationResponse(
    val approved: Boolean,
    val reason: RiskRejectReason? = null,
    val message: String? = null
)

data class MatchingRequest(
    val command: AcceptedOrderCommand
)

data class CancelMatchingRequest(
    val command: CancelOrderCommand
)

data class AmendMatchingRequest(
    val command: AmendOrderCommand
)

data class MatchingResponse(
    val result: MatchingResult
)

data class LedgerAppendRequest(
    val trades: List<Trade>,
    val traceId: String
)

data class LedgerAppendResult(
    val balances: List<BalanceSnapshot>,
    val positions: List<PositionSnapshot>
)

data class ProjectionUpdate(
    val orders: List<Order> = emptyList(),
    val balances: List<BalanceSnapshot> = emptyList(),
    val positions: List<PositionSnapshot> = emptyList(),
    val trades: List<Trade> = emptyList()
)

data class MarketDataUpdate(
    val orderBookSnapshot: OrderBookSnapshot,
    val recentTrades: List<Trade> = emptyList()
)

fun Order.toResponse(): OrderResponse = OrderResponse(
    orderId = orderId.value,
    accountId = accountId.value,
    clientOrderId = clientOrderId.value,
    symbol = symbol.value,
    side = side,
    price = price,
    quantity = quantity,
    filledQuantity = filledQuantity,
    remainingQuantity = remainingQuantity,
    status = status,
    version = version
)
