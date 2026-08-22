package tr.com.tti.hakedis.server.db

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import tr.com.tti.hakedis.domain.workorder.Region
import tr.com.tti.hakedis.domain.workorder.Scope

/**
 * Veritabanındaki lookup kodları ile `:core:domain` enum'larının aynı kaldığını açılışta doğrular.
 *
 * Bölge ve kapsam iki yerde birden yaşıyor: Kotlin tarafında `enum`, veritabanı tarafında lookup tablosu.
 * İkisi ayrışırsa hata sessizdir — kod eşleşmeyen satır basitçe listelenmez ve kullanıcı eksik veriyi
 * fark etmez. Bu yüzden uyuşmazlıkta sunucu **açılmaz** (fail loud, CLAUDE.md §7).
 */
@Component
class EnumSenkronDogrulayici(private val jdbc: JdbcClient) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        dogrula("bolge", "select kod from bolge", Region.entries.map { it.name })
        dogrula("kapsam", "select kod from kapsam", Scope.entries.map { it.name })
    }

    private fun dogrula(tablo: String, sorgu: String, kotlinKodlari: List<String>) {
        val dbKodlari: Set<String> = jdbc.sql(sorgu).query(String::class.java).list().filterNotNull().toSet()
        val beklenen: Set<String> = kotlinKodlari.toSet()

        val dbdeEksik = beklenen - dbKodlari
        val kotlindaEksik = dbKodlari - beklenen
        if (dbdeEksik.isEmpty() && kotlindaEksik.isEmpty()) return

        throw IllegalStateException(
            buildString {
                append("'$tablo' tablosu ile Kotlin enum'u uyuşmuyor. ")
                if (dbdeEksik.isNotEmpty()) append("Veritabanında eksik: ${dbdeEksik.sorted()}. ")
                if (kotlindaEksik.isNotEmpty()) append("Kotlin enum'unda karşılığı yok: ${kotlindaEksik.sorted()}. ")
                append("Migration ve enum aynı anda güncellenmeli.")
            },
        )
    }
}
