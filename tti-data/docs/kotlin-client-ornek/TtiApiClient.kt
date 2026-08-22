// Ortak TTI Veri API Client'ı — 5 uygulamanın hepsinde aynı şekilde kullanılabilir.
// Gradle bağımlılıkları (app/build.gradle.kts):
//   implementation("com.squareup.retrofit2:retrofit:2.11.0")
//   implementation("com.squareup.retrofit2:converter-gson:2.11.0")
//   implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

package com.tti.dataclient

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// ---- API sözleşmesi ----

interface TtiDataApi {
    // Dönen JSON, dataset'e göre değişir — Map<String, Any?> ile esnek tutuyoruz.
    // İhtiyaç duyulursa her dataset için ayrı, tipli bir data class da yazılabilir
    // (bkz. aşağıdaki örnek: CableRecord).
    @GET("/api/datasets/{name}/data")
    suspend fun getDatasetData(@Path("name") datasetName: String): List<Map<String, Any?>>
}

// ---- API key'i her isteğe otomatik ekleyen interceptor ----

class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("X-API-Key", apiKey)
            .build()
        return chain.proceed(request)
    }
}

// ---- Kurulum ----

object TtiApiClientFactory {
    // Sunucunun IP'si DEĞİL, ALAN ADI. Uygulama ters vekile (Caddy) 443'ten bağlanır;
    // 8083 portu yalnızca sunucunun kendi 127.0.0.1'inde dinler ve güvenlik duvarında
    // AÇIK DEĞİLDİR. İsim→IP çözümlemesi kurumsal DNS'in işidir (IT bir A kaydı ekler).
    //
    // ⚠️ TABLETLERDE SERTİFİKA İŞİ YOKTUR. Sunucunun sertifikası genel bir CA'dan gelir
    // (DigiCert / RapidSSL TLS RSA CA G1); kökü Android'in sistem deposunda minSdk 26'dan
    // çok önce vardır. Yani:
    //   · network_security_config.xml'e <certificates src="user"/> EKLEMEYİN — kullanıcı
    //     kurulumlu CA'lara güvenmek Android 7'den beri kasıtlı olarak kapalı bir kapıdır.
    //   · Pinning YAPMAYIN (sertifika 2027-02-27'de yenilenecek, pin uygulamayı kırar).
    //   · cleartextTrafficPermitted="false" kalsın/gelsin — HTTPS'e geçiş, cleartext
    //     istisnalarının SİLİNMESİ demektir, eklenmesi değil.
    //
    // Bağlanamama durumunda ilk bakılacak yer sunucudur: certs/fullchain.pem ARA
    // SERTİFİKAYI içermeli. Tarayıcılar eksik ara sertifikayı bazen tolere eder,
    // Android ETMEZ ve el sıkışma tamamen düşer.
    //   grep -c 'BEGIN CERTIFICATE' certs/fullchain.pem   # ≥ 2 olmalı
    //
    // ⚠️ Wildcard sertifika TEK etiket kapsar:
    //   data.turktelekomint.com      ✓
    //   veri.data.turktelekomint.com ✗
    private const val BASE_URL = "https://data.turktelekomint.com/"

    fun create(apiKey: String): TtiDataApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(apiKey))
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TtiDataApi::class.java)
    }
}

// ---- Kullanım örneği (örn. cable_data çeken bir uygulamada) ----
//
// val api = TtiApiClientFactory.create(apiKey = "BU_UYGULAMAYA_OZEL_KEY")
//
// viewModelScope.launch {
//     try {
//         val cables = api.getDatasetData("cable_data")
//         // cables[0]["nosid"], cables[0]["capacity"] gibi erişilebilir
//     } catch (e: Exception) {
//         // ağ hatası / yetkisiz erişim (401/403) burada yakalanır
//     }
// }

// ---- İstenirse tipli (type-safe) alternatif: her dataset için ayrı data class ----

data class CableRecord(
    val idnumber: String?,
    val nosid: String?,
    val countryA: String?,
    val cityA: String?,
    val addressA: String?,
    val capacity: Double?,
    // ... diğer alanlar aynı şekilde eklenir (bkz. backend'deki schema_definition)
)

interface TtiCableApi {
    @GET("/api/datasets/cable_data/data")
    suspend fun getCables(): List<CableRecord>
}
