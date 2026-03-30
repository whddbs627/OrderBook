package com.orderbook.ledger.service

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.BalanceSnapshot
import com.orderbook.common.events.LedgerAppendResult
import com.orderbook.common.events.PositionSnapshot
import com.orderbook.common.events.Trade

interface LedgerService {
    fun append(trades: List<Trade>, traceId: String): LedgerAppendResult
    fun balance(accountId: AccountId): BalanceSnapshot
    fun positions(accountId: AccountId): List<PositionSnapshot>
}
