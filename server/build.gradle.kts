plugins {
    application
}

application {
    mainClass.set("dev.harsh.chatroom.server.ChatServer")
}

dependencies {
    implementation(project(":protocol"))

    // Configuration
    implementation("com.typesafe:config:1.4.3")

    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Metrics
    implementation("io.micrometer:micrometer-core:1.14.4")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.4")

    // Password hashing
    implementation("at.favre.lib:bcrypt:0.10.2")

    // Async testing
    testImplementation("org.awaitility:awaitility:4.2.0")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
