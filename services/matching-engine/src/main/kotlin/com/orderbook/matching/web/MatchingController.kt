package com.orderbook.matching.web

import com.orderbook.common.events.AmendMatchingRequest
import com.orderbook.common.events.CancelMatchingRequest
import com.orderbook.common.events.MatchingRequest
import com.orderbook.common.events.MatchingResponse
import com.orderbook.common.events.OrderBookSnapshot
import com.orderbook.common.events.OrderResponse
import com.orderbook.common.events.Symbol
import com.orderbook.common.events.toResponse
import com.orderbook.matching.service.OrderBookMatchingService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/matching")
class MatchingController(
    private val matchingService: OrderBookMatchingService
) {
    @PostMapping("/process")
    suspend fun process(@RequestBody request: MatchingRequest): MatchingResponse =
        MatchingResponse(matchingService.process(request.command))

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.OK)
    suspend fun cancel(@RequestBody request: CancelMatchingRequest): OrderResponse? =
        matchingService.cancel(request.command)?.toResponse()

    @PostMapping("/amend")
    suspend fun amend(@RequestBody request: AmendMatchingRequest): MatchingResponse? =
        matchingService.amend(request.command)?.let(::MatchingResponse)

    @GetMapping("/orderbooks/{symbol}")
    fun snapshot(@PathVariable symbol: String): OrderBookSnapshot = matchingService.snapshot(Symbol(symbol))
}
