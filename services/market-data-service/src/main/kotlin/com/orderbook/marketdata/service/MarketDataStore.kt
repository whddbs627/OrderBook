package com.orderbook.marketdata.service

import com.orderbook.common.events.MarketDataUpdate
import com.orderbook.common.events.OrderBookSnapshot
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.Trade
import reactor.core.publisher.Flux

interface MarketDataStore {
    fun apply(update: MarketDataUpdate)
    fun reset()
    fun orderBook(symbol: Symbol): OrderBookSnapshot?
    fun trades(symbol: Symbol): List<Trade>
    fun stream(channel: String): Flux<String>
}
