package com.orderbook.matching

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MatchingEngineApplication

fun main(args: Array<String>) {
    runApplication<MatchingEngineApplication>(*args)
}
