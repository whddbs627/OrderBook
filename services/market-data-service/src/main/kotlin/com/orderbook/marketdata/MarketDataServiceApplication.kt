package com.orderbook.marketdata

import com.orderbook.common.kafka.CommonKafkaConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication
@EnableKafka
@Import(CommonKafkaConfiguration::class)
class MarketDataServiceApplication

fun main(args: Array<String>) {
    runApplication<MarketDataServiceApplication>(*args)
}
