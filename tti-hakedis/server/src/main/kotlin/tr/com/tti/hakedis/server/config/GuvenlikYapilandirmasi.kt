package tr.com.tti.hakedis.server.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler

/**
 * Kimlik doğrulama: yerel `kullanici` tablosu + BCrypt + **sunucu tarafı oturum** (cookie).
 *
 * JWT kullanılmıyor: sekiz kullanıcı ve tek servis için token yenileme/iptal karmaşası karşılığında
 * hiçbir şey kazandırmaz. Oturum cookie'si `SameSite=Strict` (application.yml) ve CSRF token'ı ile
 * korunur; ikisi birlikte tarayıcı tarafındaki siteler arası istek sahteciliğini kapatır.
 */
@Configuration
@EnableWebSecurity
class GuvenlikYapilandirmasi {
    @Bean
    fun sifreKodlayici(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun kimlikYoneticisi(yapilandirma: AuthenticationConfiguration): AuthenticationManager = yapilandirma.authenticationManager

    /** Oturum bağlamı HTTP oturumunda saklanır; giriş ucu bağlamı buraya kendisi yazar. */
    @Bean
    fun guvenlikBaglamDeposu(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun filtreZinciri(http: HttpSecurity, baglamDeposu: SecurityContextRepository): SecurityFilterChain {
        // Varsayılan gecikmeli token yüklemesi kapatılıyor: XSRF-TOKEN cookie'si ilk GET isteğinde
        // yazılsın ki istemci girişten önce token'ı edinebilsin.
        val csrfIsleyici = CsrfTokenRequestAttributeHandler().apply { setCsrfRequestAttributeName(null) }

        return http
            .csrf {
                it.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfIsleyici)
            }
            .securityContext { it.securityContextRepository(baglamDeposu) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/saglik", "/api/auth/giris").permitAll()
                    // Web arayüzünün statik dosyaları. Kimlik istenirse tarayıcı giriş formunu hiç
                    // çizemez: `index.html` 401 JSON'una dönüşür ve kullanıcı boş sayfa görür.
                    // Bu dosyalar veri taşımaz; korunması gereken her şey `/api` altındadır.
                    .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.ico")
                    .permitAll()
                    // Durum müdahalesi yönetici işidir: proje durumu, belge işleme alma (onay), serbest
                    // geçiş ve iptal. Hepsi gerekçe ister ve `durum_kaydi`'na iz bırakır.
                    // Kural burada, tek yerde ve görünür şekilde duruyor; servis içine gömülmüyor.
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/isler/*/durum",
                        // `iade` en ileri aşamayı geri alır ve bölge sorumlusuna baştan girdirir —
                        // yıkıcı ve akış dışı bir müdahale, dolayısıyla diğerleriyle aynı yerde.
                        "/api/isler/*/iade",
                        "/api/belgeler/*/onayla",
                        "/api/belgeler/*/durum",
                        "/api/belgeler/*/iptal",
                    )
                    .hasRole("YONETICI")
                    // Sözleşme yönetimi (Faz L/N): sözleşme yükleme, format şablonu, ad/başlık/aktiflik.
                    //
                    // ⚠️ Kalıplar yol yol yazılıyor, `"/api/sozlesmeler/**"` genel kalıbı KULLANILMIYOR:
                    // o kalıp `GET /api/sozlesmeler` ve `GET /api/sozlesmeler/{kod}/pozlar` uçlarını da
                    // yakalar ve bölge sorumlusu poz listesini çekemez hâle gelir — belge ekranı boş
                    // kalır. Yazma metotlarında (PUT) tüm alt yollar yönetici işidir; orada genel kalıp
                    // güvenli, çünkü salt okunur uçların hepsi GET.
                    //
                    // ⚠️ `liste.xlsx` bu listede OLMAK ZORUNDA (Faz V'de eklendi): dosya sözleşmenin
                    // tüm birim fiyatlarını taşıyor ve kural yazılmadığı sürece bölge sorumlusu da
                    // indirebiliyordu — 403 beklenen bir uçta 200 dönüyor ve hata görünmüyordu.
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/sozlesmeler/yonetim",
                        "/api/sozlesmeler/format.xlsx",
                        "/api/sozlesmeler/*/liste.xlsx",
                    )
                    .hasRole("YONETICI")
                    // `POST /api/sozlesmeler/*/pozlar` — sözleşmeye yeni iş kalemi ekleme (Faz V).
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/sozlesmeler",
                        "/api/sozlesmeler/onizleme",
                        "/api/sozlesmeler/*/pozlar",
                    )
                    .hasRole("YONETICI")
                    .requestMatchers(HttpMethod.PUT, "/api/sozlesmeler/**")
                    .hasRole("YONETICI")
                    // `DELETE /api/sozlesmeler/{kod}` — 18 Ağustos 2026'da geri geldi (kullanıcı
                    // isteği). Faz V'de kaldırılmıştı; dönen sürüm koşullu: belgesi olan sözleşme
                    // 409 ile reddediliyor. PUT ile aynı gerekçe: yazma metotlarının tüm alt yolları
                    // yönetici işi, genel kalıp burada güvenli.
                    .requestMatchers(HttpMethod.DELETE, "/api/sozlesmeler/**")
                    .hasRole("YONETICI")
                    //
                    // Elle kablo ekleme/düzenleme (Faz V) — okuma uçları (`GET /api/kableler`)
                    // herkese açık kalır, yazma yalnızca yöneticide.
                    .requestMatchers(HttpMethod.POST, "/api/kableler")
                    .hasRole("YONETICI")
                    .requestMatchers(HttpMethod.PUT, "/api/kableler/**")
                    .hasRole("YONETICI")
                    // Kablo envanteri sürümleri (Faz U): envanteri yönetici yükler ve yürürlüğe alır.
                    // Tüm alt yollar yönetici işidir — burada genel kalıp güvenli, çünkü envanteri
                    // **okuyan** uçlar `/api/kableler` altında ve onlara dokunulmuyor.
                    .requestMatchers("/api/kable-surumleri/**", "/api/kable-surumleri")
                    .hasRole("YONETICI")
                    // Kullanıcı yönetimi (Faz I): parola sıfırlama ve hesap kapatma yönetici işidir.
                    // Liste de dahil: kimlerin sistemde olduğu ve hangi bölgeye bağlı olduğu, bölge
                    // sorumlusunun görmesi gereken bir bilgi değil.
                    .requestMatchers("/api/kullanicilar/**", "/api/kullanicilar")
                    .hasRole("YONETICI")
                    .anyRequest().authenticated()
            }
            .exceptionHandling {
                // Tarayıcıyı giriş sayfasına yönlendirmek yerine API'ye yakışan JSON 401/403 döndürülür.
                it.authenticationEntryPoint { _, yanit, _ ->
                    yanit.status = 401
                    yanit.contentType = MediaType.APPLICATION_JSON_VALUE
                    yanit.characterEncoding = Charsets.UTF_8.name()
                    yanit.writer.write("""{"hata":"YETKISIZ","mesaj":"Giriş yapmalısınız."}""")
                }
                it.accessDeniedHandler { _, yanit, _ ->
                    yanit.status = 403
                    yanit.contentType = MediaType.APPLICATION_JSON_VALUE
                    yanit.characterEncoding = Charsets.UTF_8.name()
                    yanit.writer.write("""{"hata":"YASAK","mesaj":"Bu işlem için yetkiniz yok."}""")
                }
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .build()
    }
}
