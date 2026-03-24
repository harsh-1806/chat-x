plugins {
    java
}

allprojects {
    group = "dev.harsh.chatroom"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }

    dependencies {
        // Logging
        implementation("org.slf4j:slf4j-api:2.0.12")

        // Testing
        testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
        testImplementation("org.mockito:mockito-core:5.10.0")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
}
