-- V8 — Faz L: yöneticinin arayüzden sözleşme yüklemesi ve referans birim fiyat güncellemesi.
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.
--
-- Bu faza kadar sisteme sözleşme eklemenin tek yolu bir migration'dı (V3__sozlesme_seed.sql) ve
-- `poz_fiyat`'a yazan tek kod fizibilite onayında **işe özel** pazarlık listesi üretiyordu. Şema
-- baştan beri referans sürümün de versiyonlanabileceğini varsayıyordu (`fiyat_versiyonu.durum`
-- üç değerli) ama TASLAK ve ARSIV hiç kullanılmadı; bu migration o boşluğu kapatıyor.

-- ---------------------------------------------------------------------------
-- Sözleşmenin nereden geldiğinin izi. `fiyat_versiyonu`'nun eşdeğer alanları V6'da eklenmişti;
-- sözleşmenin kendisi izsizdi: hangi dosyadan, ne zaman, kim tarafından yüklendiği kayıtta yoktu.
-- `kaynak_ozet` (SHA-256) aynı dosyanın ikinci kez yüklendiğini tespit etmeyi sağlar.
--
-- Mevcut iki sözleşme (V3 seed) bu alanlarda NULL kalır: migration'dan geldiler, yükleyen yok.
-- ---------------------------------------------------------------------------
alter table sozlesme add column olusturan_id bigint references kullanici (id);
alter table sozlesme add column olusturma timestamptz not null default now();
alter table sozlesme add column kaynak_dosya_adi text;
alter table sozlesme add column kaynak_ozet text;

-- ---------------------------------------------------------------------------
-- Referans sürümün kullanıcıya gösterilecek sıra numarası ("Fiyat listesi v3").
--
-- `id` bu iş için kullanılamaz: bigserial tüm sözleşmeler ve işe özel sürümler arasında paylaşılıyor,
-- yani bir sözleşmenin ikinci sürümü `id = 47` olabilir. Sıra sözleşme içinde anlamlıdır.
-- İşe özel sürümlerde NULL kalır: iş başına tek sürüm var, numaralanacak bir dizi yok.
-- ---------------------------------------------------------------------------
alter table fiyat_versiyonu add column sira integer;

update fiyat_versiyonu set sira = 1 where is_id is null;

-- ---------------------------------------------------------------------------
-- Sözleşme başına tek açık TASLAK referans sürüm.
--
-- Düzenleme akışı: yönetici "Fiyatları Düzenle" der → YAYINDA sürüm TASLAK olarak kopyalanır →
-- satır satır ya da Excel ile düzenlenir → "Yayınla" ile eski sürüm ARSIV'e, taslak YAYINDA'ya geçer.
-- İki eşzamanlı taslak, hangisinin yayınlanacağı belirsiz bir durum yaratırdı; kısmi indeks bunu
-- veritabanı seviyesinde imkânsız kılıyor (V6'daki referans yayın kısıtının ikizi).
--
-- Yazma yalnızca TASLAK sürümlere yapılır: YAYINDA ve ARSIV sürümler salt okunurdur, çünkü geçmiş
-- belgeler onlara çivili (CLAUDE.md §7/4). Bu kural SQL süzgeciyle korunur (ReferansFiyatSorgulari).
-- ---------------------------------------------------------------------------
create unique index fiyat_versiyonu_referans_taslak_ux on fiyat_versiyonu (sozlesme_id)
    where durum = 'TASLAK' and is_id is null;
