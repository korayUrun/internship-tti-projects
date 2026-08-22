-- V7 — Faz F: hakediş adımının eksik parçaları — güzergâh dosyası, saha görselleri, imza.
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.

-- ---------------------------------------------------------------------------
-- EK1 tekildir: bir belgenin tek bir güzergâh dosyası olur.
--
-- Kısıt uygulamada da kontrol ediliyor, ama asıl güvence burada: iki eşzamanlı yükleme servis
-- kontrolünü birlikte geçse bile ikinci satır veritabanında düşer ve çağıran taraf 409'a çevirir.
-- EK2'de böyle bir kısıt YOK — saha görselinin adedi sınırsızdır.
-- ---------------------------------------------------------------------------
create unique index ek_ek1_ux on ek (belge_id) where tur = 'EK1_ROTA';

-- Kim yükledi izi: dosya sonradan tartışma konusu olursa (yanlış güzergâh, eksik görsel) kaydı durur.
alter table ek add column yukleyen_id bigint references kullanici (id);

-- ---------------------------------------------------------------------------
-- İmza (NEWPHASES.md E8).
--
-- Belge başına TEK imza olduğu için `belge_id` doğrudan birincil anahtardır: ayrı bir yüzey kimliği
-- ve onun üzerinde bir unique index tutmak aynı kuralı iki kez yazmak olurdu. Yeniden çizilen imza
-- aynı satırın üzerine yazılır.
--
-- `imzalayan_ad_soyad` DONDURULARAK kopyalanır. `kullanici.ad_soyad` sonradan düzeltilebilir
-- (evlilik, yazım hatası, LDAP'a geçiş); kopyalanmazsa altı ay önce imzalanmış bir hakedişin
-- altındaki ad geriye dönük değişir. Aynı gerekçe kable adreslerinin işe kopyalanmasında da geçerli
-- (CLAUDE.md §7 — çok kullanıcılı sistemin pazarlıksız kuralları/5).
--
-- `depo_yolu` adı `ek` tablosuyla aynı: ikisi de YUKLEME_KOK'e göreli yollardır. Kök değiştiğinde
-- (ortak ağ dizinine geçiş) veritabanında hiçbir satır güncellenmez.
-- ---------------------------------------------------------------------------
create table belge_imza (
    belge_id           bigint    primary key references belge (id) on delete cascade,
    depo_yolu          text      not null,
    boyut_bayt         bigint    not null check (boyut_bayt >= 0),
    imzalayan_id       bigint    not null references kullanici (id),
    imzalayan_ad_soyad text      not null,
    tarih              timestamptz not null default now()
);
