plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Saf Kotlin/JVM modülü. Android SDK bağımlılığı EKLENEMEZ (kotlin-jvm plugin bunu fiziksel olarak engeller).
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
