package com.orderbook.order.web

import com.orderbook.common.events.AmendOrderRequest
import com.orderbook.common.events.PlaceOrderRequest
import com.orderbook.order.service.OrderWorkflowService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
@RequestMapping
class OrderController(
    private val workflowService: OrderWorkflowService
) {
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    fun place(@Valid @RequestBody request: PlaceOrderRequest) =
        Mono.fromCallable { workflowService.placeOrder(request) }
            .subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/orders/{orderId}/cancel")
    fun cancel(@PathVariable orderId: String) =
        Mono.fromCallable { workflowService.cancelOrder(orderId) }
            .subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/orders/{orderId}/amend")
    fun amend(@PathVariable orderId: String, @Valid @RequestBody request: AmendOrderRequest) =
        Mono.fromCallable { workflowService.amendOrder(orderId, request.price, request.quantity) }
            .subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/orders/{orderId}")
    fun order(@PathVariable orderId: String) =
        Mono.fromCallable { workflowService.order(orderId) }
            .subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/internal/events")
    fun events() =
        Mono.fromCallable { workflowService.events() }
            .subscribeOn(Schedulers.boundedElastic())
}
