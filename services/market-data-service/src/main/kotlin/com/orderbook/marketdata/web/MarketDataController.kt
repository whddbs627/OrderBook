package com.orderbook.marketdata.web

import com.orderbook.common.events.MarketDataUpdate
import com.orderbook.common.events.Symbol
import com.orderbook.marketdata.service.MarketDataStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
@RequestMapping
class MarketDataController(
    private val marketDataStore: MarketDataStore
) {
    @PostMapping("/internal/market-data/apply")
    fun apply(@RequestBody update: MarketDataUpdate): Mono<Void> =
        Mono.fromCallable<Unit> { marketDataStore.apply(update) }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    @GetMapping("/market-data/{symbol}/orderbook")
    fun orderBook(@PathVariable symbol: String) =
        Mono.fromCallable { marketDataStore.orderBook(Symbol(symbol)) }
            .subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/market-data/{symbol}/trades")
    fun trades(@PathVariable symbol: String) =
        Mono.fromCallable { marketDataStore.trades(Symbol(symbol)) }
            .subscribeOn(Schedulers.boundedElastic())
}
