package com.orderbook.marketdata.websocket

import com.orderbook.marketdata.service.MarketDataStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

@Configuration
class WebSocketConfig {
    @Bean
    fun orderBookWebSocketHandler(store: MarketDataStore): WebSocketHandler =
        WebSocketHandler { session ->
            val symbol = session.handshakeInfo.uri.path.substringAfterLast("/")
            session.send(store.stream("orderbook:$symbol").map(session::textMessage))
        }

    @Bean
    fun tradesWebSocketHandler(store: MarketDataStore): WebSocketHandler =
        WebSocketHandler { session ->
            val symbol = session.handshakeInfo.uri.path.substringAfterLast("/")
            session.send(store.stream("trades:$symbol").map(session::textMessage))
        }

    @Bean
    fun handlerMapping(
        orderBookWebSocketHandler: WebSocketHandler,
        tradesWebSocketHandler: WebSocketHandler
    ): HandlerMapping =
        SimpleUrlHandlerMapping().apply {
            urlMap = mapOf(
                "/ws/market-data/orderbook/**" to orderBookWebSocketHandler,
                "/ws/market-data/trades/**" to tradesWebSocketHandler
            )
            order = -1
        }


    @Bean
    fun handlerAdapter() = WebSocketHandlerAdapter()
}
