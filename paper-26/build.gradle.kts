plugins {
    java
    id("com.gradleup.shadow")
}

// 26.1 without a patch has no public paper-api artifact. 26.1.2 is the stable 26.1 family.
// The Bukkit/Paper APIs this plugin uses remain source-compatible with 26.2 and 26.3.
val paperApi = "26.1.2.build.74-stable"
val paperApiVersion = "1.21"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    disableAutoTargetJvm()
}

tasks.withType<JavaCompile>().configureEach {
    // JDK 25 is required to *read* paper-api 26, but the plugin itself
    // stays on Java 21 bytecode so Shadow's relocator can process it.
    // Paper 26 servers run Java 25 and load this JAR without issue.
    options.release.set(21)
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
    archiveBaseName.set("BlazeMCLogin-Paper-26.x")
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

listOf("26.2.build.135-stable", "26.3.build.262-stable").forEach { api ->
    val safe = api.replace(".", "_").replace("-", "_")
    val cfg = configurations.create("api_$safe") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies.add(cfg.name, "io.papermc.paper:paper-api:$api")
    tasks.register<JavaCompile>("compileAgainst_$safe") {
        group = "verification"
        description = "Compile the Paper adapter against $api"
        classpath = cfg + configurations.getByName("compileClasspath")
        source = sourceSets.main.get().java
        destinationDirectory.set(layout.buildDirectory.dir("compat-$safe"))
        options.release.set(21)
        options.encoding = "UTF-8"
        javaCompiler.set(javaToolchains.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        })
    }
}
