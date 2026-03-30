package com.orderbook.ledger.web

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.LedgerAppendRequest
import com.orderbook.common.events.LedgerAppendResult
import com.orderbook.common.events.PositionSnapshot
import com.orderbook.ledger.service.LedgerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ledger")
class LedgerController(
    private val ledgerService: LedgerService
) {
    @PostMapping("/append")
    suspend fun append(@RequestBody request: LedgerAppendRequest): LedgerAppendResult =
        ledgerService.append(request.trades, request.traceId)

    @GetMapping("/accounts/{accountId}/balances")
    fun balance(@PathVariable accountId: String) = ledgerService.balance(AccountId(accountId))

    @GetMapping("/accounts/{accountId}/positions")
    fun positions(@PathVariable accountId: String): List<PositionSnapshot> =
        ledgerService.positions(AccountId(accountId))
}
