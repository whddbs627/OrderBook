package com.orderbook.gateway.web

import com.orderbook.common.events.AmendOrderRequest
import com.orderbook.common.events.PlaceOrderRequest
import com.orderbook.gateway.GatewayTargets
import io.netty.channel.ChannelOption
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class GatewayWebClientConfig {
    @Bean
    fun webClient(
        builder: WebClient.Builder,
        @Value("\${orderbook.webclient.connect-timeout-ms:2000}") connectTimeoutMs: Int,
        @Value("\${orderbook.webclient.response-timeout-ms:10000}") responseTimeoutMs: Long
    ): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .responseTimeout(Duration.ofMillis(responseTimeoutMs))
        return builder
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}

@RestController
@RequestMapping
class GatewayController(
    private val webClient: WebClient,
    private val targets: GatewayTargets
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    fun placeOrder(@RequestBody request: PlaceOrderRequest): Mono<String> {
        log.info("POST /orders account={} clientOrderId={} symbol={}", request.accountId, request.clientOrderId, request.symbol)
        return proxy("${targets.order}/orders", request)
    }

    @PostMapping("/orders/{orderId}/cancel")
    fun cancel(@PathVariable orderId: String): Mono<String> {
        log.info("POST /orders/{}/cancel", orderId)
        return webClient.post()
            .uri("${targets.order}/orders/$orderId/cancel")
            .retrieve()
            .bodyToMono(String::class.java)
    }

    @PostMapping("/orders/{orderId}/amend")
    fun amend(@PathVariable orderId: String, @RequestBody request: AmendOrderRequest): Mono<String> {
        log.info("POST /orders/{}/amend", orderId)
        return proxy("${targets.order}/orders/$orderId/amend", request)
    }

    @GetMapping("/orders/{orderId}")
    fun order(@PathVariable orderId: String): Mono<String> =
        webClient.get()
            .uri("${targets.order}/orders/$orderId")
            .retrieve()
            .bodyToMono(String::class.java)

    @GetMapping("/accounts/{accountId}/balances")
    fun balances(@PathVariable accountId: String): Mono<String> =
        webClient.get()
            .uri("${targets.query}/accounts/$accountId/balances")
            .retrieve()
            .bodyToMono(String::class.java)

    @GetMapping("/accounts/{accountId}/positions")
    fun positions(@PathVariable accountId: String): Mono<String> =
        webClient.get()
            .uri("${targets.query}/accounts/$accountId/positions")
            .retrieve()
            .bodyToMono(String::class.java)

    @GetMapping("/market-data/{symbol}/orderbook")
    fun orderBook(@PathVariable symbol: String): Mono<String> =
        webClient.get()
            .uri("${targets.marketData}/market-data/$symbol/orderbook")
            .retrieve()
            .bodyToMono(String::class.java)

    @GetMapping("/market-data/{symbol}/trades")
    fun trades(@PathVariable symbol: String): Mono<String> =
        webClient.get()
            .uri("${targets.marketData}/market-data/$symbol/trades")
            .retrieve()
            .bodyToMono(String::class.java)

    private fun proxy(uri: String, body: Any): Mono<String> =
        webClient.post()
            .uri(uri)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String::class.java)
}

@RestControllerAdvice
class GatewayExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(WebClientResponseException::class)
    fun handleUpstream(ex: WebClientResponseException): ResponseEntity<String> {
        log.warn("Upstream error: {} {}", ex.statusCode, ex.statusText)
        return ResponseEntity.status(ex.statusCode).body(ex.responseBodyAsString)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun handleUnexpected(ex: Exception): Map<String, String> {
        log.error("Gateway error", ex)
        return mapOf("message" to "Service temporarily unavailable")
    }
}
