plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    // Java 17 bytecode so the Legacy Paper JAR can run on Paper 1.20.4 (Java 17).
    options.release.set(17)
}

dependencies {
    compileOnly("net.kyori:adventure-api:4.25.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.25.0")
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    api("org.yaml:snakeyaml:2.3")
    api("com.zaxxer:HikariCP:5.1.0")
    api("org.xerial:sqlite-jdbc:3.47.1.0")
    api("com.mysql:mysql-connector-j:8.4.0")
    api("com.password4j:password4j:1.8.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("net.kyori:adventure-api:4.25.0")
    testImplementation("net.kyori:adventure-text-minimessage:4.25.0")
    testImplementation("net.kyori:adventure-text-serializer-plain:4.25.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}
