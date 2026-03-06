plugins {
    kotlin("jvm") version "2.0.21"
}

group = "one.ai-gate.generation"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}
val vertxVersion = "5.0.5"
dependencies {
    implementation("io.vertx:vertx-web-client:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:5.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
