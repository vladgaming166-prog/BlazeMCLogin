plugins {
    java
    id("com.gradleup.shadow")
}

val velocityApi = "3.4.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:$velocityApi")
    annotationProcessor("com.velocitypowered:velocity-api:$velocityApi")
    compileOnly("net.kyori:adventure-api:4.25.0")
    implementation(project(":core"))
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("BlazeMCLogin-Velocity-Bridge")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    mergeServiceFiles()
    relocate("org.yaml.snakeyaml", "net.blazemc.login.libs.snakeyaml")
    dependencies {
        exclude(dependency("com.zaxxer:HikariCP:.*"))
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        exclude(dependency("com.mysql:mysql-connector-j:.*"))
        exclude(dependency("com.password4j:password4j:.*"))
    }
    exclude("META-INF/maven/**")
    exclude("META-INF/INDEX.LIST")
}

tasks.named("build") { dependsOn(tasks.shadowJar) }
tasks.named("assemble") { dependsOn(tasks.shadowJar) }
