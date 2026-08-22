import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiHatasi } from '../api'
import {
  basHarfler,
  BosListe,
  durumAilesi,
  SayfaBasligi,
  TUR_ADI,
  tarih,
  useAcilirMenu,
  useOdaktaTazele,
  Yukleniyor,
  YuklemeHatasi,
} from '../bilesen'
import { git } from '../yonlendirme'
import type { BelgeListeSatiri, BelgeSuzgeci, BelgeTuru, Bolge, Kullanici, Varyant } from '../tipler'

/** Süzgeç seçenekleri. Durum metinleri sunucudan geldiği için burada yalnızca **kod** listelenir. */
const TURLER: { deger: string; ad: string }[] = [
  { deger: '', ad: 'Tümü' },
  { deger: 'FIZIBILITE', ad: TUR_ADI.FIZIBILITE },
  { deger: 'KESIF', ad: TUR_ADI.KESIF },
  { deger: 'HAKEDIS', ad: TUR_ADI.HAKEDIS },
]

/**
 * Durum süzgecinin etiketleri.
 *
 * Bunlar **durum metni değil**, durum makinesinin kodlarının okunabilir hâli. Kullanıcıya gösterilen
 * durum metni `(tür, durum)` çiftine bağlı ve sunucudan gelir (`durumMetni`) — süzgeçte ise tür henüz
 * seçilmemiş olabileceği için tek başına duruma karşılık gelen bir ad gerekiyor.
 */
const DURUMLAR: { deger: string; ad: string }[] = [
  { deger: '', ad: 'Tümü' },
  { deger: 'ONAY_BEKLIYOR', ad: 'Onay Bekliyor' },
  { deger: 'ISLENIYOR', ad: 'İşleniyor' },
  { deger: 'TAMAMLANDI', ad: 'Tamamlandı' },
  { deger: 'IPTAL', ad: 'İptal Edildi' },
]

/**
 * Belge listesi — üretilmiş belgelerin arşiv görünümü ve indirme noktası.
 *
 * ~~Onay kuyruğu~~ Faz M'de kalktı: onay akışı yok, dolayısıyla ön ayarlı bir süzgece de gerek yok.
 * Günlük çalışma **iş listesinde** yürüyor; bu ekran "hangi belgeler üretildi, hangisini indireyim"
 * sorusunu cevaplıyor.
 *
 * Süzgeçlerin hepsi **sunucuya** gider; istemcide liste filtrelenmez (yetki süzgeci SQL'de).
 */
export function BelgeListesi({ kullanici, arama }: { kullanici: Kullanici; arama: string }) {
  const yonetici = kullanici.rol === 'YONETICI'
  const [suzgec, setSuzgec] = useState<BelgeSuzgeci>({})
  const [satirlar, setSatirlar] = useState<BelgeListeSatiri[] | null>(null)
  const [bolgeler, setBolgeler] = useState<Bolge[]>([])
  const [hata, setHata] = useState<string | null>(null)

  useEffect(() => {
    if (yonetici) api.bolgeler().then(setBolgeler).catch(() => setBolgeler([]))
  }, [yonetici])

  /** [sessiz]: sekmeye dönüldüğünde tazelerken listeyi boşaltma — duran ekranda titreme yaratıyor. */
  const cek = useCallback(
    async (sessiz = false) => {
      if (!sessiz) setSatirlar(null)
      setHata(null)
      try {
        setSatirlar(await api.belgeler({ ...suzgec, ara: arama.trim() || undefined }))
      } catch (e) {
        setHata(e instanceof ApiHatasi ? e.message : 'Belgeler yüklenemedi.')
      }
    },
    [suzgec, arama],
  )

  useEffect(() => {
    void cek()
  }, [cek])

  useOdaktaTazele(() => void cek(true))

  function degistir(alan: keyof BelgeSuzgeci, deger: string) {
    setSuzgec((onceki) => ({ ...onceki, [alan]: deger }))
  }

  // Seçili yıl listede kalmalı: süzgeç uygulanınca liste o yıla iner ve seçenek kaybolursa
  // açılır liste kendi seçimini gösteremez hâle gelirdi.
  const yillar = [...new Set([...(satirlar ?? []).map((s) => String(s.yil)), suzgec.yil].filter(Boolean))].sort(
    (a, b) => Number(b) - Number(a),
  )

  return (
    <>
      <SayfaBasligi
        baslik="Belgeler"
        altBaslik="Üretilmiş fizibilite, keşif ve hakediş belgelerinin arşivi"
      >
        <button className="ikincil" onClick={() => git('/isler')}>
          Proje listesine dön
        </button>
      </SayfaBasligi>

      <div className="suzgec-satiri">
        <label className="suzgec-hap">
          <span className="gizli-metin">Belge türü</span>
          <select value={suzgec.tur ?? ''} onChange={(e) => degistir('tur', e.target.value)}>
            {TURLER.map((t) => (
              <option key={t.deger} value={t.deger}>
                {t.deger === '' ? 'Tüm türler' : t.ad}
              </option>
            ))}
          </select>
        </label>

        <label className="suzgec-hap">
          <span className="gizli-metin">Durum</span>
          <select value={suzgec.durum ?? ''} onChange={(e) => degistir('durum', e.target.value)}>
            {DURUMLAR.map((d) => (
              <option key={d.deger} value={d.deger}>
                {d.deger === '' ? 'Tüm durumlar' : d.ad}
              </option>
            ))}
          </select>
        </label>

        {/* Bölge süzgeci yalnızca yöneticide: bölge sorumlusu zaten tek bölge görüyor (süzgeç SQL'de). */}
        {yonetici && (
          <label className="suzgec-hap">
            <span className="gizli-metin">Bölge</span>
            <select value={suzgec.bolgeKodu ?? ''} onChange={(e) => degistir('bolgeKodu', e.target.value)}>
              <option value="">Tüm bölgeler</option>
              {bolgeler.map((b) => (
                <option key={b.kod} value={b.kod}>
                  {b.ad}
                </option>
              ))}
            </select>
          </label>
        )}

        {/*
          Yıl listesi **gelen satırlardan** türetiliyor: sunucu ayrı bir "hangi yıllar var" ucu
          vermiyor ve açmak, dört dekoratif sayaç için uç açmakla aynı kapıya çıkardı. Süzgeç
          uygulanınca liste daralacağı için seçili yıl listede olmasa da seçenek olarak korunur.
        */}
        <label className="suzgec-hap">
          <span className="gizli-metin">Yıl</span>
          <select value={suzgec.yil ?? ''} onChange={(e) => degistir('yil', e.target.value)}>
            <option value="">Tüm yıllar</option>
            {yillar.map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
        </label>

        {suzgec.tur && (
          <SuzgecCipi
            etiket={`Tür: ${TURLER.find((t) => t.deger === suzgec.tur)?.ad ?? suzgec.tur}`}
            kaldir={() => degistir('tur', '')}
          />
        )}
        {suzgec.durum && (
          <SuzgecCipi
            etiket={`Durum: ${DURUMLAR.find((d) => d.deger === suzgec.durum)?.ad ?? suzgec.durum}`}
            kaldir={() => degistir('durum', '')}
          />
        )}
        {suzgec.bolgeKodu && (
          <SuzgecCipi
            etiket={`Bölge: ${bolgeler.find((b) => b.kod === suzgec.bolgeKodu)?.ad ?? suzgec.bolgeKodu}`}
            kaldir={() => degistir('bolgeKodu', '')}
          />
        )}
        {suzgec.yil && <SuzgecCipi etiket={`Yıl: ${suzgec.yil}`} kaldir={() => degistir('yil', '')} />}
        {arama.trim() && <SuzgecCipi etiket={`Arama: ${arama.trim()}`} />}

        {/* Süzülmemiş toplamı sunucu vermiyor; "142 belgeden 1–6 arası" uydurma bir payda olurdu. */}
        {satirlar && <span className="suzgec-sayi">{satirlar.length} belge gösteriliyor</span>}
      </div>

      {hata && <YuklemeHatasi mesaj={hata} yenidenDene={() => void cek()} />}

      {!satirlar ? (
        hata ? null : (
          <Yukleniyor />
        )
      ) : satirlar.length === 0 ? (
        <BosListe
          baslik="Bu süzgeçle eşleşen belge yok."
          aciklama="Belgeler proje listesindeki aşama satırlarından açılır."
        >
          <button className="ikincil" onClick={() => git('/isler')}>
            Proje Listesi
          </button>
        </BosListe>
      ) : (
        <div className="kart tablo-sarici">
          <table className="belge-tablosu">
            <thead>
              <tr>
                <th>Belge</th>
                <th>Tür</th>
                <th>Durum</th>
                <th>Sorumlu</th>
                <th className="sayi">Toplam</th>
                <th>Oluşturma</th>
                <th>İndir</th>
              </tr>
            </thead>
            <tbody>
              {satirlar.map((satir) => (
                <tr key={satir.id}>
                  <td>
                    {/*
                      Gerçek bağlantı, tıklanabilir satır değil: satır hem tıklanabilir hem içinde
                      indirme düğmeleri taşıyordu, bu yüzden iki ayrı `stopPropagation` gerekiyordu.
                      Bağlantı ayrıca klavye erişimi ve yeni sekmede açmayı bedavaya getiriyor.
                    */}
                    <a className="belge-no" href={`#/belgeler/${satir.id}`}>
                      {satir.belgeNo}
                    </a>
                    {/* İkinci satır: belgenin ait olduğu proje. Sütun sayısı ondan üçe indi. */}
                    <div className="belge-kimlik">
                      <span className="mono">{satir.spNo}</span>
                      <span className="is-ayrac">·</span>
                      <span className="mono">{satir.kableNosid ?? '—'}</span>
                      <span className="is-ayrac">·</span>
                      <span>{satir.ekAraligi}</span>
                    </div>
                  </td>
                  <td>
                    <span className="tur-cipi">{TUR_ADI[satir.tur]}</span>
                  </td>
                  <td>
                    <DurumNoktasi durum={satir.durum} metin={satir.durumMetni} />
                  </td>
                  <td>
                    <span className="sorumlu">
                      <span className="sorumlu-avatar" aria-hidden="true">
                        {basHarfler(satir.sorumluAdSoyad)}
                      </span>
                      <span className="sorumlu-metin">
                        <span>{satir.sorumluAdSoyad}</span>
                        <span className="bos-not">{satir.bolgeAdi}</span>
                        {/* Oluşturan sorumludan farklıysa ayrıca gösterilir (madde 16). */}
                        {satir.olusturanAdSoyad && satir.olusturanAdSoyad !== satir.sorumluAdSoyad && (
                          <span className="bos-not">Oluşturan: {satir.olusturanAdSoyad}</span>
                        )}
                      </span>
                    </span>
                  </td>
                  {/* Tutar sunucudan hazır biçimlenmiş gelir; web para aritmetiği ve formatlaması yapmaz. */}
                  <td className={satir.durum === 'IPTAL' ? 'sayi mono soluk' : 'sayi mono'}>
                    {satir.toplamGosterim}
                  </td>
                  <td className="soluk">{tarih(satir.olusturma)}</td>
                  <td>
                    <IndirmeDugmeleri belgeId={satir.id} tur={satir.tur} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

/**
 * Satır içi indirme.
 *
 * Fizibilite ve keşifte dört düğme: iki çıktı × iki varyant, ikisi de gerçek bir ihtiyaç —
 * `FIYATLI` yöneticinin pazarlık tabanı, `TEKLIF` aracı firmaya giden fiyatsız kopya (E6).
 * **Hakedişte iki düğme**: fiyat pazarlığı bitmiştir, fiyatsız hakediş kopyasının gideceği yer yok
 * (kullanıcı kararı, 31 Temmuz 2026).
 */
function IndirmeDugmeleri({ belgeId, tur }: { belgeId: number; tur: BelgeTuru }) {
  const [inen, setInen] = useState<string | null>(null)
  /**
   * Sunucunun söylediği sebep saklanır. Eskiden yalnızca `boolean` tutuluyor ve ekrana çıplak
   * "indirilemedi" yazılıyordu: kullanıcı ne olduğunu da, ne yapacağını da bilmiyordu.
   */
  const [hata, setHata] = useState<string | null>(null)
  /** Son denenen indirme — "tekrar" düğmesi onu yeniden çalıştırır. */
  const sonDeneme = useRef<{ anahtar: string; calistir: () => Promise<void> } | null>(null)
  const { ref: menuRef, kapat: menuKapat } = useAcilirMenu()

  async function indir(anahtar: string, calistir: () => Promise<void>) {
    sonDeneme.current = { anahtar, calistir }
    setHata(null)
    setInen(anahtar)
    try {
      await calistir()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Dosya indirilemedi.')
    } finally {
      setInen(null)
    }
  }

  function tekrarDene() {
    const deneme = sonDeneme.current
    if (deneme) void indir(deneme.anahtar, deneme.calistir)
  }

  const dugmeler: { anahtar: string; etiket: string; calistir: () => Promise<void> }[] = [
    { anahtar: 'pdf', etiket: 'PDF', calistir: () => api.pdfIndir(belgeId, 'FIYATLI' as Varyant) },
    { anahtar: 'xls', etiket: 'Excel', calistir: () => api.excelIndir(belgeId, 'FIYATLI' as Varyant) },
    ...(tur === 'HAKEDIS'
      ? []
      : [
          { anahtar: 'pdfT', etiket: 'PDF · teklif', calistir: () => api.pdfIndir(belgeId, 'TEKLIF' as Varyant) },
          { anahtar: 'xlsT', etiket: 'Excel · teklif', calistir: () => api.excelIndir(belgeId, 'TEKLIF' as Varyant) },
        ]),
  ]

  return (
    <div className="indirme">
      {/*
        Dört düğme yerine tek düğme + menü. Açılıp kapanma ve klavye erişimi `<details>`'ten;
        **dışarı tıklayınca ve Escape ile kapanma** `useAcilirMenu`'den — native `<details>` bunları
        kendiliğinden yapmıyor, menü açık kalıp ekranda asılı duruyordu.
      */}
      <details ref={menuRef} className="indirme-menu">
        <summary className="ikincil">{inen ? 'İndiriliyor…' : 'İndir'}</summary>
        <div className="indirme-secenekler">
          {dugmeler.map((d) => (
            <button
              key={d.anahtar}
              className="sade"
              disabled={inen !== null}
              onClick={() => {
                menuKapat()
                void indir(d.anahtar, d.calistir)
              }}
            >
              {inen === d.anahtar ? '…' : d.etiket}
            </button>
          ))}
        </div>
      </details>
      {/* Sebep `title`'da: sunucu mesajı uzun olabiliyor ve hücreyi dağıtırdı. */}
      {hata && (
        <button className="sade kucuk indirme-hatasi" title={hata} disabled={inen !== null} onClick={tekrarDene}>
          İndirilemedi — tekrar
        </button>
      )}
    </div>
  )
}

/**
 * Durum: renkli nokta + sunucunun metni.
 *
 * Rozet yerine nokta — tabloda yan yana yedi sütun varken dolgulu rozetler satırı ağırlaştırıyordu.
 * Renk ailesi rozetle **aynı** kaynaktan (`durumAilesi`); ikisi ayrışamaz.
 */
function DurumNoktasi({ durum, metin }: { durum: string; metin: string }) {
  return (
    <span className={`durum-nokta-alani ${durumAilesi(durum)}`}>
      <span className="durum-nokta" aria-hidden="true" />
      {metin}
    </span>
  )
}

/** Etkin süzgeç çipi. [kaldir] verilmezse çip bilgilendirir ama kaldırılamaz (arama kabukta). */
function SuzgecCipi({ etiket, kaldir }: { etiket: string; kaldir?: () => void }) {
  return (
    <span className="suzgec-cipi">
      {etiket}
      {kaldir && (
        <button type="button" className="sade cip-kaldir" aria-label={`${etiket} süzgecini kaldır`} onClick={kaldir}>
          ×
        </button>
      )}
    </span>
  )
}
