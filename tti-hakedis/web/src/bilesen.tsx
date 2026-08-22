import { useCallback, useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import type { BelgeDurumu, BelgeTuru } from './tipler'

/**
 * Sekme yeniden odağa geldiğinde [yenile]'yi çağırır.
 *
 * Aynı kaydın iki arayüzü var: yönetici webden fizibiliteyi onaylarken bölge sorumlusu listeyi açık
 * tutuyor olabiliyor, ya da tablette bir iş açılıyor. Sekmeye dönen kullanıcı bayat veri görüp
 * "kayıt kayboldu" sanıyordu. Yoklama (polling) **yapılmaz** — sunucuya sürekli istek göndermeden,
 * yalnızca kullanıcının gerçekten baktığı anda tazelenir.
 *
 * [yenile] bir ref'te tutulur: her render'da kimliği değişse bile dinleyici yeniden bağlanmaz.
 */
export function useOdaktaTazele(yenile: () => void) {
  const sonYenile = useRef(yenile)
  sonYenile.current = yenile

  useEffect(() => {
    const dinleyici = () => {
      if (!document.hidden) sonYenile.current()
    }
    window.addEventListener('focus', dinleyici)
    document.addEventListener('visibilitychange', dinleyici)
    return () => {
      window.removeEventListener('focus', dinleyici)
      document.removeEventListener('visibilitychange', dinleyici)
    }
  }, [])
}

/**
 * Katman (modal) davranışı: Escape ile kapanma + arka planın kaydırılmasının kilitlenmesi.
 *
 * Görünüm paylaşılmıyor, **davranış** paylaşılıyor: `.katman` bir `role="dialog"`, `IslemOnayi` ise
 * `role="status" aria-live` — ikisini tek bir bileşende birleştirmek dört mod bayrağı taşıyan bir
 * parça üretirdi.
 *
 * [kapat] verilmezse Escape hiçbir şey yapmaz; kaydırma kilidi yine kurulur. Kapatılmaması gereken
 * katmanlar için bilinçli bir yol: süren ve geri alınamaz bir gönderim Escape'le kapanmamalı.
 *
 * `useOdaktaTazele` ile aynı ref deseni: satır içi bir ok fonksiyonu her render'da kimliğini
 * değiştirse de dinleyici yeniden bağlanmaz.
 */
export function useKatman(kapat?: () => void) {
  const sonKapat = useRef(kapat)
  sonKapat.current = kapat

  useEffect(() => {
    const oncekiTasma = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const dinleyici = (olay: KeyboardEvent) => {
      if (olay.key === 'Escape') sonKapat.current?.()
    }
    window.addEventListener('keydown', dinleyici)
    return () => {
      document.body.style.overflow = oncekiTasma
      window.removeEventListener('keydown', dinleyici)
    }
  }, [])
}

/**
 * `<details>` tabanlı açılır menü: **dışarı tıklayınca ve Escape ile kapanır.**
 *
 * ⚠️ Native `<details>` bunların hiçbirini kendiliğinden yapmaz — yalnızca `<summary>`'ye tıklayınca
 * açılıp kapanır. (Hafif kapanma `popover` ve `<dialog>` davranışıdır, `<details>` değil.) Menü
 * açık kalıp başka bir yere tıklandığında ekranda asılı duruyordu.
 *
 * `<details>` yine de temel: odaklanabilir bir tetikleyici ve açık/kapalı durumu tarayıcıdan geliyor,
 * ekran okuyucuya da doğru bildiriliyor. Eksik olan tek şey kapanma davranışıydı.
 *
 * `pointerdown` bilinçli (`click` değil): menü öğesine basıldığı anda kapanma kararı verilir, tıklama
 * tamamlanmadan önce. Tetikleyicinin kendisine basıldığında `contains` sayesinde kapatılmaz ve
 * `<details>`'in kendi geçişi çalışır.
 */
export function useAcilirMenu() {
  const ref = useRef<HTMLDetailsElement | null>(null)

  /** Menü öğesine tıklandığında çağrılır: seçim yapıldıktan sonra menü açık kalmamalı. */
  const kapat = useCallback(() => {
    if (ref.current) ref.current.open = false
  }, [])

  useEffect(() => {
    const disariTiklandi = (olay: PointerEvent) => {
      const oge = ref.current
      if (oge?.open && !oge.contains(olay.target as Node)) oge.open = false
    }
    const escBasildi = (olay: KeyboardEvent) => {
      if (olay.key !== 'Escape') return
      const oge = ref.current
      if (!oge?.open) return
      oge.open = false
      // Odak tetikleyiciye döner; yoksa klavye kullanıcısı sayfanın başına düşer.
      oge.querySelector('summary')?.focus()
    }

    document.addEventListener('pointerdown', disariTiklandi)
    document.addEventListener('keydown', escBasildi)
    return () => {
      document.removeEventListener('pointerdown', disariTiklandi)
      document.removeEventListener('keydown', escBasildi)
    }
  }, [])

  return { ref, kapat }
}

export function Yukleniyor({ metin = 'Yükleniyor…' }: { metin?: string }) {
  return (
    <div className="yukleniyor" role="status">
      <span className="donen-halka kucuk-halka" aria-hidden="true" />
      <span>{metin}</span>
    </div>
  )
}

export function Serit({ tur, children }: { tur: 'hata' | 'uyari' | 'basari' | 'bilgi'; children: ReactNode }) {
  return (
    <div className={`serit ${tur}`} role={tur === 'hata' ? 'alert' : 'status'}>
      {children}
    </div>
  )
}

/**
 * Veri çekilemediğinde gösterilen hata + **"Tekrar dene"**.
 *
 * CLAUDE.md §8 uzun işlemlerde tekrar deneme yolu istiyor; hiçbir ekranda yoktu çünkü her ekran
 * hata şeridini elle basıyordu. Kullanıcının tek çıkışı sayfayı yenilemekti — bu da süzgeçleri ve
 * yazdığı her şeyi götürüyordu.
 */
export function YuklemeHatasi({ mesaj, yenidenDene }: { mesaj: string; yenidenDene: () => void }) {
  return (
    <Serit tur="hata">
      <div>{mesaj}</div>
      <button className="ikincil bosluk-ust" onClick={yenidenDene}>
        Tekrar dene
      </button>
    </Serit>
  )
}

/**
 * Ekran başlığı ve sağa yaslanan eylemleri.
 *
 * Altı ekranda aynı `<div className="satir" style={{ justifyContent: 'space-between', … }}>` satırı
 * elle yazılıyordu; dördü birebir aynıydı.
 */
export function SayfaBasligi({
  baslik,
  altBaslik,
  children,
}: {
  baslik: string
  altBaslik?: ReactNode
  /** Sağa yaslanan eylemler (düğmeler, rozet). */
  children?: ReactNode
}) {
  return (
    <div className="sayfa-basligi">
      <div>
        <h1>{baslik}</h1>
        {altBaslik && <div className="soluk">{altBaslik}</div>}
      </div>
      {children}
    </div>
  )
}

/**
 * Boş liste durumu. [children] sonraki adımı taşır — boş bir ekranın asıl işi "şimdi ne yapayım"
 * sorusunu cevaplamaktır.
 */
export function BosListe({
  baslik,
  aciklama,
  children,
}: {
  baslik: string
  aciklama?: string
  children?: ReactNode
}) {
  return (
    <div className="kart liste-bos">
      <div className="liste-bos-baslik">{baslik}</div>
      {aciklama && <div className="soluk">{aciklama}</div>}
      {children && <div className="dugme-grubu bosluk-ust">{children}</div>}
    </div>
  )
}

/** Dört anlamsal durum ailesi (CLAUDE.md §8). Aile yalnızca **rengi** seçer. */
export type DurumAilesi = 'acik' | 'isleniyor' | 'tamam' | 'iptal'

/**
 * Durum kodu → renk ailesi.
 *
 * Üç tüketicisi var — rozet, kartın sol vurgu şeridi ve stepper — ve üçünün aynı jetondan beslenmesi
 * gerekiyor; aksi hâlde aynı durum kartın üç yerinde üç tonda görünür.
 *
 * ⚠️ **Metin buradan gelmez.** Durum metni her zaman sunucudan (`durumMetni`, NEWPHASES E2); web
 * hiçbir yerde durum metni yazmaz, yoksa metin iki yerde yaşar ve biri sessizce eskir.
 *
 * Yeknesek dört durum (Ağustos 2026): `ONAY_BEKLIYOR` mavi ("açık" ailesi — talep bekliyor),
 * `ISLENIYOR` amber (işleme alınmış, stepper'ın aktif halkasıyla aynı renk), `TAMAMLANDI` yeşil,
 * `IPTAL` kırmızı. Proje ve belge durumu aynı dört değeri paylaşır.
 */
export function durumAilesi(durum: string | null): DurumAilesi {
  switch (durum) {
    case 'IPTAL':
      return 'iptal'
    case 'ISLENIYOR':
      return 'isleniyor'
    case 'TAMAMLANDI':
      return 'tamam'
    default:
      // ONAY_BEKLIYOR ve `null` (henüz aşama yok) — ikisi de "onay bekleyen talep".
      return 'acik'
  }
}

export function DurumRozeti({ durum, metin }: { durum: BelgeDurumu | string; metin: string }) {
  return <span className={`rozet ${durumAilesi(durum)}`}>{metin}</span>
}

/**
 * Belge türünün kullanıcıya görünen adı.
 *
 * Sunucudan gelmiyor çünkü tür bir **sabit**, durum gibi akışa bağlı değil; `BelgeMetinleri.turAdi`
 * hata mesajları için küçük harfli ("keşif") üretiyor, arayüzde başlık gerekiyor. `IsDetay` silinince
 * buraya taşındı — orada yaşıyordu ve üç ekran oradan alıyordu.
 */
export const TUR_ADI: Record<BelgeTuru, string> = {
  FIZIBILITE: 'Ön Fizibilite',
  KESIF: '1. Keşif',
  HAKEDIS: 'Hakediş',
}

/** Aşama sırası — iş satırında düğmeler bu sırada dizilir. */
export const ASAMALAR: BelgeTuru[] = ['FIZIBILITE', 'KESIF', 'HAKEDIS']

const tarihBicimi = new Intl.DateTimeFormat('tr-TR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

/** Sunucudan gelen ISO-8601 anı, kullanıcının yerel saatinde. Para değil, biçimlendirmesi serbest. */
export function tarih(isoMetin: string | null): string {
  return isoMetin ? tarihBicimi.format(new Date(isoMetin)) : '—'
}

/**
 * Dosya boyutu. Tek birimde (KB) ve ondalıksız: `toFixed` gibi sayı biçimlendirme çağrılarının web
 * kodunda hiç bulunmaması, "para aritmetiği yok" güvencesinin grep ile denetlenmesini basitleştirir
 * (bkz. `deploy/dogrula-faz-h.sh`).
 */
export function boyut(bayt: number): string {
  return `${Math.round(bayt / 1024)} KB`
}

/**
 * Ad soyaddan baş harfler: "Servet Özkan" → "SÖ". Avatar dairesinde kullanılır.
 *
 * Büyük harfe çevirme `tr-TR` ile: `Locale.ROOT` burada **yanlış** olurdu — bu bir karşılaştırma ya
 * da dosya adı değil, kullanıcıya gösterilen metin, ve "ismail" ASCII kuralıyla "I" verirdi (§5).
 */
export function basHarfler(adSoyad: string): string {
  return adSoyad
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((parca) => parca.charAt(0).toLocaleUpperCase('tr-TR'))
    .join('')
}
