package com.orderbook.order.repository

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.ClientOrderId
import com.orderbook.common.events.DomainEvent
import com.orderbook.common.events.Order
import com.orderbook.common.events.OrderId

interface OrderRepository {
    fun save(order: Order): Order
    fun find(orderId: OrderId): Order?
    fun findByAccountAndClientOrderId(accountId: AccountId, clientOrderId: ClientOrderId): Order?
    fun appendEvents(events: List<DomainEvent>)
    fun events(): List<DomainEvent>
}
