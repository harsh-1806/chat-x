plugins {
    application
}

application {
    mainClass.set("dev.harsh.chatroom.client.ChatClient")
}

dependencies {
    implementation(project(":protocol"))

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
