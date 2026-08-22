-- Proje 2: TTIDataRapor — veritabanı `rapor`, rol `rapor`, şema `rapor`.
--
-- Çalıştırma (bir kez, `db` ilk açılıştan sonra):
--     docker exec -i tti-db psql -U tti_hakedis -d tti -v ON_ERROR_STOP=1 < provizyon/02-rapor.sql
--
-- ⚠️ Konteynerin otomatik başlatma betikleri (initdb.d) yalnızca veritabanı İLK KEZ
-- oluşturulurken çalışır; volume'de veri varsa hiç çalışmazlar. Bu yüzden elle.

-- Ayrıcalıksız rol. `POSTGRES_USER` (bakım hesabı) bir uygulama hesabı DEĞİLDİR: imaj onu
-- SUPERUSER olarak açar ve superuser tüm yetki kontrollerini atlar (bkz. 01-tti.sql).
create user rapor with password 'degistir-beni';   -- .env → RAPOR_DB_PAROLA ile AYNI olmalı

create database rapor owner rapor;

-- ⚠️ Atlanırsa "veritabanı ayrımı" sandığınızdan zayıf olur: PostgreSQL bağlanma yetkisini
-- varsayılan olarak PUBLIC'e verir.
revoke connect on database rapor from public;
grant  connect on database rapor to rapor;

-- Şemayı EF Core'un ilk migration'ı açar (EnsureSchema). Bunun için gereken tek yetki:
grant create on database rapor to rapor;

\connect rapor
revoke all on schema public from public;
