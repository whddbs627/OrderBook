package com.orderbook.query

import com.orderbook.common.kafka.CommonKafkaConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication
@EnableKafka
@Import(CommonKafkaConfiguration::class)
class QueryServiceApplication

fun main(args: Array<String>) {
    runApplication<QueryServiceApplication>(*args)
}
