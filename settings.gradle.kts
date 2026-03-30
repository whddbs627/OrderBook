rootProject.name = "orderbook"

include(
    "libs:common-events",
    "libs:common-kafka",
    "libs:common-observability",
    "services:gateway",
    "services:order-service",
    "services:matching-engine",
    "services:risk-service",
    "services:ledger-service",
    "services:market-data-service",
    "services:query-service",
    "load-test"
)
