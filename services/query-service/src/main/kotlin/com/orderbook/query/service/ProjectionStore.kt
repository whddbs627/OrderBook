package com.orderbook.query.service

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.BalanceSnapshot
import com.orderbook.common.events.Order
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.PositionSnapshot
import com.orderbook.common.events.ProjectionUpdate
import com.orderbook.common.events.Trade

interface ProjectionStore {
    fun apply(update: ProjectionUpdate)
    fun reset()
    fun order(orderId: OrderId): Order?
    fun balances(accountId: AccountId): BalanceSnapshot?
    fun positions(accountId: AccountId): List<PositionSnapshot>
    fun trades(symbol: String?): List<Trade>
}
