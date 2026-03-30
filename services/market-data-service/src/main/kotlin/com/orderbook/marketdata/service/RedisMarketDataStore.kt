package com.orderbook.marketdata.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.orderbook.common.events.MarketDataUpdate
import com.orderbook.common.events.OrderBookSnapshot
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.Trade
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

@Service
class RedisMarketDataStore(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val metrics: MarketDataMetrics
) : MarketDataStore {
    private val sinks = ConcurrentHashMap<String, Sinks.Many<String>>()

    override fun apply(update: MarketDataUpdate) {
        val symbol = update.orderBookSnapshot.symbol.value
        val existingSnapshot = orderBook(update.orderBookSnapshot.symbol)
        if (existingSnapshot == null || !existingSnapshot.lastUpdatedAt.isAfter(update.orderBookSnapshot.lastUpdatedAt)) {
            redisTemplate.opsForValue()
                .set(orderBookKey(symbol), write(update.orderBookSnapshot))
                .block()
            metrics.incrementSnapshotWrite()
        }

        if (update.recentTrades.isNotEmpty()) {
            val tradesKey = tradesKey(symbol)
            update.recentTrades
                .distinctBy { it.tradeId }
                .map(::write)
                .forEach { payload ->
                    redisTemplate.opsForList()
                        .remove(tradesKey, 0, payload)
                        .then(redisTemplate.opsForList().rightPush(tradesKey, payload))
                        .then(redisTemplate.opsForList().trim(tradesKey, -MAX_TRADES.toLong(), -1))
                        .block()
                }
        }

        sink("orderbook:$symbol")
            .tryEmitNext("""{"symbol":"$symbol","type":"orderbook"}""")
        if (update.recentTrades.isNotEmpty()) {
            sink("trades:$symbol")
                .tryEmitNext("""{"symbol":"$symbol","type":"trade","count":${update.recentTrades.size}}""")
        }
    }

    override fun orderBook(symbol: Symbol): OrderBookSnapshot? =
        redisTemplate.opsForValue()
            .get(orderBookKey(symbol.value))
            .map { read(it, OrderBookSnapshot::class.java) }
            .block()

    override fun reset() {
        val keys = redisTemplate.keys("marketdata:*").collectList().block().orEmpty()
        if (keys.isNotEmpty()) {
            redisTemplate.delete(*keys.toTypedArray()).block()
        }
    }

    override fun trades(symbol: Symbol): List<Trade> =
        redisTemplate.opsForList()
            .range(tradesKey(symbol.value), 0, -1)
            .map { read(it, Trade::class.java) }
            .collectList()
            .block()
            ?.sortedByDescending { it.occurredAt }
            ?: emptyList()

    override fun stream(channel: String): Flux<String> = sink(channel).asFlux()

    private fun orderBookKey(symbol: String) = "marketdata:orderbook:$symbol"

    private fun tradesKey(symbol: String) = "marketdata:trades:$symbol"

    private fun sink(channel: String): Sinks.Many<String> =
        sinks.computeIfAbsent(channel) { Sinks.many().multicast().onBackpressureBuffer() }

    private fun write(value: Any): String = objectMapper.writeValueAsString(value)

    private fun <T> read(value: String, type: Class<T>): T = objectMapper.readValue(value, type)

    private companion object {
        const val MAX_TRADES = 200
    }
}
