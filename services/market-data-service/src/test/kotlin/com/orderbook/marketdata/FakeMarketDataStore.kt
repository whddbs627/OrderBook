package com.orderbook.marketdata

import com.orderbook.common.events.MarketDataUpdate
import com.orderbook.common.events.OrderBookSnapshot
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.Trade
import com.orderbook.marketdata.service.MarketDataStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

class FakeMarketDataStore : MarketDataStore {
    private val orderBooks = ConcurrentHashMap<Symbol, OrderBookSnapshot>()
    private val trades = ConcurrentHashMap<Symbol, MutableList<Trade>>()
    private val sinks = ConcurrentHashMap<String, Sinks.Many<String>>()

    override fun apply(update: MarketDataUpdate) {
        orderBooks[update.orderBookSnapshot.symbol] = update.orderBookSnapshot
        if (update.recentTrades.isNotEmpty()) {
            val bucket = trades.computeIfAbsent(update.orderBookSnapshot.symbol) { mutableListOf() }
            bucket += update.recentTrades
            if (bucket.size > 200) {
                repeat(bucket.size - 200) { bucket.removeFirst() }
            }
        }
    }

    override fun orderBook(symbol: Symbol): OrderBookSnapshot? = orderBooks[symbol]

    override fun reset() {
        orderBooks.clear()
        trades.clear()
    }

    override fun trades(symbol: Symbol): List<Trade> = trades[symbol]?.sortedByDescending { it.occurredAt } ?: emptyList()

    override fun stream(channel: String): Flux<String> = sinks.computeIfAbsent(channel) {
        Sinks.many().multicast().onBackpressureBuffer()
    }.asFlux()
}
