-- V9 — Faz M: yönetici onayı, imza ve bölge sıra kuralı kalkıyor.
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.
--
-- Gerekçe (30 Temmuz kurgusunun sahada görülen bedeli): fizibilite yöneticiye düşüp pazarlık
-- fiyatları yüklenene kadar keşif açılamıyordu ve bu döngü günlerce sürüyordu. Onay adımı
-- kaldırıldı; aşama kapısı artık işin kendi durumu (V11).

-- ---------------------------------------------------------------------------
-- 1) Tür-durum kısıtı ÖNCE düşer.
--
-- V6'daki kısıt fizibilitenin TAMAMLANDI olmasını yasaklıyor; aşağıdaki update tam olarak bunu
-- yapacak. Sıra ters çevrilirse migration kısıt ihlaliyle düşer ve sunucu hiç açılmaz.
-- ---------------------------------------------------------------------------
alter table belge drop constraint belge_durum_tur_tutarli;

-- ---------------------------------------------------------------------------
-- 2) Var olan onay durumları "gönderilmiş"e indirgenir.
--
-- Onaylanmış fizibilite = gönderilmiş fizibilite. Anlam kaybı bilinçli ve kabul edilmiş: onay
-- kavramı sistemden tamamen çıktığı için ONAYLANDI'yı ayrı taşımanın karşılığı kalmadı.
-- `tamamlanma` doldurulur, yoksa satır ekranında "değiştirilme tarihi" boş görünürdü.
-- ---------------------------------------------------------------------------
update belge
   set tamamlanma = coalesce(tamamlanma, onay_tarihi, gonderme, olusturma)
 where durum in ('ONAY_BEKLIYOR', 'ONAYLANDI');

update belge set durum = 'TAMAMLANDI' where durum in ('ONAY_BEKLIYOR', 'ONAYLANDI');

alter table belge drop constraint belge_durum_check;
alter table belge add constraint belge_durum_check
    check (durum in ('TASLAK', 'TAMAMLANDI', 'IPTAL'));

alter table belge drop column onaylayan_id;
alter table belge drop column onay_tarihi;

-- ---------------------------------------------------------------------------
-- 3) Bölge sıra kuralı (D3/b, E3) kalkıyor.
--
-- "Bir bölgede aynı türden tek açık belge" kısıtı, bölge sorumlusunu aynı anda birden fazla işi
-- ilerletmekten alıkoyuyordu ve kullanıcı sırayı ancak 409 hatasını görünce öğreniyordu. Artık
-- sırayı işin durumu (Açık / İşleniyor) gösteriyor.
--
-- `belge_is_tur_ux` (iş başına tür başına tek canlı belge) KORUNUR — eşzamanlı iki taslak
-- açılışına karşı tek veritabanı güvencesi odur.
-- ---------------------------------------------------------------------------
drop index belge_acik_tur_ux;

-- ---------------------------------------------------------------------------
-- 4) "Son güncelleyen" izi.
--
-- İş listesi satır ekranı her aşamanın yanında "değiştirilme tarihi" ve "kim güncelledi" gösteriyor.
-- `olusturan_id` bunu karşılamıyor: miktarı giren, belgeyi açandan başkası olabilir.
-- ---------------------------------------------------------------------------
alter table belge add column guncelleme timestamptz;
alter table belge add column guncelleyen_id bigint references kullanici (id);

update belge set guncelleme = coalesce(tamamlanma, gonderme, olusturma), guncelleyen_id = olusturan_id;

alter table belge alter column guncelleme set not null;

-- ---------------------------------------------------------------------------
-- 5) İmza kalkıyor (E8 geri alınıyor).
--
-- Diskteki IMZA.png dosyaları SİLİNMEZ: yükleme kökü asıl veridir ve kaybı geri alınamaz
-- (CLAUDE.md §7/6). Temizlik gerekirse elle yapılır.
-- ---------------------------------------------------------------------------
drop table belge_imza;
