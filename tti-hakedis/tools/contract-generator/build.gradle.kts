plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Sözleşme `.xlsx` **parse kütüphanesi**. Apache POI burada kalır, APK'ya girmez (CLAUDE.md §4).
//
// Bu modülün bir CLI'ı YOKTU olmadı — vardı ve kaldırıldı: `Main.kt` sözleşmeleri
// `app/src/main/assets/contracts` altına JSON olarak üretiyordu, ama Faz J'de assets kalktı ve
// uygulama sözleşmeleri API'den alıyor. Çıktı klasörü diskte bile yoktu. Bugün tek tüketici
// `:server` (`SozlesmeYuklemeServisi`, `MiktarOkuyucu`, `XlsxOkuyucu`, `HataYakalayici`):
// yönetici sözleşmeyi web'den yüklüyor ve dosya sunucuda okunuyor.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.poi.ooxml)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
