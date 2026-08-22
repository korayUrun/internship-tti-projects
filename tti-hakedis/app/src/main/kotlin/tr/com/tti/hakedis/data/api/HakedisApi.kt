package tr.com.tti.hakedis.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import tr.com.tti.hakedis.data.Settings
import tr.com.tti.hakedis.domain.document.DocumentStatus
import tr.com.tti.hakedis.domain.document.DocumentType
import tr.com.tti.hakedis.domain.document.DocumentVariant
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Sunucu hatası. Gövde her uçta aynı biçimdedir (`HataYakalayici`): `{ hata, mesaj }`.
 *
 * Mesaj **doğrudan kullanıcıya gösterilir**: sunucu metinleri Türkçe ve eyleme dönüktür
 * ("önce 2. işin hakedişini tamamlayın"). Mobil bunları yeniden yazmaz — aynı kuralı iki yerde
 * yaşatmak olurdu (web de aynısını yapıyor).
 */
class ApiException(val status: Int, val code: String, message: String) : Exception(message)

/** Sunucuya hiç ulaşılamadı: VPN kapalı, adres yanlış ya da sunucu ayakta değil. */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** İndirilebilir çıktı biçimleri; uç yolu ve uzantı tek yerde. */
enum class OutputFormat(val path: String, val extension: String) {
    PDF("pdf", "pdf"),
    EXCEL("excel", "xlsx"),
}

/**
 * Sunucu API'sinin tek erişim noktası (Faz J).
 *
 * Retrofit kullanılmıyor: bir düzine uç için arayüz üretimi ve ikinci bir dönüştürücü katmanı
 * CLAUDE.md §1/7'ye aykırı. OkHttp'nin kendisi çerez saklama, multipart ve zaman aşımı sağlıyor.
 *
 * **Oturum ve CSRF:** sunucu tarayıcı kalıbını kullanıyor — HTTP oturum çerezi + `XSRF-TOKEN`
 * çerezinden okunup `X-XSRF-TOKEN` başlığında geri gönderilen token. Aynı kalıp burada da işliyor;
 * çerezler yalnızca **bellekte** tutulur: parola gibi oturum da diske yazılmaz (karar J2).
 */
class HakedisApi(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true }

    private val cookieJar = MemoryCookieJar()

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        // VPN üzerinden çalışılıyor: bağlantı yavaş olabilir, ama sonsuza kadar beklemek kullanıcıyı
        // donmuş bir ekranla baş başa bırakır. PDF üretimi en uzun süren uç.
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    // --- Oturum -------------------------------------------------------------

    suspend fun login(userName: String, password: String): UserDto {
        // Yeni giriş öncesi eski çerezler düşürülür: sunucu da aynısını yapıyor (oturum sabitleme
        // koruması), istemcide kalan bayat çerez yeni oturumu bozardı.
        cookieJar.clear()
        return decode(sendForText("/api/auth/giris", "POST", jsonBody(LoginRequest(userName, password))))
    }

    suspend fun logout() {
        runCatching { sendForText("/api/auth/cikis", "POST", EMPTY_BODY) }
        cookieJar.clear()
    }

    // --- Sabit listeler -----------------------------------------------------

    suspend fun regions(): List<RegionDto> = get("/api/bolgeler")

    suspend fun scopes(): List<ScopeDto> = get("/api/kapsamlar")

    suspend fun contracts(): List<ContractDto> = get("/api/sozlesmeler")

    /**
     * Kable araması — **bölge kısıtı yok** (Faz O): her kullanıcı her kabloyu seçebilir.
     *
     * Envanterin tamamı çekilmiyor: liste 900+ satıra çıktı ve VPN üzerinden her açılışta indirmek
     * anlamsız. Arayüz `mtc` ön dolu bir kutu gösterip kullanıcı yazdıkça arıyor; sunucu üç
     * karakterden kısa aramada boş liste döner.
     *
     * [limit] **açıkça** gönderilir: sunucunun varsayılanına güvenmek kırpmayı arayüzden görünmez
     * kılıyordu — ekran kaç sonucun kesildiğini bilemeden tam liste sanıyordu.
     */
    suspend fun cables(query: String, limit: Int): List<CableDto> = get("/api/kableler?ara=${query.encoded()}&limit=$limit")

    // --- İşler --------------------------------------------------------------

    suspend fun workOrders(status: WorkOrderStatusDto? = null): List<WorkOrderDto> =
        get("/api/isler" + if (status == null) "" else "?durum=$status")

    /**
     * İş listesinin `.xlsx` hâli — **ekrandaki süzgeçle**. Dosyayı sunucu üretir (CLAUDE.md §13);
     * cihaz yalnızca indirir ve paylaşır. Ad `Content-Disposition`'dan gelir, istemcide türetilmez.
     */
    suspend fun downloadWorkOrderList(status: WorkOrderStatusDto?, targetDir: File): File {
        val response = send("/api/isler/liste.xlsx" + if (status == null) "" else "?durum=$status", "GET", null)
        return download(response, File(targetDir, fileNameOf(response) ?: "isler.xlsx"))
    }

    suspend fun workOrder(id: Long): WorkOrderDto = get("/api/isler/$id")

    suspend fun createWorkOrder(body: WorkOrderRequest): WorkOrderDto = decode(sendForText("/api/isler", "POST", jsonBody(body)))

    // Durum değiştirme (`POST /api/isler/{id}/durum`) ve belge onaylama (`.../onayla`) **yalnızca
    // web'dedir** (CLAUDE.md §9): mobil bölge sorumlusunun ekranıdır. Uçlar zaten yönetici yetkisi
    // istiyor ve her geçişte gerekçe zorunlu — mobilde karşılığı olmayan bir akıştı.

    // --- Belgeler -----------------------------------------------------------

    /**
     * Belge arşivi. Süzgeçler **sunucuda** uygulanır (SQL `where`; yetki süzgeci de orada, §7/3).
     *
     * ⚠️ Boş süzgeç sorgu dizesine **hiç eklenmez**: `?tur=` göndermek sunucuda tip dönüşüm hatası
     * verir. Web tarafı da aynı kuralı uyguluyor.
     */
    suspend fun documents(tur: DocumentType? = null, durum: DocumentStatus? = null): List<DocumentListDto> {
        val parametreler = buildList {
            if (tur != null) add("tur=$tur")
            if (durum != null) add("durum=$durum")
        }
        return get("/api/belgeler" + if (parametreler.isEmpty()) "" else "?" + parametreler.joinToString("&"))
    }

    suspend fun document(id: Long): DocumentDto = get("/api/belgeler/$id")

    /**
     * Belgenin poz listesi — fiyatlar **belgenin çivilendiği sürümden** gelir.
     *
     * Sözleşme de buradan türer: belge kendi sözleşmesini seçtiği için (Faz N) poz listesi işin
     * değil, belgenin çivilendiği sürümün sözleşmesinden gelir.
     */
    suspend fun documentItems(documentId: Long): List<PozDto> = get("/api/belgeler/$documentId/pozlar")

    suspend fun createDocument(workOrderId: Long, type: DocumentType, contractCode: String): DocumentDto =
        decode(sendForText("/api/isler/$workOrderId/belgeler", "POST", jsonBody(DocumentRequest(type, contractCode))))

    /** Sözleşmeyi değiştirir — **girilmiş miktarlar silinir**; poz kimlikleri sözleşmeye özeldir. */
    suspend fun changeContract(documentId: Long, contractCode: String): DocumentDto =
        decode(sendForText("/api/belgeler/$documentId/sozlesme", "PUT", jsonBody(ContractRequest(contractCode))))

    /**
     * Miktar girişi — **tam değiştirme**: gönderilmeyen poz belgeden silinir. "Girilmemiş" ile
     * "0 girilmiş" ayrımı satırın varlığıyla korunur (CLAUDE.md §5).
     */
    suspend fun writeRows(documentId: Long, rows: List<RowEntry>): DocumentDto =
        decode(sendForText("/api/belgeler/$documentId/satirlar", "PUT", jsonBody(RowsRequest(rows))))

    suspend fun submit(documentId: Long): DocumentDto = decode(sendForText("/api/belgeler/$documentId/gonder", "POST", EMPTY_BODY))

    // --- Miktar Excel'i (Faz P) ---------------------------------------------

    /** Şablon **girilmiş miktarlarla dolu** iner: kullanıcı yalnızca değişecekleri düzeltir. */
    suspend fun downloadQuantityTemplate(documentId: Long, targetDir: File): File {
        val response = send("/api/belgeler/$documentId/miktar-sablonu.xlsx", "GET", null)
        val name = fileNameOf(response) ?: "miktar-sablonu-$documentId.xlsx"
        return download(response, File(targetDir, name))
    }

    /** Doldurulmuş şablon — **tam değiştirme**. Tek sorunlu satır varsa hiçbir şey yazılmaz (400). */
    suspend fun uploadQuantities(documentId: Long, file: File, fileName: String): DocumentDto {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("dosya", fileName, file.asRequestBody(BINARY_TYPE))
            .build()
        return decode(sendForText("/api/belgeler/$documentId/miktar-yukleme", "POST", body))
    }

    // --- Ekler --------------------------------------------------------------

    suspend fun attachments(documentId: Long): List<AttachmentDto> = get("/api/belgeler/$documentId/ekler")

    suspend fun uploadAttachment(documentId: Long, type: AttachmentType, file: File, fileName: String): AttachmentDto {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("tur", type.name)
            .addFormDataPart("dosya", fileName, file.asRequestBody(BINARY_TYPE))
            .build()
        return decode(sendForText("/api/belgeler/$documentId/ekler", "POST", body))
    }

    suspend fun deleteAttachment(attachmentId: Long) {
        sendForText("/api/ekler/$attachmentId", "DELETE", EMPTY_BODY)
    }

    // --- EK3 malzemeleri (Faz Y) --------------------------------------------

    /**
     * Okuma her belge türünde serbesttir; hakediş dışında sunucu **boş liste** döner. Gözlem modunda
     * da çağrılır — tamamlanmış bir hakedişin malzemelerini görmek gözlemin amacının kendisi.
     */
    suspend fun materials(documentId: Long): List<MaterialDto> = get("/api/belgeler/$documentId/malzemeler")

    /**
     * Listeyi **tamamen değiştirir** ([writeRows] ile aynı semantik): satır satır ekleme/silme ucu
     * yok, form zaten listenin tamamını ekranda tutuyor.
     *
     * Sunucu tamamen boş satırları eler ve temizlenmiş listeyi döner; çağıran ekranı **dönen liste
     * ile** tazelemeli ki kullanıcı neyin kaydedildiğini görsün. Hakediş dışı türde 409.
     */
    suspend fun writeMaterials(documentId: Long, materials: List<MaterialEntry>): List<MaterialDto> =
        decode(sendForText("/api/belgeler/$documentId/malzemeler", "PUT", jsonBody(MaterialsRequest(materials))))

    // --- Çıktı --------------------------------------------------------------

    /**
     * PDF/Excel **sunucuda** üretilir, cihaz yalnızca indirir (CLAUDE.md §13).
     *
     * Dosya adı sunucunun `Content-Disposition` başlığından alınır: belgenin kimliği odur
     * (`TTI_IZMIR_2026_KESIF_001_TEKLIF.pdf`) ve istemcide yeniden türetilmez.
     */
    suspend fun downloadOutput(documentId: Long, format: OutputFormat, variant: DocumentVariant, targetDir: File): File {
        val response = send("/api/belgeler/$documentId/${format.path}?varyant=$variant", "GET", null)
        val name = fileNameOf(response) ?: "belge-$documentId.${format.extension}"
        return download(response, File(targetDir, name))
    }

    // --- Alt yapı -----------------------------------------------------------

    private suspend inline fun <reified T> get(path: String): T = decode(sendForText(path, "GET", null))

    /** Sorgu parametresi kaçışı: kable kimlikleri `*` ve `.` içeriyor. */
    private fun String.encoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private inline fun <reified T> jsonBody(body: T): RequestBody = json.encodeToString(body).toRequestBody(JSON_TYPE)

    /**
     * Okunmuş yanıt: durum kodu + gövde metni.
     *
     * ⚠️ **Gövde `Response` olarak taşınmaz.** `execute()` yalnızca **başlıkları** getirir; gövde
     * akıştan okunur ve `body.string()` gerçek bir soket okuması yapar. `Response`'u geri döndürüp
     * gövdeyi çağıranın iş parçacığında okumak, ana iş parçacığında `NetworkOnMainThreadException`
     * demektir — üstelik o istisnanın **mesajı `null`** olduğu için ekranda boş bir hata kutusu
     * olarak görünür. Küçük yanıtlarda hata çıkmaz (gövde ilk pakette gelip tamponda bulunur), bu
     * yüzden sorun ancak liste büyüyünce ortaya çıkar. Okuma bu yüzden istekle **aynı** IO bloğunda
     * tamamlanır.
     */
    private data class TextResponse(val code: Int, val url: HttpUrl, val text: String)

    /**
     * Tek çıkış kapısı: CSRF token'ını hazırlar, isteği kurar ve çağırır.
     *
     * Sunucuda CSRF **giriş ucu dahil** açık (`GuvenlikYapilandirmasi` hiçbir yolu muaf tutmuyor).
     * Token `XSRF-TOKEN` çerezinden okunup `X-XSRF-TOKEN` başlığında geri gönderilir; çerez ise
     * sunucudan geçen **ilk istekte** yazılır. Uygulama ilk açıldığında elde çerez olmadığı için,
     * yazan bir istek atmadan önce kimlik istemeyen bir uca (`/api/saglik`) GET yapılıp token
     * alınır — aksi hâlde ilk giriş denemesi 403 ile düşerdi. Web de aynı yolu izliyor (`api.ts`).
     */
    private suspend fun send(path: String, method: String, body: RequestBody?): Response {
        if (method != "GET" && cookieJar.value(CSRF_COOKIE) == null) {
            // Token isteğinin gövdesi kullanılmıyor ama yine de kapatılmalı; kapatma da soket
            // okuyabildiği için IO'da yapılır.
            runCatching { withContext(Dispatchers.IO) { call(request("/api/saglik", "GET", null)).close() } }
        }
        return call(request(path, method, body))
    }

    /** İstek ve **gövdenin okunması** tek bir IO bloğunda tamamlanır (bkz. [TextResponse]). */
    private suspend fun sendForText(path: String, method: String, body: RequestBody?): TextResponse {
        val response = send(path, method, body)
        return withContext(Dispatchers.IO) {
            response.use { TextResponse(it.code, it.request.url, it.body.string()) }
        }
    }

    private fun request(path: String, method: String, body: RequestBody?): Request {
        val base = settings.serverAddress().trimEnd('/')
        val url = (base + path).toHttpUrlOrNull()
            ?: throw NetworkException("Sunucu adresi geçersiz: $base")

        val builder = Request.Builder().url(url).method(method, body)
        if (method != "GET") {
            cookieJar.value(CSRF_COOKIE)?.let { builder.header(CSRF_HEADER, it) }
        }
        return builder.build()
    }

    private suspend fun call(request: Request): Response = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            Log.e(TAG, "Sunucuya ulaşılamadı: ${request.url}", error)
            throw NetworkException("Sunucuya ulaşılamadı. VPN bağlantınızı ve sunucu adresini kontrol edin.", error)
        }

        if (response.isSuccessful) return@withContext response

        // Hata gövdesi JSON değilse (ör. vekil hatası) ham metin gösterilir — sessizce yutulmaz.
        val text = response.use { it.body.string() }
        val error = runCatching { json.decodeFromString<ErrorDto>(text) }.getOrNull()
        val apiError = ApiException(
            status = response.code,
            code = error?.hata?.ifBlank { null } ?: "BILINMEYEN",
            message = error?.mesaj?.ifBlank { null } ?: text.ifBlank { "Sunucu ${response.code} döndü." },
        )
        Log.e(TAG, "İstek reddedildi (${response.code}): ${request.url} — ${apiError.message}")
        throw apiError
    }

    /**
     * Yanıtı çözer.
     *
     * Çözümleme hatası **yutulmaz**: sunucu bir alanı yeniden adlandırdığında ya da mobil DTO'su
     * geride kaldığında kullanıcı boş bir hata kutusu görür ve neyin bozulduğu hiçbir yerde
     * yazmazdı. Ayrıntı `Log.e` ile kaydedilir, kullanıcıya ne yapması gerektiğini söyleyen bir
     * mesaj gider (CLAUDE.md: fail loud).
     */
    private inline fun <reified T> decode(response: TextResponse): T = try {
        json.decodeFromString<T>(response.text)
    } catch (error: SerializationException) {
        Log.e(TAG, "Yanıt çözümlenemedi: ${response.url} — ${response.text.take(LOG_GOVDE_SINIRI)}", error)
        throw ApiException(
            status = response.code,
            code = "COZUMLEME_HATASI",
            message = "Sunucu yanıtı beklenen biçimde değil. Uygulama ile sunucu sürümleri uyuşmuyor olabilir.",
        )
    }

    /** Akışın kapatılması da IO'da: `close()` gövdenin kalanını atlarken soket okuyabiliyor. */
    private suspend fun download(response: Response, target: File): File = withContext(Dispatchers.IO) {
        response.use {
            target.parentFile?.mkdirs()
            it.body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        target
    }

    private fun fileNameOf(response: Response): String? =
        response.header("Content-Disposition")?.let { FILE_NAME_PATTERN.find(it)?.groupValues?.get(1) }

    /**
     * Çerezler yalnızca bellekte. Uygulama kapandığında oturum düşer ve parola yeniden istenir —
     * cihazda oturum saklamamak karar J2'nin doğal sonucu.
     */
    private class MemoryCookieJar : CookieJar {
        private val cookies = mutableMapOf<String, Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { this.cookies[it.name] = it }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.toList()

        @Synchronized
        fun value(name: String): String? = cookies[name]?.value

        @Synchronized
        fun clear() = cookies.clear()
    }

    companion object {
        private const val TAG = "HakedisApi"
        private const val CONNECT_TIMEOUT_SEC = 15L
        private const val READ_TIMEOUT_SEC = 60L
        private const val CSRF_COOKIE = "XSRF-TOKEN"
        private const val CSRF_HEADER = "X-XSRF-TOKEN"

        /** Log satırı kilometrelerce uzamasın; teşhis için yanıtın başı yeter. */
        private const val LOG_GOVDE_SINIRI = 500

        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        val BINARY_TYPE = "application/octet-stream".toMediaType()
        val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)
        val FILE_NAME_PATTERN = Regex("""filename="?([^";]+)"?""")
    }
}
