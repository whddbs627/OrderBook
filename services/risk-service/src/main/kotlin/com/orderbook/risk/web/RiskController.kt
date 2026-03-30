package com.orderbook.risk.web

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.RiskEvaluationRequest
import com.orderbook.common.events.RiskEvaluationResponse
import com.orderbook.common.events.RiskDecision
import com.orderbook.risk.service.InMemoryRiskEvaluator
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/risk")
class RiskController(
    private val riskEvaluator: InMemoryRiskEvaluator
) {
    @PostMapping("/evaluate")
    suspend fun evaluate(@RequestBody request: RiskEvaluationRequest): RiskEvaluationResponse =
        when (val decision = riskEvaluator.evaluate(request.command)) {
            is RiskDecision.Approved -> RiskEvaluationResponse(
                approved = true
            )

            is RiskDecision.Rejected -> RiskEvaluationResponse(
                approved = false,
                reason = decision.reason,
                message = decision.message
            )
        }

    @GetMapping("/accounts/{accountId}")
    fun snapshot(@PathVariable accountId: String) = riskEvaluator.snapshot(AccountId(accountId))
}
