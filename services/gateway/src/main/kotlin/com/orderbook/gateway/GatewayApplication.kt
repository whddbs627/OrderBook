package com.orderbook.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(GatewayTargets::class)
class GatewayApplication

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}

@ConfigurationProperties(prefix = "services")
data class GatewayTargets(
    val order: String = "http://localhost:8081",
    val query: String = "http://localhost:8085",
    val marketData: String = "http://localhost:8086"
)
