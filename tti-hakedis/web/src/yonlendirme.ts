import { useEffect, useState } from 'react'

/**
 * Adres çubuğu yönlendirmesi — **hash tabanlı**, kütüphanesiz.
 *
 * Neden hash: yol tabanlı yönlendirme, sunucuda "bilinmeyen yolu index.html'e döndür" kuralı ister.
 * Bu kural Spring Security zincirine ikinci bir istisna listesi ekler ve yanlış yazıldığında `/api`
 * altındaki gerçek 404'leri de HTML'e çevirir. Hash, sunucuya hiç ulaşmadığı için o riski tamamen
 * kaldırır: sunucu tarafında tek bir statik dosya kuralı yeter. Bağlantı paylaşımı ve yenileme yine
 * çalışır (`#/belgeler/42`).
 *
 * Altı ekran için yönlendirme kütüphanesi eklenmedi (CLAUDE.md §1/7): parçalama ve eşleştirme
 * aşağıdaki yirmi satırdır.
 */
export type Rota =
  | { ad: 'belgeler' }
  | { ad: 'isler' }
  | { ad: 'yeniIs' }
  | { ad: 'belge'; id: number }
  /** Salt okunur belge: "Gözlemle" düğmesi buraya gider. */
  | { ad: 'belgeGozlem'; id: number }
  | { ad: 'kullanicilar' }
  | { ad: 'sozlesmeler' }
  | { ad: 'kabloEnvanteri' }

function coz(hash: string): Rota {
  const parcalar = hash.replace(/^#\/?/, '').split('/').filter(Boolean)

  if (parcalar[0] === 'isler') {
    if (parcalar[1] === 'yeni') return { ad: 'yeniIs' }
    return { ad: 'isler' }
  }
  if (parcalar[0] === 'belgeler' && parcalar[1]) {
    const id = Number(parcalar[1])
    return parcalar[2] === 'gozlem' ? { ad: 'belgeGozlem', id } : { ad: 'belge', id }
  }
  // Çıplak `#/belgeler` kendi dalında çözülmek ZORUNDA: geri düşüş artık `isler` olduğu için, bu
  // satır olmadan belge arşivine hiçbir adresten ulaşılamazdı ve hata da çıkmazdı — kullanıcı
  // "Belgeler" sekmesine basınca Projeler açılırdı.
  if (parcalar[0] === 'belgeler') return { ad: 'belgeler' }
  if (parcalar[0] === 'kullanicilar') return { ad: 'kullanicilar' }
  if (parcalar[0] === 'sozlesmeler') return { ad: 'sozlesmeler' }
  if (parcalar[0] === 'kablo-envanteri') return { ad: 'kabloEnvanteri' }

  // Bilinmeyen adres **çalışma ekranına** düşer (CLAUDE.md §9: proje listesi). Eskiden belge
  // arşivine düşüyordu; giriş yapan kullanıcı her seferinde 10 sütunlu bir arşivle karşılaşıp
  // asıl işini yapacağı ekrana ikinci bir tıkla gidiyordu.
  return { ad: 'isler' }
}

/**
 * Üst şeritte hangi sekmenin etkin görüneceği.
 *
 * Belge ekranında `null` döner — oraya hem "Projeler"den hem "Belgeler"den gelinir, birini etkin
 * göstermek kullanıcıya nereden geldiğini yanlış söylerdi.
 */
/** Üst çubuktaki konum izi. Kırıntı **`Rota` nesnesinden** türer, asla adres dizesinden. */
export function kirintiYolu(rota: Rota): { ad: string; yol?: string }[] {
  const kok = { ad: 'Saha Hakediş' }
  switch (rota.ad) {
    case 'isler':
      return [kok, { ad: 'Projeler' }]
    case 'yeniIs':
      return [kok, { ad: 'Projeler', yol: '/isler' }, { ad: 'Yeni proje' }]
    case 'belgeler':
      return [kok, { ad: 'Belgeler' }]
    case 'belge':
    case 'belgeGozlem':
      // Belgeye iki sekmeden de gelinir; hangisinden gelindiğini bilmiyoruz, iddia etmiyoruz.
      return [kok, { ad: 'Belge' }]
    case 'kullanicilar':
      return [kok, { ad: 'Kullanıcılar' }]
    case 'sozlesmeler':
      return [kok, { ad: 'Sözleşmeler' }]
    case 'kabloEnvanteri':
      return [kok, { ad: 'Kablo Envanteri' }]
  }
}

export function etkinSekme(rota: Rota): string | null {
  switch (rota.ad) {
    case 'isler':
    case 'yeniIs':
      return '/isler'
    case 'belgeler':
      return '/belgeler'
    case 'kullanicilar':
      return '/kullanicilar'
    case 'sozlesmeler':
      return '/sozlesmeler'
    case 'kabloEnvanteri':
      return '/kablo-envanteri'
    default:
      return null
  }
}

/**
 * Rota değişikliğini dinleyenler. `hashchange` **aynı adrese** gidildiğinde tetiklenmiyor; üst
 * şeritteki "İşler"e zaten o sayfadayken basmak hiçbir şey yapmıyordu ve kullanıcı listenin
 * yenilenmesini bekliyordu. Kendi olayımızla o durumu da kapsıyoruz.
 */
const dinleyiciler = new Set<() => void>()

export function git(yol: string) {
  const hedef = yol.startsWith('#') ? yol : `#${yol.startsWith('/') ? '' : '/'}${yol}`
  if (window.location.hash === hedef) {
    // Adres aynı: tarayıcı olay üretmez, dinleyicileri elle uyandırırız (ekran yeniden yüklensin).
    dinleyiciler.forEach((dinleyici) => dinleyici())
    return
  }
  window.location.hash = yol
}

/**
 * Geçerli rota ve bir **tazeleme sayacı**.
 *
 * Sayaç her gezinme isteğinde artar — aynı rotaya gidildiğinde bile. Ekran bileşenine `key` olarak
 * verildiğinde React bileşeni yeniden bağlar, o da verisini baştan çeker. Aynı rotada `Rota` nesnesi
 * eşit kaldığı için tek başına yeniden çizim tetiklemezdi.
 */
export function useRota(): { rota: Rota; tazeleme: number } {
  const [rota, setRota] = useState<Rota>(() => coz(window.location.hash))
  const [tazeleme, setTazeleme] = useState(0)

  useEffect(() => {
    const dinleyici = () => {
      setRota(coz(window.location.hash))
      setTazeleme((onceki) => onceki + 1)
    }
    window.addEventListener('hashchange', dinleyici)
    dinleyiciler.add(dinleyici)
    return () => {
      window.removeEventListener('hashchange', dinleyici)
      dinleyiciler.delete(dinleyici)
    }
  }, [])

  return { rota, tazeleme }
}
