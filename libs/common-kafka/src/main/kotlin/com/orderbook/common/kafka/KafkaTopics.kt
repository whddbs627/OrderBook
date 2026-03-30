package com.orderbook.common.kafka

object KafkaTopics {
    const val ORDERS_RECEIVED = "orders.received"
    const val ORDERS_ACCEPTED = "orders.accepted"
    const val ORDERS_REJECTED = "orders.rejected"
    const val TRADES_EXECUTED = "trades.executed"
    const val LEDGER_APPENDED = "ledger.appended"
    const val BALANCES_UPDATED = "balances.updated"
    const val MARKET_DATA_UPDATED = "marketdata.updated"
}
