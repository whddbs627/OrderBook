package com.orderbook.order.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.TimeoutException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(WebExchangeBindException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: WebExchangeBindException): Map<String, Any> {
        val errors = ex.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return mapOf("message" to "Validation failed", "errors" to errors)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(ex: IllegalArgumentException) = mapOf("message" to (ex.message ?: "Invalid request"))

    @ExceptionHandler(OptimisticLockingFailureException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleOptimisticLock(ex: OptimisticLockingFailureException) =
        mapOf("message" to (ex.message ?: "Resource was modified concurrently"))

    @ExceptionHandler(WebClientResponseException::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun handleUpstreamError(ex: WebClientResponseException): Map<String, Any> {
        log.error("Upstream service error: {} {}", ex.statusCode, ex.statusText, ex)
        return mapOf(
            "message" to "Upstream service error",
            "status" to ex.statusCode.value()
        )
    }

    @ExceptionHandler(TimeoutException::class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    fun handleTimeout(ex: TimeoutException): Map<String, String> {
        log.error("Upstream service timeout", ex)
        return mapOf("message" to "Upstream service did not respond in time")
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleUnexpected(ex: Exception): Map<String, String> {
        log.error("Unexpected error", ex)
        return mapOf("message" to "Internal server error")
    }
}
