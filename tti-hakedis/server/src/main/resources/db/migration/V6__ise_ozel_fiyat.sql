-- V6 — Faz E: onay akışı durumları, işe özel fiyat listesi, aynı kabloda birden fazla iş.
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.

-- ---------------------------------------------------------------------------
-- Belge durumları beşe çıkıyor (NEWPHASES.md E2).
--
--   TASLAK ──gonder──▶ ONAY_BEKLIYOR ──onayla (yönetici)──▶ ONAYLANDI    (yalnızca FIZIBILITE)
--   TASLAK ──gonder──▶ TAMAMLANDI                                       (KESIF, HAKEDIS)
--   TASLAK / ONAY_BEKLIYOR ──iptal (yönetici)──▶ IPTAL
--
-- Ayrı bir 'GONDERILDI' değeri yok: "1. Keşif Tamamlandı" ile "Hakediş İşlemi Gönderildi" arasında
-- davranış farkı yok, fark yalnızca gösterilen metinde (BelgeMetinleri).
-- ---------------------------------------------------------------------------
alter table belge drop constraint belge_durum_check;
alter table belge add constraint belge_durum_check
    check (durum in ('TASLAK', 'ONAY_BEKLIYOR', 'ONAYLANDI', 'TAMAMLANDI', 'IPTAL'));

-- Fizibilitenin onaylandığı an — kim onayladı, ne zaman.
alter table belge add column onaylayan_id bigint references kullanici (id);
alter table belge add column onay_tarihi timestamptz;
alter table belge add column gonderme timestamptz;

-- 'ONAYLANDI' yalnızca fizibilitede, 'TAMAMLANDI' yalnızca keşif/hakedişte görülür. Kısıt bunu
-- veritabanı seviyesinde tutar: bir keşif belgesinin "onaylandı" olması akışta anlamsızdır ve
-- aşama kuralını sessizce yanlış çalıştırırdı (kapanış durumu türe bağlı).
alter table belge add constraint belge_durum_tur_tutarli check (
    (tur = 'FIZIBILITE' and durum <> 'TAMAMLANDI') or
    (tur <> 'FIZIBILITE' and durum not in ('ONAY_BEKLIYOR', 'ONAYLANDI'))
);

-- ---------------------------------------------------------------------------
-- Aşama kuralı (b) artık yalnızca KESIF/HAKEDIS'te (NEWPHASES.md E3).
-- Fizibilite dış dünyaya (aracı firma + satın alma onayı) bağlı olarak haftalarca bekleyebilir;
-- o süre boyunca bölgeyi kilitlemesi bir saha disiplini sorunu değil.
--
-- "Açık belge" tanımı da genişliyor: onay bekleyen fizibilite de açıktır (işi kapanmamıştır).
-- Fizibilitede "iş başına tek belge" güvencesini `belge_is_tur_ux` devralıyor — o zaten
-- (is_id, tur) where durum <> 'IPTAL' üzerinde ve yeni durumları doğal olarak kapsıyor.
-- ---------------------------------------------------------------------------
drop index belge_acik_tur_ux;

create unique index belge_acik_tur_ux on belge (bolge_id, tur)
    where durum in ('TASLAK', 'ONAY_BEKLIYOR') and tur in ('KESIF', 'HAKEDIS');

-- ---------------------------------------------------------------------------
-- İşe özel fiyat listesi (NEWPHASES.md E1).
--
-- `is_id` boşsa satır REFERANS (global) fiyat sürümüdür — V3'ün kurduğu liste böyledir.
-- Doluysa, yöneticinin o iş için aracı firmayla pazarlıktan sonra yüklediği listedir.
-- Ayrı tablo açılmıyor: `poz_fiyat` ve fiyat dondurma zinciri olduğu gibi çalışıyor.
--
-- Belgenin hangi sürüme çivilendiği:
--   FIZIBILITE      → sözleşmenin referans YAYINDA sürümü (is_id is null)
--   KESIF, HAKEDIS  → işin kendi sürümü
-- ---------------------------------------------------------------------------
alter table fiyat_versiyonu add column is_id bigint references is_kaydi (id) on delete cascade;

-- İzlenebilirlik: yanlış bir yükleme sonradan tartışma konusu olursa hangi dosyanın yüklendiği
-- kayıtta durur; aynı dosyanın ikinci kez yüklenmesi de tespit edilebilir.
alter table fiyat_versiyonu add column kaynak_dosya_adi text;
alter table fiyat_versiyonu add column kaynak_ozet text;

-- Yayın kısıtı ikiye bölünüyor: referans sürüm sözleşme başına tek, işe özel sürüm iş başına tek.
-- İşe özel sürümde durum süzgeci YOK: yeniden pazarlık edilemez (ikinci yükleme 409), yani bir işin
-- ömrü boyunca tek bir pazarlık listesi olur.
drop index fiyat_versiyonu_yayin_ux;

create unique index fiyat_versiyonu_referans_yayin_ux on fiyat_versiyonu (sozlesme_id)
    where durum = 'YAYINDA' and is_id is null;

create unique index fiyat_versiyonu_is_ux on fiyat_versiyonu (is_id)
    where is_id is not null;

-- ---------------------------------------------------------------------------
-- Aynı kablo üzerinde birden fazla iş (NEWPHASES.md E7).
--
-- Bazı hatlar çok uzun olduğu için aynı kabloda birden fazla işlem yapılabiliyor. Bu KISIT DEĞİL,
-- arama/sayım indeksidir — unique konulursa bölge sorumlusu ikinci bölümü sisteme hiç giremez
-- (D1'de SP No için verilen kararın aynısı). Hattın hangi parçası olduğunu `ek_araligi` ve `route`
-- ayırt ediyor. `kable_id` FK'sı bugüne kadar indekssizdi.
-- ---------------------------------------------------------------------------
create index is_kaydi_kable_ix on is_kaydi (kable_id);
