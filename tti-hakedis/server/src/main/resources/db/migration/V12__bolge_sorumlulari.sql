-- Bölge sorumlularının gösterim adlarını örnek değerlere günceller.
--
-- `V1__init.sql`'de yer tutucu unvanlar vardı ("İzmir Bölge Sorumlusu"). Ad soyad kullanıcıya
-- görünen her yerde okunuyor: proje listesindeki "Güncelleyen" sütunu, proje Excel'i, üst şerit.
--
-- Değişiklik **geriye dönük görünür**: belge kayıtları `guncelleyen_id` tutuyor, ad soyad her
-- sorguda birleştirmeyle geliyor. Yani eski belgelerde de artık gerçek isim yazacak — aynı kişi
-- olduğu için doğru davranış budur.
--
-- ⚠️ `V1__init.sql` DÜZENLENMEDİ: yayımlanmış bir migration'ın içeriği değişirse Flyway checksum
-- uyuşmazlığı verir ve sunucu hiç açılmaz (CLAUDE.md §13).

update kullanici set ad_soyad = 'Bolge Sorumlusu 1' where kullanici_adi = 'ist-avr';
update kullanici set ad_soyad = 'Bolge Sorumlusu 2' where kullanici_adi = 'ist-and';
update kullanici set ad_soyad = 'Bolge Sorumlusu 3' where kullanici_adi = 'edirne';
update kullanici set ad_soyad = 'Bolge Sorumlusu 4' where kullanici_adi = 'ankara';
update kullanici set ad_soyad = 'Bolge Sorumlusu 5' where kullanici_adi = 'kutahya';
update kullanici set ad_soyad = 'Bolge Sorumlusu 6' where kullanici_adi = 'izmir';
update kullanici set ad_soyad = 'Bolge Sorumlusu 7' where kullanici_adi = 'bursa';

-- Fail loud: bir kullanıcı adı tutmasaydı yukarıdaki `update` sessizce 0 satır günceller ve
-- o bölge sorumlusu yer tutucu unvanla kalırdı. Yedi ismin de yerine oturduğunu doğruluyoruz.
do $$
declare eksik text;
begin
    select string_agg(kullanici_adi, ', ' order by kullanici_adi) into eksik
      from kullanici
     where rol = 'BOLGE_SORUMLUSU'
       and ad_soyad like '%Bölge Sorumlusu%';

    if eksik is not null then
        raise exception 'Ad soyad güncellenmemiş bölge sorumlusu var: %', eksik;
    end if;
end $$;
