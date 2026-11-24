plugins {
    java
    id("com.gradleup.shadow")
}

val paperApi = "1.21.11-R0.1-SNAPSHOT"
val paperApiVersion = "1.21"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

sourceSets {
    main {
        java.setSrcDirs(listOf(rootProject.file("paper/src/main/java")))
        resources.setSrcDirs(listOf(rootProject.file("paper/src/main/resources")))
    }
    test {
        java.setSrcDirs(listOf(rootProject.file("paper/src/test/java")))
        resources.setSrcDirs(listOf(rootProject.file("paper/src/test/resources")))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    compileOnly("net.kyori:adventure-api:4.25.0")
    implementation(project(":core"))
    testImplementation("io.papermc.paper:paper-api:$paperApi")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation(project(":core"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "apiVersion" to paperApiVersion
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("BlazeMCLogin-Paper-1.21.x")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    mergeServiceFiles()
    relocate("org.yaml.snakeyaml", "net.blazemc.login.libs.snakeyaml")
    relocate("com.zaxxer.hikari", "net.blazemc.login.libs.hikari")
    relocate("com.mysql", "net.blazemc.login.libs.mysql")
    relocate("com.password4j", "net.blazemc.login.libs.password4j")
    // sqlite-jdbc must keep org.sqlite — JNI native methods bind to that package.
    exclude("META-INF/maven/**")
    exclude("META-INF/INDEX.LIST")
}

tasks.named("build") { dependsOn(tasks.shadowJar) }
tasks.named("assemble") { dependsOn(tasks.shadowJar) }
