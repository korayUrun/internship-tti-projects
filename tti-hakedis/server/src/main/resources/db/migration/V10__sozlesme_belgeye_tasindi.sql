-- V10 — Faz N: sözleşme işten belgeye taşınıyor; işe özel pazarlık fiyat listesi kalkıyor.
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.
--
-- Artık üç belge de KENDİ sözleşmesini seçer ve o sözleşmenin yayındaki REFERANS fiyat sürümüne
-- çivilenir. Belgenin sözleşmesi `fiyat_versiyonu.sozlesme_id` üzerinden türetilir; `belge`ye yeni
-- bir sütun AÇILMAZ ve `belge.fiyat_versiyonu_id` NOT NULL kalır — "belge daima bir fiyat sürümüne
-- çivilidir" değişmezi hiç gevşemez.
--
-- ⚠️ ADIM SIRASI PAZARLIKSIZ. 1. adım `is_kaydi.sozlesme_id`'ye muhtaç, 4. adım onu düşürüyor.
-- Ters sırada hangi belgenin hangi sözleşmeye ait olduğu geri dönüşsüz kaybolur.

-- ---------------------------------------------------------------------------
-- 1) İşe özel sürüme çivili belgeleri, işin sözleşmesinin YAYINDA referans sürümüne taşı.
--
-- `belge_satir.birim_fiyat` dondurulmuş kopya olduğu için eski belgelerin TUTARLARI değişmez;
-- değişen yalnızca "hangi sürüme çivili" bilgisidir. Kabul edilmiş bir tarih kaybı: alternatifi
-- ölü sürümleri sonsuza dek taşımaktı.
-- ---------------------------------------------------------------------------
update belge bg
   set fiyat_versiyonu_id = ref.id
  from is_kaydi i
  join fiyat_versiyonu ref
    on ref.sozlesme_id = i.sozlesme_id and ref.durum = 'YAYINDA' and ref.is_id is null
 where bg.is_id = i.id
   and bg.fiyat_versiyonu_id in (select id from fiyat_versiyonu where is_id is not null);

-- ---------------------------------------------------------------------------
-- 2) İşe özel fiyat satırları ve sürümleri silinir.
--
-- `TRUNCATE ... CASCADE` YASAK (CLAUDE.md §13): cascade zinciri referans fiyat listesini de
-- götürürdü. Silme sırası elle yazılır — önce çocuk, sonra ebeveyn.
-- ---------------------------------------------------------------------------
delete from poz_fiyat where versiyon_id in (select id from fiyat_versiyonu where is_id is not null);
delete from fiyat_versiyonu where is_id is not null;

-- ---------------------------------------------------------------------------
-- 3) `is_id` sütunu düşer.
--
-- ⚠️ Bu sütun düşerken, predicate'inde onu kullanan İKİ kısmi indeks de PostgreSQL tarafından
-- sessizce düşürülür: `fiyat_versiyonu_referans_yayin_ux` ve `fiyat_versiyonu_referans_taslak_ux`.
-- Yayın tekilliği aynı migration'da yeniden kurulmazsa bir sözleşme iki YAYINDA sürüme sahip
-- olabilir ve `referansFiyatVersiyonuId` sorgusu `optional()` üzerinde patlar.
--
-- Taslak indeksi geri gelmiyor: referans fiyat düzenleme akışı (taslak aç → yayınla) Faz N'de
-- kaldırıldı. Sözleşme fiyatı değişecekse sözleşme yeniden yüklenir.
-- ---------------------------------------------------------------------------
alter table fiyat_versiyonu drop column is_id;

create unique index fiyat_versiyonu_yayin_ux on fiyat_versiyonu (sozlesme_id) where durum = 'YAYINDA';

-- ---------------------------------------------------------------------------
-- 4) Sözleşme artık belgenin, işin değil.
-- ---------------------------------------------------------------------------
alter table is_kaydi drop column sozlesme_id;
