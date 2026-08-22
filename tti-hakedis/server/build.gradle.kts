plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // İş mantığının tek kaynağı. Para hesabı, yuvarlama, poz sıralama ve çıktı modeli burada yaşar;
    // sunucu da mobil de aynı sınıfları çağırır (CLAUDE.md: iş mantığı iki kez yazılamaz).
    implementation(project(":core:domain"))

    // Sözleşme xlsx okuma. `ContractXlsxParser` saf bir fonksiyon (IO/println yok); yöneticinin fiyat
    // listesi yüklemesi aynı parse mantığını yeniden kullanır, ikinci kez yazılmaz.
    // `:app` `:tools`'a bağımlı DEĞİL — POI bu yolla APK'ya girmez (CLAUDE.md §6: POI `:server`'da serbest).
    implementation(project(":tools:contract-generator"))

    // Kable envanteri xlsx okuma + sürüm/izdüşüm yazma (Faz U). Yöneticinin web'den yüklediği dosya
    // ile CLI'ın (`:tools:kable-import:run`) yüklediği dosya **aynı** koddan geçer; iki yazma yolu
    // olsaydı `kable` tablosu sürümlerden sessizce ayrışabilirdi.
    implementation(project(":tools:kable-import"))
    implementation(libs.poi.ooxml)

    // Fiyat giriş şablonunun ve belge Excel'inin (.xlsx) üretimi.
    implementation(libs.fastexcel)

    // PDF üretimi. LGPL 2.1 — iText 5/7 AGPL olduğu için kurumsal kullanımda uygun değil
    // (CLAUDE.md §6 lisans notu). `PdfTemplate`in ürettiği XHTML'i PDFBox ile bastırır.
    implementation(libs.openhtmltopdf.core)
    implementation(libs.openhtmltopdf.pdfbox)

    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.flyway)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.truth)
}

// Spring Boot 3+ JUnit 4'ü desteklemiyor: :server JUnit 5 kullanır, :core:domain ve :app JUnit 4'te kalır.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// --- Web arayüzü (Faz H) ---------------------------------------------------------------------
//
// `web/` (Vite + React + TS) derlenir ve çıktısı jar'ın `static/` klasörüne gömülür: tek servis,
// tek port, CORS yok, nginx yok.
//
// Bağ **yalnızca `bootJar`'a** kurulur, `processResources`'a değil. İkincisi `test` ve `ktlintCheck`
// dahil her derlemeyi npm'e bağımlı kılardı; oysa web yalnızca dağıtılacak jar üretilirken gerekli.
//
// Node/npm bulunmayan bir makinede jar üretmek gerekirse `-PwebAtla` ile bu adım atlanır — çıkan
// jar'da API çalışır, arayüz **bulunmaz** (bilerek gürültülü bir uyarı basılır).
val webKok = rootProject.layout.projectDirectory.dir("web")
val webCiktisi = webKok.dir("dist")
val npm = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "npm.cmd" else "npm"

val webBagimliliklari by tasks.registering(Exec::class) {
    description = "web/ bağımlılıklarını kurar (npm ci)"
    workingDir = webKok.asFile
    commandLine(npm, "ci", "--no-audit", "--no-fund")
    inputs.files(webKok.file("package.json"), webKok.file("package-lock.json"))
    // npm bu dosyayı kurulum sonunda yazar; işaretçi olarak kullanmak, `node_modules` altındaki on
    // binlerce dosyayı her derlemede taramaktan kurtarır.
    outputs.file(webKok.file("node_modules/.package-lock.json"))
}

val webDerle by tasks.registering(Exec::class) {
    description = "web/ arayüzünü derler (tsc + vite build)"
    dependsOn(webBagimliliklari)
    workingDir = webKok.asFile
    commandLine(npm, "run", "build")
    inputs.dir(webKok.dir("src"))
    inputs.files(
        webKok.file("index.html"),
        webKok.file("package.json"),
        webKok.file("tsconfig.json"),
        webKok.file("vite.config.ts"),
    )
    outputs.dir(webCiktisi)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("tti-hakedis-server.jar")

    if (project.hasProperty("webAtla")) {
        doFirst { logger.warn("UYARI: -PwebAtla verildi — jar'a web arayüzü GÖMÜLMEDİ, yalnızca API çalışır.") }
    } else {
        dependsOn(webDerle)
        from(webCiktisi) { into("BOOT-INF/classes/static") }
    }
}
