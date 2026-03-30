plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":libs:common-events"))
    api("org.springframework.kafka:spring-kafka:3.3.5")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.4")
}
