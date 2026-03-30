package com.orderbook.order

import com.orderbook.common.kafka.CommonKafkaConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(ServiceEndpoints::class)
@EnableScheduling
@Import(CommonKafkaConfiguration::class)
class OrderServiceApplication

fun main(args: Array<String>) {
    runApplication<OrderServiceApplication>(*args)
}

@ConfigurationProperties(prefix = "services")
data class ServiceEndpoints(
    val risk: String = "http://localhost:8082",
    val matching: String = "http://localhost:8083",
    val ledger: String = "http://localhost:8084",
    val query: String = "http://localhost:8085",
    val marketData: String = "http://localhost:8086"
)
