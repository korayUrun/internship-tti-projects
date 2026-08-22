package tr.com.tti.hakedis.server.lookup

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class LookupDenetleyici(private val sorgular: LookupSorgulari) {
    @GetMapping("/bolgeler")
    fun bolgeler(): List<BolgeYaniti> = sorgular.bolgeler()

    @GetMapping("/kapsamlar")
    fun kapsamlar(): List<KapsamYaniti> = sorgular.kapsamlar()
}
