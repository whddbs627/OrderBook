package com.orderbook.order.client

import com.orderbook.common.events.AcceptedOrderCommand
import com.orderbook.common.events.AmendMatchingRequest
import com.orderbook.common.events.AmendOrderCommand
import com.orderbook.common.events.CancelMatchingRequest
import com.orderbook.common.events.CancelOrderCommand
import com.orderbook.common.events.LedgerAppendRequest
import com.orderbook.common.events.LedgerAppendResult
import com.orderbook.common.events.MarketDataUpdate
import com.orderbook.common.events.MatchingRequest
import com.orderbook.common.events.MatchingResponse
import com.orderbook.common.events.OrderBookSnapshot
import com.orderbook.common.events.OrderResponse
import com.orderbook.common.events.PlaceOrderCommand
import com.orderbook.common.events.ProjectionUpdate
import com.orderbook.common.events.RiskEvaluationRequest
import com.orderbook.common.events.RiskEvaluationResponse
import com.orderbook.order.ServiceEndpoints
import io.netty.channel.ChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig {
    @Bean
    fun webClient(
        builder: WebClient.Builder,
        @Value("\${orderbook.webclient.connect-timeout-ms:2000}") connectTimeoutMs: Int,
        @Value("\${orderbook.webclient.response-timeout-ms:5000}") responseTimeoutMs: Long
    ): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .responseTimeout(Duration.ofMillis(responseTimeoutMs))
        return builder
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}

@Component
class RiskClient(
    private val webClient: WebClient,
    private val endpoints: ServiceEndpoints
) {
    fun evaluate(command: PlaceOrderCommand): RiskEvaluationResponse =
        webClient.post()
            .uri("${endpoints.risk}/internal/risk/evaluate")
            .bodyValue(RiskEvaluationRequest(command))
            .retrieve()
            .bodyToMono(RiskEvaluationResponse::class.java)
            .block()!!
}

@Component
class MatchingClient(
    private val webClient: WebClient,
    private val endpoints: ServiceEndpoints
) {
    fun process(command: AcceptedOrderCommand): MatchingResponse =
        webClient.post()
            .uri("${endpoints.matching}/internal/matching/process")
            .bodyValue(MatchingRequest(command))
            .retrieve()
            .bodyToMono(MatchingResponse::class.java)
            .block()!!

    fun cancel(command: CancelOrderCommand): OrderResponse? =
        webClient.post()
            .uri("${endpoints.matching}/internal/matching/cancel")
            .bodyValue(CancelMatchingRequest(command))
            .retrieve()
            .bodyToMono(OrderResponse::class.java)
            .block()

    fun amend(command: AmendOrderCommand): MatchingResponse? =
        webClient.post()
            .uri("${endpoints.matching}/internal/matching/amend")
            .bodyValue(AmendMatchingRequest(command))
            .retrieve()
            .bodyToMono(MatchingResponse::class.java)
            .block()

    fun snapshot(symbol: String): OrderBookSnapshot =
        webClient.get()
            .uri("${endpoints.matching}/internal/matching/orderbooks/$symbol")
            .retrieve()
            .bodyToMono(OrderBookSnapshot::class.java)
            .block()!!
}

@Component
class LedgerClient(
    private val webClient: WebClient,
    private val endpoints: ServiceEndpoints
) {
    fun append(request: LedgerAppendRequest): LedgerAppendResult =
        webClient.post()
            .uri("${endpoints.ledger}/internal/ledger/append")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(LedgerAppendResult::class.java)
            .block()!!
}

@Component
class QueryProjectionClient(
    private val webClient: WebClient,
    private val endpoints: ServiceEndpoints
) {
    fun apply(update: ProjectionUpdate) {
        webClient.post()
            .uri("${endpoints.query}/internal/query/apply")
            .bodyValue(update)
            .retrieve()
            .toBodilessEntity()
            .block()
    }
}

@Component
class MarketDataClient(
    private val webClient: WebClient,
    private val endpoints: ServiceEndpoints
) {
    fun apply(update: MarketDataUpdate) {
        webClient.post()
            .uri("${endpoints.marketData}/internal/market-data/apply")
            .bodyValue(update)
            .retrieve()
            .toBodilessEntity()
            .block()
    }
}
