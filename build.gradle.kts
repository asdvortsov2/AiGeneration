plugins {
    kotlin("jvm") version "2.0.21"
    id("maven-publish")
}

group = "com.github.asdvortsov2"
version = "1.0.3"

repositories {
    mavenCentral()
}
val vertxVersion = "5.0.5"
dependencies {
    implementation("io.vertx:vertx-web-client:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:5.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(kotlin("stdlib"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.github.asdvortsov2"  // обязательно com.github.твой-ник
            artifactId = "AiGeneration"
            version = project.version.toString()  // бери версию из gradle.properties
        }
    }
}
