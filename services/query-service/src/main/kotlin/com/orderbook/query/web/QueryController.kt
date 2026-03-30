package com.orderbook.query.web

import com.orderbook.common.events.AccountId
import com.orderbook.common.events.OrderId
import com.orderbook.common.events.ProjectionUpdate
import com.orderbook.common.events.toResponse
import com.orderbook.query.service.ProjectionStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
@RequestMapping
class QueryController(
    private val projectionStore: ProjectionStore
) {
    @PostMapping("/internal/query/apply")
    fun apply(@RequestBody update: ProjectionUpdate): Mono<Void> =
        Mono.fromCallable<Unit> { projectionStore.apply(update) }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    @GetMapping("/orders/{orderId}")
    fun order(@PathVariable orderId: String) =
        Mono.fromCallable { projectionStore.order(OrderId(orderId))?.toResponse() }
            .subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/accounts/{accountId}/balances")
    fun balances(@PathVariable accountId: String) =
        Mono.fromCallable { projectionStore.balances(AccountId(accountId)) }
            .subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/accounts/{accountId}/positions")
    fun positions(@PathVariable accountId: String) =
        Mono.fromCallable { projectionStore.positions(AccountId(accountId)) }
            .subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/trades")
    fun trades(@RequestParam(required = false) symbol: String?) =
        Mono.fromCallable { projectionStore.trades(symbol) }
            .subscribeOn(Schedulers.boundedElastic())
}
