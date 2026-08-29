import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "mterm"

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
