pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // JDK 17 toolchain'i cihazda yoksa otomatik indirilsin. Android Studio kendi JDK'sı (21) ile
    // sync ettiğinde bu olmadan "Undefined Toolchain Download Repositories" hatası veriyor.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tti-hakedis"

// Her çalışma zamanı hedefi bir modül: :server (JVM sunucu), :core:domain (ikisinin paylaştığı saf JVM iş
// mantığı), :tools:* (build zamanı araçları). Bunlar Android SDK'sı olmadan da derlenir.
include(":core:domain")
include(":server")
include(":tools:contract-generator")
include(":tools:kable-import")

// :app yalnızca Android SDK'sı bulunan makinelerde build'e girer. Sunucu tarafı mobile bağımlı değildir:
// APK, Android Studio kurulu bir geliştirici makinesinde derlenip elle dağıtılır. SDK'sız bir ortamda
// (sunucu, CI) bu modül hiç okunmaz, böylece `./gradlew :server:bootJar` Android eklentisine ihtiyaç duymaz.
val androidSdkAvailable =
    file("local.properties").exists() || !System.getenv("ANDROID_HOME").isNullOrBlank()
if (androidSdkAvailable) {
    include(":app")
} else {
    println("Android SDK bulunamadı (local.properties / ANDROID_HOME) — :app modülü atlandı.")
}
