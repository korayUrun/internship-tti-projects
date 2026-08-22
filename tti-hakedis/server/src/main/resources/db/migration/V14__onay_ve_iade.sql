-- V14 — Faz V: keşif ve hakediş yönetici onayından geçiyor, iade ediliyor; yönetici durum yetkisi.
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.
--
-- Gerekçe: bugün bölge sorumlusu "Gönder" dediğinde belge kapanıyor ve geri dönüşü yok — yanlış
-- girilmiş bir keşfin tek çıkışı projeyi iptal etmekti. Onay adımı V9'da tamamen kaldırılmıştı;
-- burada YALNIZCA keşif ve hakediş için geri geliyor. Fizibilite onaysız kalıyor: V9'daki kaldırma
-- gerekçesi (dış dünyaya bağlı olduğu için yöneticide günlerce bekliyordu) hâlâ geçerli.

-- ===========================================================================
-- 1) Belge durumu: TASLAK | ONAY_BEKLIYOR | TAMAMLANDI | IPTAL
--
-- `belge_iptal_izi_tutarli` (V5) `durum <> 'IPTAL'` yazdığı için yeni değerle kendiliğinden doğru
-- çalışır; dokunulmuyor.
--
-- Bu kısıtın Kotlin ikizi `DocumentStatus`'tur ve `EnumSenkronDogrulayici` onu KAPSAMAZ (yalnızca
-- bolge/kapsam lookup tablolarını denetler). Eşleşmeyi `DocumentStageRuleTest` kilitliyor.
-- ===========================================================================
alter table belge drop constraint belge_durum_check;
alter table belge add constraint belge_durum_check
    check (durum in ('TASLAK', 'ONAY_BEKLIYOR', 'TAMAMLANDI', 'IPTAL'));

-- ===========================================================================
-- 2) İade izi.
--
-- İade belgeyi TASLAK'a geri döndürür; kayıt "neden geri geldi" bilgisini taşımazsa bölge sorumlusu
-- ekranında sıradan bir taslakla iade edilmiş bir taslağı ayırt edemez ve düzeltmesi gereken şeyi
-- bilemez. Alanlar iade edilmemiş belgede NULL kalır.
--
-- Yeniden gönderimde temizlenmez: "bu aşama bir kez iade edilmişti" bilgisi kalıcıdır.
-- ===========================================================================
alter table belge add column iade_gerekce text;
alter table belge add column iade_eden_id bigint references kullanici (id);
alter table belge add column iade_tarihi timestamptz;

alter table belge add constraint belge_iade_izi_tutarli check (
    (iade_gerekce is null and iade_eden_id is null and iade_tarihi is null) or
    (iade_gerekce is not null and iade_eden_id is not null and iade_tarihi is not null)
);

-- ===========================================================================
-- 3) Durum müdahalesi kaydı.
--
-- Yönetici artık projeyi ve belgeyi her durumdan her duruma çekebiliyor (kapanmış projeyi yeniden
-- açmak dahil). Bu yetkinin bedeli iz bırakmasıdır: gerekçesiz bir müdahale, sonradan "bu proje
-- neden yeniden açıldı" sorusunu cevapsız bırakırdı.
--
-- Tek tablo, iki hedef: `is_id` XOR `belge_id`. İki ayrı tablo aynı beş sütunu ve aynı yazma
-- kodunu ikizlerdi.
--
-- Otomatik geçişler (fizibilite açılışında null → ACIK, hakediş onayında → KAPANDI) buraya
-- YAZILMAZ: kayıt "kim, neden müdahale etti" sorusunun cevabıdır, sistemin kendi akışının değil.
-- ===========================================================================
create table durum_kaydi (
    id           bigserial   primary key,
    is_id        bigint      references is_kaydi (id) on delete cascade,
    belge_id     bigint      references belge (id) on delete cascade,
    onceki       text,
    yeni         text        not null,
    gerekce      text        not null,
    kullanici_id bigint      not null references kullanici (id),
    zaman        timestamptz not null default now(),
    constraint durum_kaydi_tek_hedef check (num_nonnulls(is_id, belge_id) = 1)
);

create index durum_kaydi_is_ix on durum_kaydi (is_id, zaman desc) where is_id is not null;
create index durum_kaydi_belge_ix on durum_kaydi (belge_id, zaman desc) where belge_id is not null;
