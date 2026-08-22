buildscript {
    configurations.classpath {
        // Android Gradle Plugin ile Spring Boot eklentisi aynı buildscript classpath'ini paylaşıyor ve
        // AGP eski bir commons-compress getiriyor; bu sürümde `bootJar`ın çağırdığı
        // ZipArchiveOutputStream.putArchiveEntry(ZipArchiveEntry) aşırı yüklemesi yok ve jar üretimi
        // NoSuchMethodError ile düşüyor. Sürümü sabitlemek iki eklentiyi bir arada tutmanın en dar çözümü.
        resolutionStrategy.force("org.apache.commons:commons-compress:1.28.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.ktlint)
}

subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        android.set(true)
        ignoreFailures.set(false)
        filter {
            exclude { it.file.path.contains("/build/") }
        }
    }
}
