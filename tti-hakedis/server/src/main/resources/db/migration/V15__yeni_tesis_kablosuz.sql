-- V15 — Faz W: kapsamı "Yeni Tesis" olan projelerde kablo seçimi zorunlu değil.
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.
--
-- Gerekçe (yönetici, 10 Ağustos 2026): "Yeni Tesis kapsamı seçilen projelerde iş kablo ile ilgili
-- olacak ancak belli bir kablo ID'nin kapsamında olmuyor." Tesis yeni kuruluyor; envanterde o işe
-- karşılık gelen bir kablo henüz yok. Zorunluluk kalsaydı bu projeler ya hiç açılamaz ya alakasız
-- bir kablo seçilerek açılır ve hakediş belgesine yanlış bir kablo kimliğiyle adres basılırdı.
--
-- Kablo yoksa uç adresleri de envanterden gelemez; `adres_a` bu yüzden birlikte nullable oluyor.
-- Kablosuz projede yönetici uçları elle girebilir (opsiyonel) ve girilen değer yine **donmuştur**:
-- envanter yenilendiğinde değişmez (CLAUDE.md §7/5).

-- ---------------------------------------------------------------------------
-- Aşağıdaki CHECK kapsam kimliğini gömüyor (alt sorgu CHECK içinde kullanılamaz). Seed değişmişse
-- migration burada DURSUN: sessizce yanlış kapsamı muaf tutan bir kısıt, kablosuz açılmaması gereken
-- projelerin açılmasına izin verir ve hata ancak hakediş çıktısında görünür.
-- ---------------------------------------------------------------------------
do $$
begin
    if not exists (select 1 from kapsam where id = 4 and kod = 'YENI_TESIS') then
        raise exception 'kapsam.id = 4 artık YENI_TESIS değil; V15 gözden geçirilmeli.';
    end if;
end
$$;

alter table is_kaydi alter column kable_id    drop not null;
alter table is_kaydi alter column kable_nosid drop not null;
alter table is_kaydi alter column adres_a     drop not null;

-- Kural yalnızca domain'de (`Scope.kableZorunluMu`) değil veritabanında da duruyor: "diğer bütün
-- kapsamlarda kablo zorunlu" bir iş değişmezidir ve servis katmanında bir dal unutulursa hata
-- SESSİZ olur — kablosuz bir Deplase projesi açılır, kimse fark etmez.
alter table is_kaydi add constraint is_kaydi_kable_zorunlu
    check (kable_id is not null or kapsam_id = 4);

-- `is_kaydi_kable_ix` (V6) nullable sütunda çalışmaya devam eder: PostgreSQL B-tree indeksleri
-- null taşır ve `ayniKableSayisi` sorgusu zaten yalnızca dolu `kable_id` ile çağrılıyor.
