plugins {
    java
    id("com.gradleup.shadow") version "8.3.6" apply false
}

val pluginGroup = "net.blazemc"
val pluginVersion = providers.gradleProperty("pluginVersion").getOrElse("1.0.0")

allprojects {
    group = pluginGroup
    version = pluginVersion
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
        options.isFork = true
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
        maxHeapSize = "1g"
    }
}

tasks.register("copyBuildOutput") {
    group = "distribution"
    description = "Copies compiled plugin JARs into build-output/"
    dependsOn(
        ":paper-legacy:shadowJar",
        ":paper-1.21:shadowJar",
        ":paper-26:shadowJar",
        ":velocity-bridge:shadowJar"
    )
    doLast {
        val out = rootProject.layout.projectDirectory.dir("build-output").asFile
        out.mkdirs()
        copyJar("paper-legacy", "BlazeMCLogin-Paper-Legacy.jar", out)
        copyJar("paper-1.21", "BlazeMCLogin-Paper-1.21.x.jar", out)
        copyJar("paper-26", "BlazeMCLogin-Paper-26.x.jar", out)
        copyJar("velocity-bridge", "BlazeMCLogin-Velocity-Bridge.jar", out)
    }
}

fun copyJar(projectName: String, fileName: String, out: File) {
    val libs = project(projectName).layout.buildDirectory.dir("libs").get().asFile
    val version = pluginVersion
    val candidates = libs.listFiles()?.filter {
        it.name.endsWith(".jar") && !it.name.contains("plain") && !it.name.endsWith("-sources.jar")
    }.orEmpty()
    val shadow = candidates.firstOrNull { it.name.contains(version) }
        ?: candidates.maxByOrNull { it.lastModified() }
        ?: error("No shadow JAR found for $projectName in $libs")
    val dest = File(out, fileName)
    shadow.copyTo(dest, overwrite = true)
    println("Copied ${shadow.name} -> ${dest.path} (${dest.length()} bytes)")
}

tasks.register("printCompatibilityMatrix") {
    group = "help"
    doLast {
        println(
            """
            BlazeMCLogin compatibility matrix (verified artifacts only)
            ----------------------------------------------------------
            Paper 1.20.4 API  -> paper-legacy  (Java 17 bytecode)
            Paper 1.21.11 API -> paper-1.21    (Java 21 bytecode)
            Paper 26.1.2 API  -> paper-26      (Java 25 bytecode; also for 26.2 / 26.3)
            Velocity 3.4 API  -> velocity-bridge (Java 21 bytecode)
            """.trimIndent()
        )
    }
}
