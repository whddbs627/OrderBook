package com.orderbook.risk

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.ClientOrderId
import com.orderbook.common.events.OrderSide
import com.orderbook.common.events.PlaceOrderCommand
import com.orderbook.common.events.RiskDecision
import com.orderbook.common.events.RiskRejectReason
import com.orderbook.common.events.Symbol
import com.orderbook.risk.service.InMemoryRiskEvaluator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class InMemoryRiskEvaluatorTest {
    private val evaluator = InMemoryRiskEvaluator()

    @Test
    fun `buy order is approved when account has enough cash`() = runBlocking {
        val decision = evaluator.evaluate(
            PlaceOrderCommand(
                accountId = AccountId("ACC-001"),
                clientOrderId = ClientOrderId("C1"),
                symbol = Symbol("AAPL"),
                side = OrderSide.BUY,
                price = BigDecimal("100"),
                quantity = 10,
                traceId = UUID.randomUUID().toString()
            )
        )

        assertTrue(decision is RiskDecision.Approved)
    }

    @Test
    fun `approved buy order reserves cash for subsequent evaluations`() = runBlocking {
        val first = evaluator.evaluate(
            PlaceOrderCommand(
                accountId = AccountId("ACC-001"),
                clientOrderId = ClientOrderId("C1"),
                symbol = Symbol("AAPL"),
                side = OrderSide.BUY,
                price = BigDecimal("100"),
                quantity = 9000,
                traceId = UUID.randomUUID().toString()
            )
        )
        val second = evaluator.evaluate(
            PlaceOrderCommand(
                accountId = AccountId("ACC-001"),
                clientOrderId = ClientOrderId("C2"),
                symbol = Symbol("AAPL"),
                side = OrderSide.BUY,
                price = BigDecimal("100"),
                quantity = 2000,
                traceId = UUID.randomUUID().toString()
            )
        )

        assertTrue(first is RiskDecision.Approved)
        assertTrue(second is RiskDecision.Rejected)
        assertEquals(RiskRejectReason.INSUFFICIENT_CASH, (second as RiskDecision.Rejected).reason)
    }

    @Test
    fun `sell order is rejected when there is no position`() = runBlocking {
        val decision = evaluator.evaluate(
            PlaceOrderCommand(
                accountId = AccountId("ACC-001"),
                clientOrderId = ClientOrderId("C2"),
                symbol = Symbol("TSLA"),
                side = OrderSide.SELL,
                price = BigDecimal("100"),
                quantity = 200,
                traceId = UUID.randomUUID().toString()
            )
        )

        assertTrue(decision is RiskDecision.Rejected)
        assertEquals(RiskRejectReason.INSUFFICIENT_POSITION, (decision as RiskDecision.Rejected).reason)
    }

    @Test
    fun `approved sell order reserves position for subsequent evaluations`() = runBlocking {
        val first = evaluator.evaluate(
            PlaceOrderCommand(
                accountId = AccountId("ACC-001"),
                clientOrderId = ClientOrderId("C3"),
                symbol = Symbol("AAPL"),
                side = OrderSide.SELL,
                price = BigDecimal("100"),
                quantity = 250,
                traceId = UUID.randomUUID().toString()
            )
        )
        val second = evaluator.evaluate(
            PlaceOrderCommand(
                accountId = AccountId("ACC-001"),
                clientOrderId = ClientOrderId("C4"),
                symbol = Symbol("AAPL"),
                side = OrderSide.SELL,
                price = BigDecimal("100"),
                quantity = 100,
                traceId = UUID.randomUUID().toString()
            )
        )

        assertTrue(first is RiskDecision.Approved)
        assertTrue(second is RiskDecision.Rejected)
        assertEquals(RiskRejectReason.INSUFFICIENT_POSITION, (second as RiskDecision.Rejected).reason)
    }
}
