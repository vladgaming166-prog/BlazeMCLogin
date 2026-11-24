rootProject.name = "BlazeMCLogin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

include("core")
include("paper-legacy")
include("paper-1.21")
include("paper-26")
include("velocity-bridge")
