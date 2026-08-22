#!/usr/bin/env bash
#
# Platform doğrulaması — salt okunur, üretimde her an güvenle çalıştırılabilir.
#
# Bu betik PROJELERİN kendi doğrulamalarının YERİNE geçmez, ÜSTÜNE gelir. Projeler kendi
# içlerinin doğru olduğunu kontrol eder; burada kontrol edilen şey **projeler arasındaki
# sınırlar** — yani hiçbir projenin tek başına göremeyeceği, yalnızca makinenin tamamına
# bakınca görünen şeyler:
#
#   · Bir proje başkasının veritabanına bağlanabiliyor mu
#   · `public` şemaları boş mu (paylaşılan tek çarpışma alanı)
#   · Havuz sınırlarının toplamı `max_connections`'ı aşıyor mu
#   · Sunucuda `dev`/`latest` gibi HAREKETLİ etiketli imaj çalışıyor mu (geri dönüşü imkânsız kılar)
#   · Tüm sağlık uçları yeşil mi ve hangi sürümü bildiriyor
#
# Kullanım:
#   bash dogrula.sh
#   PROJELER="tti:tti-hakedis-server:8081 rapor:tti-rapor-server:8082" bash dogrula.sh

set -uo pipefail

DB_KAPSAYICI="${DB_KAPSAYICI:-tti-db}"
DB_SAHIBI="${DB_SAHIBI:-tti_hakedis}"
DB_ADI="${DB_ADI:-tti}"

# Ayarlar yanındaki .env'den okunur; ortam değişkeniyle override edilebilir.
# Portları GÖMMEK YERİNE .env'den okumanın sebebi: geliştirme makinesinde portlar sunucudakiyle
# aynı değil (orada 8082/8083 başka şeylerce kullanılıyor). Gömülü olsaydı betik yanlış porta
# bakar ve BAŞKA BİR PROJENİN sağlık ucunu bu projeninki sanıp "geçti" derdi.
_env_oku() {
  local anahtar="$1" varsayilan="$2" deger
  deger="$(grep -E "^${anahtar}=" "$(dirname "$0")/.env" 2>/dev/null | head -1 | cut -d= -f2-)"
  printf '%s' "${deger:-$varsayilan}"
}

DOMAIN="${DOMAIN:-$(_env_oku DOMAIN tti.local)}"

# "veritabanı:konteyner:port" üçlüleri. Yeni proje eklerken buraya bir üçlü eklenir.
# Port, o projenin HOST'a bağlanan portudur (5. bölüm buraya curl atıyor).
PROJELER="${PROJELER:-tti:tti-hakedis-server:$(_env_oku TTI_PORT 8081) rapor:tti-rapor-server:$(_env_oku RAPOR_PORT 8082) data:tti-data-server:$(_env_oku DATA_PORT 8083)}"

# Veritabanı olmayan ama sürümlenen kapsayıcılar — yalnızca 4. bölümdeki etiket kontrolüne
# girerler. PROJELER'e eklenselerdi olmayan bir veritabanı ve olmayan bir /api/saglik ucu
# aranırdı.
EK_KAPSAYICILAR="${EK_KAPSAYICILAR:-tti-data-panel}"

# Yerel prova içindir. Sunucuda BOŞ KALIR: sertifika kurumsal CA'dan gelir ve zincirin
# sistemce doğrulanabilir olması kontrolün ta kendisidir (`-k` bilerek kullanılmıyor).
# Geliştirme makinesinde sertifika kendi imzalıdır ve doğrulanamaz:
#   CA_SERTIFIKA=certs/fullchain.pem bash dogrula.sh
CA_SERTIFIKA="${CA_SERTIFIKA:-}"
_ca_arg=()
[ -n "$CA_SERTIFIKA" ] && _ca_arg=(--cacert "$CA_SERTIFIKA")

# Beyan edilen havuz sınırları — .env'deki bütçe tablosuyla AYNI olmalı.
BEYAN_EDILEN_HAVUZ="${BEYAN_EDILEN_HAVUZ:-30}"
BAKIM_YEDEGI="${BAKIM_YEDEGI:-5}"

hata_sayisi=0
basarili()  { printf '\033[32m✔\033[0m %s\n' "$1"; }
basarisiz() { printf '\033[31m✘\033[0m %s\n' "$1"; hata_sayisi=$((hata_sayisi + 1)); }
bilgi()     { printf '  · %s\n' "$1"; }
baslik()    { printf '\n\033[1m%s\033[0m\n' "$1"; }

sorgu() {  # sorgu <veritabani> <sql>
  docker exec -i "$DB_KAPSAYICI" psql -U "$DB_SAHIBI" -d "$1" -tAc "$2" 2>/dev/null
}

if ! docker exec "$DB_KAPSAYICI" true 2>/dev/null; then
  basarisiz "Veritabanı konteynerine ulaşılamadı: $DB_KAPSAYICI"
  exit 2
fi

# --- 1. Veritabanı sınırları ----------------------------------------------------------------
baslik "1. Veritabanı sınırları"

for ucla in $PROJELER; do
  vt="${ucla%%:*}"
  var="$(sorgu "$DB_ADI" "select 1 from pg_database where datname='$vt'")"
  if [ "$var" = "1" ]; then
    tablo="$(sorgu "$vt" "select count(*) from information_schema.tables where table_schema not in ('pg_catalog','information_schema')")"
    basarili "$vt veritabanı var (${tablo:-0} tablo)"
  else
    basarisiz "$vt veritabanı yok — proje kurulmamış olabilir"
  fi
done

# `public` her veritabanında paylaşılan tek isim alanıdır; iki projenin çarpışabileceği tek
# yer orasıdır. Boş olmalı.
for ucla in $PROJELER; do
  vt="${ucla%%:*}"
  sorgu "$DB_ADI" "select 1 from pg_database where datname='$vt'" >/dev/null || continue
  pub="$(sorgu "$vt" "select count(*) from information_schema.tables where table_schema='public'")"
  if [ "${pub:-0}" -eq 0 ]; then
    basarili "$vt → public şeması boş"
  else
    basarisiz "$vt → public şemasında $pub tablo var"
  fi
done

# --- 2. Çapraz erişim -----------------------------------------------------------------------
# En değerli kontrol: bir projenin rolü BAŞKA bir projenin veritabanına bağlanabiliyor mu.
# PostgreSQL yeni veritabanına bağlanma yetkisini VARSAYILAN OLARAK PUBLIC'e verir; provizyon
# SQL'inde `revoke connect ... from public` atlanmışsa sınır sandığınızdan zayıftır ve bunu
# başka hiçbir kontrol göstermez.
baslik "2. Çapraz erişim (roller birbirinin veritabanına bağlanabiliyor mu)"

# ⚠️ ÖNCE SUPERUSER KONTROLÜ. Superuser TÜM yetki kontrollerini atlar; superuser bir rolle
# bağlanan uygulama, `revoke connect` ne yazarsanız yazın her projenin verisini okur ve
# aşağıdaki bağlanma kontrolü de "bağlanabiliyor" der ama nedenini göstermez.
# Postgres imajı POSTGRES_USER ile verilen rolü superuser olarak açar — o rol BAKIM hesabıdır,
# hiçbir uygulama onunla bağlanmamalıdır.
for ucla in $PROJELER; do
  rol="$(sorgu "$DB_ADI" "select rolname from pg_roles where rolname='${ucla%%:*}' or rolname='${ucla%%:*}_uygulama' order by rolname desc limit 1")"
  [ -n "$rol" ] || continue
  su="$(sorgu "$DB_ADI" "select rolsuper from pg_roles where rolname='$rol'")"
  if [ "$su" = "t" ]; then
    basarisiz "$rol SUPERUSER — uygulama rolü asla superuser olmamalı, tüm sınırları atlar"
  else
    basarili "$rol ayrıcalıksız (superuser değil)"
  fi
done

for ucla in $PROJELER; do
  vt="${ucla%%:*}"
  rol="$(sorgu "$DB_ADI" "select rolname from pg_roles where rolname='$vt' or rolname='${vt}_uygulama' order by rolname desc limit 1")"
  [ -n "$rol" ] || continue
  for digeri in $PROJELER; do
    hedef="${digeri%%:*}"
    [ "$vt" = "$hedef" ] && continue
    sorgu "$DB_ADI" "select 1 from pg_database where datname='$hedef'" >/dev/null || continue

    yetki="$(sorgu "$DB_ADI" "select has_database_privilege('$rol','$hedef','CONNECT')")"
    if [ "$yetki" = "f" ]; then
      basarili "$rol → $hedef veritabanına bağlanamıyor"
    else
      basarisiz "$rol → $hedef veritabanına BAĞLANABİLİYOR (revoke connect ... from public atlanmış)"
    fi
  done
done

# --- 3. Bağlantı bütçesi --------------------------------------------------------------------
baslik "3. Bağlantı bütçesi"

maxc="$(sorgu "$DB_ADI" "select setting from pg_settings where name='max_connections'")"
rezerv="$(sorgu "$DB_ADI" "select setting from pg_settings where name='superuser_reserved_connections'")"
kullanilabilir=$(( ${maxc:-0} - ${rezerv:-0} ))
gereken=$(( BEYAN_EDILEN_HAVUZ + BAKIM_YEDEGI ))

bilgi "max_connections=$maxc, superuser'a ayrılan=$rezerv → kullanılabilir=$kullanilabilir"
bilgi "beyan edilen havuzlar=$BEYAN_EDILEN_HAVUZ + bakım yedeği=$BAKIM_YEDEGI → gereken=$gereken"

if [ "$gereken" -le "$kullanilabilir" ]; then
  basarili "Bütçe uygun — $(( kullanilabilir - gereken )) bağlantı boşta (~$(( (kullanilabilir - gereken) / 10 )) proje daha)"
else
  basarisiz "Bütçe AŞILMIŞ: $gereken > $kullanilabilir — bir servis diğerlerini düşürebilir"
fi

# Anlık kullanım. Rol bazında EŞİK KOYULMUYOR: veritabanı sahibi rolü aynı zamanda bakım
# bağlantılarını da taşır (psql, yedek.sh'ın pg_dump'ı, bu betiğin kendisi), dolayısıyla
# "havuz sınırını aştı" gibi görünen bir sayı çoğu zaman yanlış alarmdır.
# Anlamlı olan tek eşik TOPLAMIN kullanılabilir sınıra yaklaşması.
baslik "3b. Anlık bağlantı kullanımı"
toplam=0
while IFS='|' read -r rol adet; do
  [ -n "$rol" ] || continue
  bilgi "$rol: $adet açık bağlantı"
  toplam=$(( toplam + adet ))
done <<EOF
$(sorgu "$DB_ADI" "select usename, count(*) from pg_stat_activity where usename is not null group by usename")
EOF

esik=$(( kullanilabilir * 80 / 100 ))
if [ "$toplam" -ge "$esik" ]; then
  basarisiz "Toplam $toplam bağlantı, kullanılabilir sınırın %80'ini (${esik}) aştı"
else
  basarili "Toplam $toplam / $kullanilabilir bağlantı"
fi

# --- 4. Sürüm etiketleri --------------------------------------------------------------------
# Hareketli etiket (`dev`, `latest`) üstüne yazılır; bozuk bir sürüm kurulduğunda bir öncekinin
# ADI KALMAZ ve geri dönüş imkânsızlaşır.
baslik "4. Sürüm etiketleri"

for ucla in $PROJELER; do
  kapsayici="$(echo "$ucla" | cut -d: -f2)"
  imaj="$(docker inspect "$kapsayici" --format '{{.Config.Image}}' 2>/dev/null || true)"
  if [ -z "$imaj" ]; then
    basarisiz "$kapsayici çalışmıyor"
    continue
  fi
  etiket="${imaj##*:}"
  case "$etiket" in
    dev|latest|"$imaj")
      basarisiz "$kapsayici → $imaj (HAREKETLİ etiket — geri dönüş yapılamaz)" ;;
    *)
      basarili "$kapsayici → $imaj" ;;
  esac
done

# Veritabanı olmayan kapsayıcılar da sürümlenir; denetlenmezse bir `:dev` etiketi üretime
# sızabilir ve o servis için geri dönüş imkânı kalmaz.
for kapsayici in $EK_KAPSAYICILAR; do
  imaj="$(docker inspect "$kapsayici" --format '{{.Config.Image}}' 2>/dev/null || true)"
  if [ -z "$imaj" ]; then
    basarisiz "$kapsayici çalışmıyor"
    continue
  fi
  etiket="${imaj##*:}"
  case "$etiket" in
    dev|latest|"$imaj")
      basarisiz "$kapsayici → $imaj (HAREKETLİ etiket — geri dönüş yapılamaz)" ;;
    *)
      basarili "$kapsayici → $imaj" ;;
  esac
done

# --- 5. Sağlık uçları -----------------------------------------------------------------------
baslik "5. Sağlık uçları"

for ucla in $PROJELER; do
  ad="${ucla%%:*}"
  port="${ucla##*:}"
  yanit="$(curl -fsS --max-time 5 --noproxy '*' "http://localhost:${port}/api/saglik" 2>/dev/null || true)"
  if [ -z "$yanit" ]; then
    basarisiz "$ad (:$port) sağlık ucu yanıt vermiyor"
    continue
  fi
  surum="$(printf '%s' "$yanit" | sed -n 's/.*"surum":"\([^"]*\)".*/\1/p')"
  if [ -z "$surum" ] || [ "$surum" = "bilinmiyor" ]; then
    basarisiz "$ad (:$port) ayakta ama SÜRÜM bildirmiyor — çalışan sürüm dışarıdan doğrulanamaz"
  else
    basarili "$ad (:$port) ayakta — sürüm $surum"
  fi
done

# --- 6. Reverse proxy ve port yüzeyi --------------------------------------------------------
# Proxy tam da bir MAKİNE-çapı sınırdır: tek bir proje "domain adımla erişilebiliyor muyum" ya da
# "portum yanlışlıkla dışarı açık mı" sorusunu göremez — yalnızca makinenin tamamına bakınca görünür.
baslik "6. Reverse proxy ve port yüzeyi"

# 6a. Proxy ayakta ve Host başlığına göre yönlendiriyor mu.
#     HTTPS (443) üzerinden — sertifika GERÇEK isme göre doğrulanır (--resolve; -k YOK, yani
#     zincir/anahtar bozuksa burada yakalanır). Ayrıca http→https yönlendirmesi de sınanır.
for ad in hakedis rapor data; do
  kod="$(curl -fsS -o /dev/null -w '%{http_code}' --max-time 5 --noproxy '*' "${_ca_arg[@]}" \
    --resolve "${ad}.${DOMAIN}:443:127.0.0.1" "https://${ad}.${DOMAIN}/api/saglik" 2>/dev/null || true)"
  if [ "$kod" = "200" ]; then
    basarili "proxy → ${ad}.${DOMAIN} (HTTPS) yönlendiriyor (200)"
  else
    basarisiz "proxy → ${ad}.${DOMAIN} (HTTPS) yanıt vermiyor (kod: ${kod:-yok}) — Caddy/DOMAIN/sertifika/servis adı?"
  fi
  rkod="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 --noproxy '*' \
    --resolve "${ad}.${DOMAIN}:80:127.0.0.1" "http://${ad}.${DOMAIN}/api/saglik" 2>/dev/null || true)"
  case "$rkod" in
    30*) basarili "proxy → ${ad}.${DOMAIN} http→https yönlendiriyor ($rkod)" ;;
    *)   basarisiz "proxy → ${ad}.${DOMAIN} http→https yönlendirmesi yok (kod: ${rkod:-yok})" ;;
  esac
done

# 6a-2. Yola göre ayrımın PANEL dalı. Yukarıdaki döngü yalnızca /api/saglik'i, yani BACKEND
#       dalını kanıtlıyor. Panel dalı sınanmazsa "@arka matcher'ı her şeyi yuttu" ya da "panel
#       handle bloğu erişilemez" arızası hiçbir kontrolden düşmez — kullanıcı boş sayfa görür.
if docker inspect tti-data-panel >/dev/null 2>&1; then
  kod="$(curl -fsS -o /dev/null -w '%{http_code}' --max-time 5 --noproxy '*' "${_ca_arg[@]}" \
    --resolve "data.${DOMAIN}:443:127.0.0.1" "https://data.${DOMAIN}/login" 2>/dev/null || true)"
  if [ "$kod" = "200" ]; then
    basarili "proxy → data.${DOMAIN}/login panel'e gidiyor (200)"
  else
    basarisiz "proxy → data.${DOMAIN}/login panel'e gitmiyor (kod: ${kod:-yok}) — Caddy yol ayrımı?"
  fi
fi

# 6b. App portları dışarı SIZMAMALI — yalnızca 127.0.0.1'de yayımlanmalı.
#
# ⚠️ Kontrol .env'e DEĞİL, çalışan konteynerlerin GERÇEK bağlarına bakar. .env yalnızca "ne
# olması gerektiğini" söyler; bir konteyner elle ya da başka bir compose projesinden farklı
# yayımlanmış olabilir ve tam da o durumda sızıntı olur. Yapılandırmaya bakan bir kontrol
# böyle bir sızıntıyı GÖREMEZ. (Bir kez yaşandı: geliştirme makinesinde eski bir yığın
# 8081/8082'yi 0.0.0.0'a açmıştı, .env ise başka portlar diyordu.)
for kapsayici in $(for u in $PROJELER; do echo "$u" | cut -d: -f2; done) $EK_KAPSAYICILAR; do
  baglar="$(docker inspect "$kapsayici" \
    --format '{{range $p, $c := .NetworkSettings.Ports}}{{range $c}}{{.HostIp}}:{{.HostPort}} {{end}}{{end}}' \
    2>/dev/null || true)"
  if [ -z "$baglar" ]; then
    bilgi "$kapsayici çalışmıyor ya da host'a port yayımlamıyor — atlandı"
    continue
  fi
  for bag in $baglar; do
    hostip="${bag%:*}"
    hostport="${bag##*:}"
    case "$hostip" in
      127.0.0.1|localhost)
        basarili "$kapsayici :$hostport yalnızca localhost'ta" ;;
      *)
        basarisiz "$kapsayici :$hostport DIŞARI AÇIK ($hostip) — 127.0.0.1'e bağlanmalı, erişim proxy üzerinden olmalı" ;;
    esac
  done
done

# --- Sonuç ----------------------------------------------------------------------------------
baslik "Sonuç"
if [ "$hata_sayisi" -eq 0 ]; then
  printf '\033[32mTüm platform kontrolleri geçti.\033[0m\n'
  exit 0
fi
printf '\033[31m%d kontrol başarısız.\033[0m\n' "$hata_sayisi"
exit 1
