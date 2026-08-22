package tr.com.tti.hakedis.server.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.ToStringSerializer
import java.math.BigDecimal

/**
 * Para ve miktar değerleri JSON'a **string** olarak yazılır: `"648.35852723050107"`.
 *
 * Varsayılan davranışta Jackson `BigDecimal`'i JSON *sayısı* olarak yazar. JavaScript her JSON sayısını
 * `double`'a çevirdiği için 17 haneli ham birim fiyat istemcide sessizce bozulur ve kuruş kayması doğar.
 * String olarak yazmak hem hassasiyeti korur hem de istemcinin sayıya çevirip aritmetik yapmasını caydırır
 * (CLAUDE.md: web ve mobil para aritmetiği yapmaz, tutarı sunucudan alır).
 *
 * Okuma yönünde dönüşüme gerek yoktur: Jackson string'i doğrudan `BigDecimal`'e çevirir ve
 * `BigDecimal(String)` yapıcısı kullanıldığı için `double` üzerinden geçiş olmaz.
 */
@Configuration
class ParaJsonYapilandirmasi {
    @Bean
    fun paraStringModulu(): SimpleModule = SimpleModule("ParaStringModulu")
        .addSerializer(BigDecimal::class.java, ToStringSerializer(BigDecimal::class.java))
}
