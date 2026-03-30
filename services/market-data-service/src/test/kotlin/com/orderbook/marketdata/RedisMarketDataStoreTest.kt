package com.orderbook.marketdata

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.orderbook.common.events.MarketDataUpdate
import com.orderbook.common.events.OrderBookSnapshot
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.PriceLevel
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.Trade
import com.orderbook.common.events.TradeId
import com.orderbook.marketdata.service.MarketDataMetrics
import com.orderbook.marketdata.service.RedisMarketDataStore
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant

class RedisMarketDataStoreTest {
    @Test
    fun `redis-backed store persists orderbook and trims recent trades`() {
        assumeTrue(redisAvailable(), "Redis is not available on localhost:6379")
        val connectionFactory = redisConnectionFactory()
        try {
            val redisTemplate = ReactiveStringRedisTemplate(connectionFactory)
            val store = RedisMarketDataStore(
                redisTemplate,
                jacksonObjectMapper().findAndRegisterModules(),
                MarketDataMetrics(SimpleMeterRegistry())
            )
            val symbol = Symbol("AAPL")
            val snapshot = OrderBookSnapshot(
                symbol = symbol,
                bids = listOf(PriceLevel(BigDecimal("100.00"), 10)),
                asks = listOf(PriceLevel(BigDecimal("101.00"), 5)),
                lastUpdatedAt = Instant.parse("2026-03-23T00:00:00Z")
            )
            redisTemplate.connectionFactory.reactiveConnection.serverCommands().flushAll().block()

            repeat(205) { index ->
                val trade = Trade(
                    tradeId = TradeId("TRD-$index"),
                    symbol = symbol,
                    buyOrderId = OrderId("BUY-$index"),
                    sellOrderId = OrderId("SELL-$index"),
                    buyAccountId = com.orderbook.common.events.AccountId("ACC-001"),
                    sellAccountId = com.orderbook.common.events.AccountId("ACC-002"),
                    price = BigDecimal("100.00"),
                    quantity = 1,
                    occurredAt = Instant.parse("2026-03-23T00:00:00Z").plusSeconds(index.toLong())
                )
                store.apply(MarketDataUpdate(snapshot, listOf(trade)))
            }

            assertEquals(snapshot, store.orderBook(symbol))
            val trades = store.trades(symbol)
            assertEquals(200, trades.size)
            assertEquals(TradeId("TRD-204"), trades.first().tradeId)
            assertEquals(TradeId("TRD-5"), trades.last().tradeId)

            val duplicateTrade = Trade(
                tradeId = TradeId("TRD-204"),
                symbol = symbol,
                buyOrderId = OrderId("BUY-204"),
                sellOrderId = OrderId("SELL-204"),
                buyAccountId = com.orderbook.common.events.AccountId("ACC-001"),
                sellAccountId = com.orderbook.common.events.AccountId("ACC-002"),
                price = BigDecimal("100.00"),
                quantity = 1,
                occurredAt = Instant.parse("2026-03-23T00:03:24Z")
            )
            val newerSnapshot = snapshot.copy(lastUpdatedAt = Instant.parse("2026-03-23T00:10:00Z"))
            val staleSnapshot = snapshot.copy(lastUpdatedAt = Instant.parse("2026-03-22T23:59:59Z"))

            store.apply(MarketDataUpdate(newerSnapshot, listOf(duplicateTrade)))
            store.apply(MarketDataUpdate(staleSnapshot, listOf(duplicateTrade)))

            val deduplicatedTrades = store.trades(symbol)
            assertEquals(200, deduplicatedTrades.size)
            assertEquals(1, deduplicatedTrades.count { it.tradeId == TradeId("TRD-204") })
            assertEquals(newerSnapshot, store.orderBook(symbol))
        } finally {
            connectionFactory.destroy()
        }
    }

    private fun redisConnectionFactory(): LettuceConnectionFactory =
        LettuceConnectionFactory(RedisStandaloneConfiguration("localhost", 6379)).apply {
            afterPropertiesSet()
        }

    private fun redisAvailable(): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("localhost", 6379), 200)
                true
            }
        } catch (_: Exception) {
            false
        }
}
