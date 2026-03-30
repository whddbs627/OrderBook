plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(kotlin("stdlib"))
    api("com.fasterxml.jackson.core:jackson-annotations:2.18.4")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.4")
    compileOnly("jakarta.validation:jakarta.validation-api:3.1.0")
}
