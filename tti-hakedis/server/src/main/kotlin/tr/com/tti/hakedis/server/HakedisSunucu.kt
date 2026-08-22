package tr.com.tti.hakedis.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * [ConfigurationPropertiesScan] çıktı etiketlerini (`cikti.etiket.*`) `application.yml`'den okur:
 * kullanıcıya görünen metin kodda sabitlenmez (CLAUDE.md §13).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class HakedisSunucu

fun main(args: Array<String>) {
    runApplication<HakedisSunucu>(*args)
}
