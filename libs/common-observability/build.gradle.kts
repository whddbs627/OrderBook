plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api("io.micrometer:micrometer-core:1.14.5")
    api("io.micrometer:micrometer-observation:1.14.5")
}
