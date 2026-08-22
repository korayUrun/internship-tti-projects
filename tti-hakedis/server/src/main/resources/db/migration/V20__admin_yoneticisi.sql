-- V20 — İkinci yönetici hesabı: `admin` (kullanıcı isteği, 19 Ağustos 2026).
--
-- Yayımlanmış bir migration ASLA düzenlenmez; bu yüzden `V1__init.sql`'deki insert'e dokunulmuyor,
-- yeni hesap ayrı bir sürümle geliyor.
--
-- Rol `YONETICI`: mevcut `yonetici` hesabıyla **birebir aynı** yetkiler. Ayrı bir yetki katmanı
-- DEĞİL — hakedişte üçüncü bir rol yok ve istenmedi de. `kullanici_rol_bolge_tutarli` kısıtı gereği
-- yöneticinin `bolge_id`'si null olmak zorunda; bu sayede tüm bölgeleri görür.
--
-- Parola `TTI000000` — `yonetici` hesabıyla aynı olduğu için hash'i de aynı (BCrypt tuzu hash'in
-- içinde; aynı hash aynı parolayı doğrular). Yeni bir hash üretmek yerine var olanı kopyalamak,
-- parolanın gerçekten `TTI000000` olduğunu bu dosyadan doğrulanabilir kılıyor.
-- ⚠️ Teslimden sonra değiştirin (bkz. KULLANICILAR-VE-PAROLALAR.md).
--
-- `on conflict do nothing`: `admin` adında bir hesap elle açılmışsa migration düşmez, atlar.

insert into kullanici (kullanici_adi, ad_soyad, sifre_hash, rol, bolge_id) values
    ('admin', 'Admin', '$2y$10$HwTmC2oS1B8r4DI3el.KFOpyEzf55fw9iQrhLewhWYziC98Tx5hFu', 'YONETICI', null)
on conflict (kullanici_adi) do nothing;
