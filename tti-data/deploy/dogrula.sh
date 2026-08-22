#!/usr/bin/env bash
# TTI Merkezi Veri — PROJE SEVİYESİ doğrulama. Salt okunur, üretimde de güvenli.
#
# Bu betik platform doğrulamasının (tti-sunucu/dogrula.sh) YERİNE geçmez, ÜSTÜNE gelir:
# orası makine-çapı sınırları (port yüzeyi, proxy, havuz bütçesi) denetler, burası bu projenin
# kendi sözleşme maddelerini.
#
# Kullanım:
#   bash deploy/dogrula.sh
#   DB=tti-data-db DB_BAKIM=bakim API_PORT=8093 PANEL_PORT=8094 bash deploy/dogrula.sh
#   # sunucuda:
#   DOCKER="sudo docker" DB=tti-db DB_BAKIM=tti_hakedis API_PORT=8083 PANEL_PORT=8084 \
#     bash deploy/dogrula.sh

set -uo pipefail

DOCKER="${DOCKER:-docker}"
DB="${DB:-tti-data-db}"                 # postgres konteyneri (sunucuda: tti-db)
DB_BAKIM="${DB_BAKIM:-bakim}"           # bakım superuser'ı (sunucuda: tti_hakedis)
VT="${VT:-data}"                        # veritabanı adı
SEMA="${SEMA:-data}"                    # şema adı
ROL="${ROL:-data}"                      # uygulama rolü
API_PORT="${API_PORT:-8093}"
PANEL_PORT="${PANEL_PORT:-8094}"
KOMSU_VT="${KOMSU_VT:-tti rapor}"       # bağlanamaması gereken diğer projelerin veritabanları

hata_sayisi=0
basarili() { printf '\033[32m  ✓\033[0m %s\n' "$1"; }
basarisiz() { printf '\033[31m  ✗\033[0m %s\n' "$1"; hata_sayisi=$((hata_sayisi + 1)); }
bilgi()    { printf '\033[90m  ·\033[0m %s\n' "$1"; }
baslik()   { printf '\n\033[1m%s\033[0m\n' "$1"; }

sql() { $DOCKER exec -i "$DB" psql -U "$DB_BAKIM" -d "$VT" -tAc "$1" 2>/dev/null; }

# --- 1. Şema yerleşimi ----------------------------------------------------------------------
baslik "1. Şema yerleşimi"

tablo_sayisi="$(sql "select count(*) from information_schema.tables where table_schema='$SEMA'")"
if [ "${tablo_sayisi:-0}" -ge 5 ]; then
  basarili "$SEMA şemasında $tablo_sayisi tablo var"
else
  basarisiz "$SEMA şemasında yalnızca ${tablo_sayisi:-0} tablo — migration koştu mu?"
fi

public_sayisi="$(sql "select count(*) from information_schema.tables where table_schema='public'")"
if [ "${public_sayisi:-1}" = "0" ]; then
  basarili "public şeması boş"
else
  basarisiz "public şemasında $public_sayisi tablo VAR — migration hedefi yanlış"
fi

# ⚠️ En kolay unutulan ayar: version_table_schema verilmezse alembic_version public'e düşer.
# (EF Core'daki MigrationsHistoryTable'ın karşılığı.)
av_sema="$(sql "select table_schema from information_schema.tables where table_name='alembic_version'")"
if [ "$av_sema" = "$SEMA" ]; then
  basarili "alembic_version $SEMA şemasında"
else
  basarisiz "alembic_version '${av_sema:-yok}' şemasında — alembic/env.py version_table_schema?"
fi

surum_num="$(sql "select version_num from $SEMA.alembic_version")"
if [ -n "$surum_num" ]; then
  basarili "migration sürümü: $surum_num"
else
  basarisiz "alembic_version okunamadı"
fi

# --- 2. Projeler arası sınır ----------------------------------------------------------------
baslik "2. Projeler arası sınır"

superuser="$(sql "select rolsuper from pg_roles where rolname='$ROL'")"
if [ "$superuser" = "f" ]; then
  basarili "$ROL rolü superuser DEĞİL"
elif [ -z "$superuser" ]; then
  bilgi "$ROL rolü bulunamadı — provizyon çalıştı mı?"
else
  basarisiz "$ROL rolü SUPERUSER — tüm yetki kontrollerini atlar, projeler arası sınır yok"
fi

for komsu in $KOMSU_VT; do
  var_mi="$(sql "select 1 from pg_database where datname='$komsu'")"
  if [ -z "$var_mi" ]; then
    bilgi "$komsu veritabanı bu makinede yok — atlandı"
    continue
  fi
  yetki="$(sql "select has_database_privilege('$ROL','$komsu','connect')")"
  if [ "$yetki" = "f" ]; then
    basarili "$ROL rolü '$komsu' veritabanına bağlanamıyor"
  else
    basarisiz "$ROL rolü '$komsu' veritabanına BAĞLANABİLİYOR — sınır delinmiş"
  fi
done

# --- 3. Sağlık ucu --------------------------------------------------------------------------
baslik "3. Sağlık ucu"

saglik="$(curl -fsS --max-time 5 "http://localhost:${API_PORT}/api/saglik" 2>/dev/null)"
if [ -z "$saglik" ]; then
  basarisiz "/api/saglik yanıt vermiyor (:$API_PORT) — konteyner ayakta mı?"
else
  case "$saglik" in
    *'"durum":"ayakta"'*) basarili "/api/saglik: ayakta" ;;
    *) basarisiz "/api/saglik 'ayakta' demiyor: $saglik" ;;
  esac
  case "$saglik" in
    *'"veritabani":"ayakta"'*) basarili "veritabanı bağlantısı ayakta" ;;
    *) basarisiz "veritabanına erişilemiyor: $saglik" ;;
  esac
  # Eksik `:Z` SELinux etiketini İLK YÜKLEMEDEN ÖNCE yakalayan tek kontrol.
  case "$saglik" in
    *'"yuklemeYazilabilir":true'*) basarili "/yukleme yazılabilir" ;;
    *) basarisiz "/yukleme YAZILAMIYOR — bind mount ':Z' etiketi eksik olabilir" ;;
  esac
  surum="$(printf '%s' "$saglik" | sed -n 's/.*"surum":"\([^"]*\)".*/\1/p')"
  case "${surum:-}" in
    ""|bilinmiyor) basarisiz "surum alanı boş/bilinmiyor — SURUM ortam değişkeni verilmemiş" ;;
    *) basarili "çalışan sürüm: $surum" ;;
  esac
fi

# --- 4. Yetkilendirme -----------------------------------------------------------------------
baslik "4. Yetkilendirme"

kod="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://localhost:${API_PORT}/api/datasets")"
if [ "$kod" = "401" ] || [ "$kod" = "403" ]; then
  basarili "X-API-Key'siz /api/datasets reddediliyor ($kod)"
else
  basarisiz "X-API-Key'siz /api/datasets $kod döndü — 401 bekleniyordu"
fi

kod="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -H 'X-API-Key: gecersiz-anahtar' \
  "http://localhost:${API_PORT}/api/datasets")"
if [ "$kod" = "401" ] || [ "$kod" = "403" ] || [ "$kod" = "200" ]; then
  # 200 + boş liste de kabul: geçersiz anahtarın hiçbir dataset'e erişimi yoktur.
  basarili "geçersiz anahtar veri sızdırmıyor ($kod)"
else
  basarisiz "geçersiz anahtarla beklenmedik yanıt: $kod"
fi

kod="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://localhost:${API_PORT}/admin/datasets")"
if [ "$kod" = "401" ] || [ "$kod" = "403" ]; then
  basarili "token'sız /admin/datasets reddediliyor ($kod)"
else
  basarisiz "token'sız /admin/datasets $kod döndü — 401 bekleniyordu"
fi

# --- 5. Havuz tavanı ------------------------------------------------------------------------
baslik "5. Havuz tavanı (beyan: 10)"

# 40 eşzamanlı istek: havuz sınırsız olsaydı bağlantı sayısı 10'u aşardı ve o an DİĞER
# projeler `sorry, too many clients` ile düşerdi.
for _ in $(seq 1 40); do
  curl -s -o /dev/null --max-time 10 "http://localhost:${API_PORT}/api/saglik" &
done
wait 2>/dev/null

zirve="$(sql "select count(*) from pg_stat_activity where usename='$ROL'")"
if [ "${zirve:-99}" -le 10 ]; then
  basarili "40 eşzamanlı istekten sonra $ROL bağlantısı: ${zirve:-?} (≤10)"
else
  basarisiz "$ROL bağlantısı ${zirve} — beyan edilen 10'luk tavan aşılmış"
fi

# --- 6. Panel -------------------------------------------------------------------------------
baslik "6. Panel"

kod="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://localhost:${PANEL_PORT}/")"
[ "$kod" = "200" ] && basarili "panel kökü 200 dönüyor" \
                   || basarisiz "panel kökü $kod döndü (:$PANEL_PORT)"

# SPA geri düşüşü: istemci yolu doğrudan yenilendiğinde index.html dönmeli.
kod="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://localhost:${PANEL_PORT}/login")"
[ "$kod" = "200" ] && basarili "/login SPA geri düşüşü çalışıyor" \
                   || basarisiz "/login $kod döndü — nginx try_files eksik olabilir"

# /docs dış bir CDN'e bağlıysa kapalı ağda BOŞ açılır ve dataset şeması tanımlanamaz.
dis="$(curl -fsS --max-time 5 "http://localhost:${API_PORT}/docs" 2>/dev/null | grep -cE 'src="https?://|href="https?://')"
if [ "${dis:-1}" = "0" ]; then
  basarili "/docs dış CDN'e bağlı değil (kapalı ağda açılır)"
else
  basarisiz "/docs $dis adet dış bağlantı içeriyor — kapalı ağda boş açılır"
fi

# --- Sonuç ----------------------------------------------------------------------------------
baslik "Sonuç"
if [ "$hata_sayisi" -eq 0 ]; then
  printf '\033[32mTüm proje kontrolleri geçti.\033[0m\n'
  exit 0
fi
printf '\033[31m%d kontrol başarısız.\033[0m\n' "$hata_sayisi"
exit 1
