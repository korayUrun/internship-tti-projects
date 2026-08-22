#!/usr/bin/env bash
#
# TTIDataRapor doğrulama betiği — salt okunur, üretimde de güvenli.
#
# En kritik iddiası VERİTABANI AYRIMI'dır: bu proje yalnızca kendi `rapor` veritabanında yaşamalı,
# `public` şemasına hiçbir şey yazmamalı ve BAŞKA bir projenin veritabanına bağlanamamalıdır.
# Ayrım bozulduğunda hiçbir hata çıkmaz — uygulama çalışmaya devam eder ve sorun ancak iki proje
# çakıştığında, muhtemelen aylar sonra ortaya çıkar. Bu betik o sessiz başarısızlığın tek
# yakalanma noktasıdır.
#
# Kullanım:
#   bash deploy/dogrula.sh
#   DB_KAPSAYICI=tti-db SUNUCU=http://localhost:8082 bash deploy/dogrula.sh

set -uo pipefail

DB_KAPSAYICI="${DB_KAPSAYICI:-tti-rapor-db}"
DB_ADI="${DB_ADI:-rapor}"          # BU PROJENİN veritabanı
KOMSU_DB="${KOMSU_DB:-tti}"        # bağlanmamamız gereken komşu (yoksa o kontrol atlanır)
DB_KULLANICI="${DB_KULLANICI:-rapor}"
SUNUCU="${SUNUCU:-http://localhost:8082}"
SEMA="rapor"
HAVUZ_SINIRI="${HAVUZ_SINIRI:-10}"

hata_sayisi=0

basarili() { printf '\033[32m✔\033[0m %s\n' "$1"; }
basarisiz() { printf '\033[31m✘\033[0m %s\n' "$1"; hata_sayisi=$((hata_sayisi + 1)); }
baslik()   { printf '\n\033[1m%s\033[0m\n' "$1"; }

# Veritabanına normalde konteyner üzerinden gidilir. PSQL_KOMUTU verilirse (ör. konteynersiz
# geliştirme ya da uzaktan bağlantı) doğrudan o kullanılır:
#   PSQL_KOMUTU="psql -h /tmp -p 55432 -U postgres" bash deploy/dogrula.sh
sorgu() {  # sorgu <sql> [veritabani]
  local vt="${2:-$DB_ADI}"
  if [ -n "${PSQL_KOMUTU:-}" ]; then
    $PSQL_KOMUTU -d "$vt" -tAc "$1" 2>/dev/null
  else
    docker exec -i "$DB_KAPSAYICI" psql -U "$DB_KULLANICI" -d "$vt" -tAc "$1" 2>/dev/null
  fi
}

# --- 0. Ön koşul --------------------------------------------------------------------------
if [ -z "${PSQL_KOMUTU:-}" ] && ! docker exec "$DB_KAPSAYICI" true 2>/dev/null; then
  basarisiz "Veritabanı konteynerine ulaşılamadı: $DB_KAPSAYICI"
  echo "  DB_KAPSAYICI ortam değişkeniyle doğru adı verin (üretimde: tti-db),"
  echo "  ya da PSQL_KOMUTU ile doğrudan bir psql çağrısı geçin."
  exit 2
fi

# --- 1. Veritabanı ayrımı ------------------------------------------------------------------
baslik "1. Veritabanı ayrımı"

rapor_tablo="$(sorgu "select count(*) from information_schema.tables where table_schema='$SEMA'")"
if [ "${rapor_tablo:-0}" -ge 4 ]; then
  basarili "$DB_ADI veritabanı → $SEMA şemasında $rapor_tablo tablo var"
else
  basarisiz "$DB_ADI veritabanı → $SEMA şemasında beklenen tablolar yok (bulunan: ${rapor_tablo:-0}, beklenen: en az 4)"
fi

public_tablo="$(sorgu "select count(*) from information_schema.tables where table_schema='public'")"
if [ "${public_tablo:-0}" -eq 0 ]; then
  basarili "public şeması boş"
else
  basarisiz "public şemasında $public_tablo tablo var — burası kimsenin çalışma alanı değil"
  sorgu "select table_name from information_schema.tables where table_schema='public'" | sed 's/^/    /'
fi

# HasDefaultSchema tabloları taşır ama migration geçmişi tablosunu TAŞIMAZ; en kolay unutulan ayar.
gecmis_sema="$(sorgu "select table_schema from information_schema.tables where table_name='__EFMigrationsHistory'")"
if [ "$gecmis_sema" = "$SEMA" ]; then
  basarili "__EFMigrationsHistory $SEMA şemasında"
elif [ -z "$gecmis_sema" ]; then
  basarisiz "__EFMigrationsHistory bulunamadı — migration hiç koşmamış olabilir"
else
  basarisiz "__EFMigrationsHistory '$gecmis_sema' şemasında olmamalı — MigrationsHistoryTable ayarı eksik"
fi

# En değerli kontrol: komşu projenin veritabanına BAĞLANAMIYOR olmalıyız. PostgreSQL bağlanma
# yetkisini varsayılan olarak PUBLIC'e verir; provizyonda `revoke connect ... from public`
# atlanmışsa ayrım sandığınızdan zayıftır ve bunu başka hiçbir kontrol göstermez.
komsu_var="$(sorgu "select 1 from pg_database where datname='$KOMSU_DB'")"
if [ "$komsu_var" = "1" ]; then
  yetki="$(sorgu "select has_database_privilege('$SEMA','$KOMSU_DB','CONNECT')")"
  if [ "$yetki" = "f" ]; then
    basarili "$SEMA rolü → $KOMSU_DB veritabanına bağlanamıyor"
  else
    basarisiz "$SEMA rolü → $KOMSU_DB veritabanına BAĞLANABİLİYOR (revoke connect ... from public atlanmış)"
  fi
else
  echo "  · $KOMSU_DB veritabanı yok (komşu proje kurulu değil) — atlandı"
fi

# --- 2. Migration durumu ------------------------------------------------------------------
baslik "2. Migration"

uygulanan="$(sorgu "select count(*) from $SEMA.\"__EFMigrationsHistory\"")"
# Sütun adı `migration_id`, `MigrationId` değil: snake_case dönüşümü EF'in kendi geçmiş
# tablosuna da uygulanıyor. EF kendi içinde tutarlı olduğu için sorun değil, ama elle sorgu
# yazarken şaşırtır.
son_migration="$(sorgu "select migration_id from $SEMA.\"__EFMigrationsHistory\" order by migration_id desc limit 1")"
if [ "${uygulanan:-0}" -ge 1 ]; then
  basarili "$uygulanan migration uygulanmış (son: $son_migration)"
else
  basarisiz "Hiç migration uygulanmamış"
fi

# --- 3. Veri tipleri ----------------------------------------------------------------------
baslik "3. Veri tipleri"

zaman_tipi="$(sorgu "select data_type from information_schema.columns where table_schema='$SEMA' and table_name='kablo_talep' and column_name='olusturulma_tarihi'")"
if [ "$zaman_tipi" = "timestamp with time zone" ]; then
  basarili "Tarih sütunları timestamptz"
else
  basarisiz "olusturulma_tarihi tipi 'timestamp with time zone' değil: '$zaman_tipi'"
fi

# Kolonlar snake_case olmalı; PascalCase kalırsa her sorgu çift tırnak ister ve `hakedis`
# şemasıyla aynı ağaçta iki farklı adlandırma yaşar.
pascal="$(sorgu "select count(*) from information_schema.columns where table_schema='$SEMA' and column_name ~ '[A-Z]' and table_name <> '__EFMigrationsHistory'")"
if [ "${pascal:-0}" -eq 0 ]; then
  basarili "Sütun adları snake_case"
else
  basarisiz "$pascal sütun büyük harf içeriyor — snake_case dönüşümü uygulanmamış"
fi

# --- 4. Başlangıç verisi ------------------------------------------------------------------
baslik "4. Başlangıç verisi"

kullanici="$(sorgu "select count(*) from $SEMA.kullanici")"
if [ "${kullanici:-0}" -ge 11 ]; then
  basarili "$kullanici kullanıcı kayıtlı"
else
  basarisiz "Kullanıcı sayısı beklenenden az: ${kullanici:-0}"
fi

duz_parola="$(sorgu "select count(*) from $SEMA.kullanici where parola_ozeti not like '\$2%'")"
if [ "${duz_parola:-0}" -eq 0 ]; then
  basarili "Tüm parolalar BCrypt özeti olarak saklanmış"
else
  basarisiz "$duz_parola kullanıcının parolası BCrypt özeti değil"
fi

# --- 5. Servis ----------------------------------------------------------------------------
baslik "5. Servis"

saglik="$(curl -fsS "$SUNUCU/api/saglik" 2>/dev/null)"
if [ -n "$saglik" ]; then
  basarili "Sağlık ucu yanıt veriyor: $saglik"
  case "$saglik" in
    *'"yuklemeYazilabilir":true'*) basarili "Yükleme kökü yazılabilir" ;;
    *) basarisiz "Yükleme kökü yazılamıyor — SELinux etiketi (:Z) veya klasör izni eksik olabilir" ;;
  esac
else
  basarisiz "Sağlık ucuna ulaşılamadı: $SUNUCU/api/saglik"
fi

# Kimliksiz erişim kapalı olmalı. Bu tek satır, yetki süzgecinin sunucuda olduğunun kanıtıdır.
kod="$(curl -s -o /dev/null -w '%{http_code}' "$SUNUCU/api/kablo" 2>/dev/null)"
if [ "$kod" = "401" ]; then
  basarili "Oturumsuz istek 401 dönüyor"
else
  basarisiz "Oturumsuz /api/kablo isteği $kod döndü, 401 bekleniyordu"
fi

# --- 6. Bağlantı havuzu -------------------------------------------------------------------
# Npgsql'in varsayılanı 100'dür. max_connections tüm projelerce PAYLAŞILAN sert bir sınır
# olduğu için, sınırsız bir havuz bütçeyi tek başına tüketip DİĞER projeleri düşürür — hata da
# yanlış projenin log'una çıkar. Aşağıdaki yük, sınırın gerçekten uygulandığını kanıtlar.
baslik "6. Bağlantı havuzu"

for _ in $(seq 1 40); do curl -s -o /dev/null "$SUNUCU/api/saglik" & done
wait 2>/dev/null
acik="$(sorgu "select count(*) from pg_stat_activity where usename='$SEMA'")"
if [ "${acik:-0}" -le "$HAVUZ_SINIRI" ]; then
  basarili "Yük altında en çok ${acik:-0} bağlantı (sınır: $HAVUZ_SINIRI)"
else
  basarisiz "${acik} açık bağlantı, beyan edilen sınır $HAVUZ_SINIRI — 'Maximum Pool Size' ayarlanmamış"
fi

# --- Sonuç --------------------------------------------------------------------------------
baslik "Sonuç"
if [ "$hata_sayisi" -eq 0 ]; then
  printf '\033[32mTüm kontroller geçti.\033[0m\n'
  exit 0
fi
printf '\033[31m%d kontrol başarısız.\033[0m\n' "$hata_sayisi"
exit 1
