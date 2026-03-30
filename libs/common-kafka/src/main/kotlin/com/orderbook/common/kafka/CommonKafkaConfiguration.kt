package com.orderbook.common.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class CommonKafkaConfiguration {
    @Bean
    open fun eventEnvelopeMapper(objectMapper: ObjectMapper): EventEnvelopeMapper =
        EventEnvelopeMapper(objectMapper)
}
