plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Saf JVM aracı: Apache POI burada kalır, APK'ya girmez.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.poi.ooxml)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

application {
    mainClass.set("tr.com.tti.hakedis.tools.kable.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    // 1: kable envanteri .xlsx yolu. Farklı dosya için: --args="baska/dosya.xlsx"
    args = listOf("tools/kable/cable_data_28072026.xlsx")

    // Veritabanı bilgileri ortam değişkeninden ya da -P özelliğinden gelir; parola build dosyasında durmaz.
    //   ./gradlew :tools:kable-import:run -PDB_PAROLA=...
    listOf("DB_URL", "DB_KULLANICI", "DB_PAROLA").forEach { ad ->
        providers.gradleProperty(ad).orElse(providers.environmentVariable(ad)).orNull
            ?.let { environment(ad, it) }
    }
}
