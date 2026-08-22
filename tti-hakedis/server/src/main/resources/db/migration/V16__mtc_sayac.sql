-- "Yeni Tesis" projelerine otomatik "mtc#####" kimliği üreten sayaç (12 Ağustos 2026, kullanıcı isteği).
--
-- Faz W'de Yeni Tesis projesinde Kablo ID serbest metin (opsiyonel) idi; artık sistem atıyor:
-- mtc90000'den başlayan tek, genel bir dizi. Kimlik `is_kaydi.kable_nosid`'e yazılır (Kablo ID alanı);
-- envantere (kable tablosuna) EKLENMEZ — kullanıcı kararı "sadece proje etiketi": kablo aramasında
-- çıkmaz, `AYNI_KABLE_VAR` uyarısı çalışmaz, Faz W'nin "Yeni Tesis envantere dokunmaz" izolasyonu korunur.
--
-- `is_sayac` / `belge_sayac` ile AYNI kalıp: sequence DEĞİL, tek-satır tablo + atomik
-- `insert ... on conflict do update ... returning`. Sequence rollback'te boşluk bırakır ve numara
-- 90000'lik "yeni tesis" aralığında boşluksuz ilerlemeli. Sayacı okuyan `MtcNumaraUretici` çağıranın
-- `@Transactional` olması sayesinde proje insert'i düşerse sayaç artışı da geri alınır.
--
-- İlk 90000 değeri sayaç ilk kez okunduğunda `insert ... values (true, 90000)` ile doğar; tabloyu
-- seed'lemeye gerek yok. `id boolean primary key` + check(id) tablonun tek satırlı kalmasını garantiler.
create table mtc_sayac (
    id     boolean primary key default true,
    son_no integer not null,
    constraint mtc_sayac_tek_satir check (id)
);
