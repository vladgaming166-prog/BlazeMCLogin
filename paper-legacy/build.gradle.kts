plugins {
    java
    id("com.gradleup.shadow")
}

val paperApi = "1.20.4-R0.1-SNAPSHOT"
val paperApiVersion = "1.20"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    disableAutoTargetJvm()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

sourceSets {
    main {
        java.setSrcDirs(listOf(rootProject.file("paper/src/main/java")))
        resources.setSrcDirs(listOf(rootProject.file("paper/src/main/resources")))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    compileOnly("net.kyori:adventure-api:4.25.0")
    implementation(project(":core"))
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
    archiveBaseName.set("BlazeMCLogin-Paper-Legacy")
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

tasks.register<JavaCompile>("compileAgainstPaperApi") {
    group = "verification"
    description = "Recompile the Paper adapter against $paperApi"
    classpath = sourceSets.main.get().compileClasspath
    source = sourceSets.main.get().java
    destinationDirectory.set(layout.buildDirectory.dir("compat-classes"))
    options.release.set(17)
    options.encoding = "UTF-8"
}
