package com.orderbook.common.observability

import io.micrometer.observation.ObservationRegistry

fun noopObservationRegistry(): ObservationRegistry = ObservationRegistry.create()
