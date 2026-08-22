import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release imzalama bilgileri — **depo dışında**: `~/.tti-keys/tti-hakedis-keystore.properties`
 * (keystore da orada). `.gitignore` `*.jks`'i zaten hariç tutuyor ama depo klasörü sunucuya
 * **zip'lenerek** gidiyor (ServerDeploy.md) ve `zip -r` klasördeki her şeyi alır; özel anahtarı
 * depo dışında tutmak bu sızıntı yolunu tamamen kapatıyor.
 *
 * Dosya yoksa `null` döner ve release **imzasız** üretilir — hata verilmez. Böylece anahtarı olmayan
 * bir makine (CI, devralan geliştirici) `assembleRelease`'i yine de derleyebiliyor; imzasız APK
 * tablete kurulamaz ama derlemenin kırılması hiçbir şeyi kolaylaştırmazdı.
 *
 * ⚠️ Keystore **yedeklenmeli**. Android, kurulu bir uygulamanın üzerine güncellemeyi ancak **aynı
 * anahtarla** imzalanmışsa kabul eder; anahtar kaybolursa yeni sürüm tabletlere ancak önce
 * kaldırılarak kurulur.
 */
val imzaAyarlari: Properties? = File(System.getProperty("user.home"), ".tti-keys/tti-hakedis-keystore.properties")
    .takeIf { it.isFile }
    ?.let { dosya -> Properties().apply { dosya.inputStream().use { load(it) } } }

android {
    namespace = "tr.com.tti.hakedis"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "tr.com.tti.hakedis"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Yalnızca anahtar varsa tanımlanır; yoksa aşağıdaki `signingConfig` ataması da yapılmaz.
        imzaAyarlari?.let { ayar ->
            create("release") {
                storeFile = file(ayar.getProperty("storeFile"))
                storePassword = ayar.getProperty("storePassword")
                keyAlias = ayar.getProperty("keyAlias")
                keyPassword = ayar.getProperty("keyPassword")
                // İmza şeması AGP varsayılanına bırakıldı: minSdk 26'da **v2 yeterli** (APK
                // Signature Scheme v2 Android 7.0/API 24'ten beri var) ve AGP minSdk ≥ 24 iken v1'i
                // zaten atlıyor — `enableV1Signing = true` yazmak APK'yı değiştirmiyor, yalnızca
                // yanlış bir izlenim bırakıyordu (`apksigner verify -v` ile doğrulandı: v1 false,
                // v2 true, "Verifies").
            }
        }
    }

    buildTypes {
        release {
            // Faz J: küçültme açıldı. kotlinx.serialization ve OkHttp keep kuralları
            // `proguard-rules.pro` içinde; ikisi de sınıflara isimle ulaştığı için kural olmadan
            // hata derlemede değil, cihazda ilk API çağrısında çıkardı.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") { kotlin.srcDir("src/main/kotlin") }
        getByName("test") { kotlin.srcDir("src/test/kotlin") }
        getByName("androidTest") { kotlin.srcDir("src/androidTest/kotlin") }
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.truth)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
