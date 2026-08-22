-- GELİŞTİRME veritabanı kurulumu — `deploy/docker-compose.yml`in kendi postgres konteyneri
-- ilk açılışta bunu `/docker-entrypoint-initdb.d/` üzerinden çalıştırır.
--
-- ⚠️ ÜRETİMDE BU DOSYA KULLANILMAZ. Sunucuda otorite `tti-sunucu/provizyon/03-data.sql`dir ve
-- elle, bir kez çalıştırılır. Buradaki tek fark parolanın geliştirme değeri olması.
--
-- ⚠️ initdb.d betikleri YALNIZCA veritabanı İLK KEZ oluşturulurken çalışır; volume'de veri
-- varsa hiç çalışmazlar.

-- Ayrıcalıksız uygulama rolü. `POSTGRES_USER` (bakım hesabı) bir uygulama hesabı DEĞİLDİR:
-- imaj onu SUPERUSER olarak açar ve superuser tüm yetki kontrollerini atlar.
create user data with password 'data';

create database data owner data;

-- ⚠️ Atlanırsa "veritabanı ayrımı" sandığınızdan zayıf olur: PostgreSQL yeni bir veritabanına
-- bağlanma yetkisini varsayılan olarak PUBLIC'e verir.
revoke connect on database data from public;
grant  connect on database data to data;

-- Şemayı Alembic'in env.py'si açar (`create schema if not exists data`). Gereken tek yetki:
grant create on database data to data;

\connect data
revoke all on schema public from public;
