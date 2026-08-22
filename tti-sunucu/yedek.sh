#!/bin/bash
# Platform günlük yedeği — MAKİNEDEKİ TÜM PROJELER.
#
#   bash yedek.sh            # yedek al  (systemd timer bunu çağırır)
#   bash yedek.sh dogrula    # son yedeği denetle, HİÇBİR ŞEY yazmaz
#
# ⚠️ Bu betik bir PROJEYE değil MAKİNEYE aittir; bu yüzden `tti-sunucu` deposunda yaşar.
#    Daha önce `tti-hakedis/deploy/yedek.sh` yolundaydı ve TTI'nin deposu başka bir projenin verisini
#    yedeklemekten sorumlu görünüyordu — projeler birbirinin işletim yükünü taşımamalı.
#
# Ne yedeklenir:
#   1. Her projenin VERİTABANI (VERITABANLARI listesi) — yoksa ATLANDI, genel sonuç bozulmaz
#   2. Roller (pg_dumpall --globals-only) — veritabanı dump'ı rolleri İÇERMEZ; sıfır makineye
#      geri yüklerken "role does not exist" ile durulur ve parolası kimsede olmaz
#   3. Her projenin YÜKLEME KÖKÜ (YUKLEMELER listesi) — ASIL VERİ, yeniden üretilemez
#   4. /srv/kod/tti-sunucu/{docker-compose.yml,.env} — hiçbir depoda yok, tek kopya
#
# Ne yedeklenmez:
#   TTI'nin ARSIV_KOK klasörü — üretilen PDF/Excel'in tek yönlü aynasıdır, veritabanından
#   yeniden üretilir (tti-hakedis/docs/uretim-notlari.md kararı). Yedeklemek aynı veriyi iki kez
#   saklamak olurdu.
#
# YENİ PROJE EKLERKEN: aşağıdaki VERITABANLARI ve YUKLEMELER listelerine birer satır ekleyin.
# (YENI-PROJE.md, katılım sözleşmesinin dört adımından biri olarak bunu şart koşar.)
#
# ⚠️ Belgelerdeki tek satırlık `pg_dump > dosya` kalıbının İKİ sessiz kusuru vardır; bu betiğin
#    var oluş nedeni onları kapatmaktır:
#
#    1. Kabuğun `>` yönlendirmesi dosyayı komut ÇALIŞMADAN ÖNCE yaratır. `pg_dump` düşse bile
#       diskte bugünün tarihini taşıyan 0 baytlık bir `.dump` kalır ve "yedek var" sanılır.
#       Burada her çıktı önce `.gecici/`'ye yazılır ve YALNIZCA doğrulamayı geçerse asıl adına
#       taşınır. Başarısızlıkta dünün iyi yedeği yerinde kalır.
#
#    2. `docker exec -t` TTY ayırır; satır disiplini `\n`'i `\r\n` yapar ve `-Fc` ikili arşivi
#       SESSİZCE bozulur — ancak geri yükleme günü anlaşılır. Bu yüzden `-t` HİÇ kullanılmaz.
#       İnce ayrım: `docker exec` için doğru olan `-t` YOKLUĞU, `docker compose exec` için ise
#       `-T` VARLIĞI'dır (compose varsayılan olarak TTY ayırır). Biri diğerine bakıp düzeltmesin.
#
# Parola taşımaz: `docker exec` komutu konteynerin İÇİNDE koşar, oradaki `pg_hba.conf` unix
# soketi için `trust`tır. Dışarıdan gelen bağlantılar (uygulama dahil) parola ister — orada
# gevşeme yok. Sonuç: parola `ps` çıktısında, journald'da ve systemd biriminde hiç görünmez.
#
# macOS'ta da koşar (geliştirme makinesinde test için): GNU'ya özel bayraklar bilinçli olarak
# kullanılmadı — `df -Pk`, `wc -c`, `sort -r | awk` ve `mkdir` kilidi her iki platformda aynı.

set -euo pipefail

# --- Ayarlar — hepsi ortam değişkeniyle ezilebilir (geliştirme makinesinde test için şart) ---
YEDEK_KOK="${YEDEK_KOK:-/srv/veri/yedek}"
DB_KAPSAYICI="${DB_KAPSAYICI:-tti-db}"
# `pg_isready` ve katalog sorguları için başvurulan veritabanı — her zaman var olan biri.
DB_ADI="${DB_ADI:-tti}"

# "veritabanı:sahip" çiftleri. Her veritabanı KENDİ SAHİBİYLE dökülür: sahibin okuma yetkisi
# tanım gereği vardır, dolayısıyla `grant` bakımı hiç gerekmez ve yetki hatası doğmaz.
#
# Şema değil VERİTABANI dökülüyor (`pg_dump -d`, `-n` yok). Fark geri yükleme gününde ortaya
# çıkar: bir veritabanını `dropdb` + `pg_restore` ile bağımsız geri alabilirsiniz; bir şemayı
# geri yüklemek ise onu DİĞER PROJELERİN canlı verisiyle paylaşan bir veritabanına yazmak demek.
VERITABANLARI="${VERITABANLARI:-tti:tti_hakedis rapor:rapor data:data}"

# "proje:konteyner" çiftleri — kullanıcının YÜKLEDİĞİ dosyaların kökü konteynerin `/yukleme`
# bağlamasından okunur. Her proje kendi klasörüne yedeklenir: yukleme/<proje>/<tarih>/
YUKLEMELER="${YUKLEMELER:-tti:tti-hakedis-server rapor:tti-rapor-server data:tti-data-server}"

SUNUCU_AYAR_DIZINI="${SUNUCU_AYAR_DIZINI:-/srv/kod/tti-sunucu}"
GUN_DB="${GUN_DB:-14}"
GUN_YUKLEME="${GUN_YUKLEME:-7}"
AYLIK_SAKLA="${AYLIK_SAKLA:-3}"
AYLIK_GUN_DB="${AYLIK_GUN_DB:-400}"
ASGARI_BOS_MB="${ASGARI_BOS_MB:-5000}"
HAZIR_BEKLE_SN="${HAZIR_BEKLE_SN:-180}"
KILIT_BAYAT_SN="${KILIT_BAYAT_SN:-21600}"

TARIH="$(date +%F)"
BASLANGIC="$(date +%s)"
HATA_KODU=0
KILIT="$YEDEK_KOK/.kilit"
GECICI="$YEDEK_KOK/.gecici"
DURUM_DOSYASI=""

# --- Log — sabit önekler journald'da grep'lenebilsin diye ---
log()    { printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
adim()   { log "ADIM   $*"; }
tamam()  { log "TAMAM  $*"; satir_yaz "TAMAM   $*"; }
atlandi(){ log "ATLANDI $*"; satir_yaz "ATLANDI $*"; }
uyari()  { log "UYARI  $*"; satir_yaz "UYARI   $*"; }
hata()   { log "HATA   $*" >&2; satir_yaz "HATA    $*"; }

# Durum tablosu bellekte dizide değil dosyada tutuluyor: macOS'ta bash 3.2 var ve `set -u` altında
# boş diziye erişmek hata veriyor. Dosya iki platformda da aynı davranıyor.
satir_yaz() { [ -n "$DURUM_DOSYASI" ] && printf '%s\n' "$*" >> "$DURUM_DOSYASI" || true; }

# İlk hatanın kodu tutulur; sonraki adımlar yine de koşar (bir projenin sorunu, diğer projelerin
# asıl verisini yedeksiz bırakamaz).
hata_kaydet() { if [ "$HATA_KODU" -eq 0 ]; then HATA_KODU="$1"; fi; }

# NOT: `yolu_coz` ve `env_oku` yardımcıları kaldırıldı. Yükleme kökleri artık YALNIZCA
# `docker inspect` ile konteynerin gerçek bağlamasından okunuyor; `.env`'e ikinci bir kaynak
# olarak bakmak, yanlış ya da boş bir klasörü "yedeklenmiş" saymaya açık kapı bırakıyordu.
# Konteyner çalışmıyorsa yedek ATLANDI der — sessizce boş klasör yedeklemez.

temizle() {
  rm -rf "$GECICI" 2>/dev/null || true
  rmdir "$KILIT" 2>/dev/null || true
}

# ==============================================================================================
# dogrula — salt okunur. Üretimde her an güvenle çalıştırılabilir.
# ==============================================================================================
dogrula_calistir() {
  local sonuc=0 simdi yas
  simdi="$(date +%s)"
  echo "TTI yedek denetimi — $YEDEK_KOK"
  echo

  if [ ! -d "$YEDEK_KOK" ]; then
    echo "✘ Yedek kökü yok: $YEDEK_KOK"
    return 7
  fi

  # 1 — Son çalışmanın damgası. Yedeğin klasik ölüm şekli sessizce bir daha hiç koşmamaktır;
  #     bu dosyanın TARİHİ ona karşı en ucuz sigortadır.
  if [ -f "$YEDEK_KOK/SON-DURUM.txt" ]; then
    local ilk
    ilk="$(head -1 "$YEDEK_KOK/SON-DURUM.txt")"
    yas=$(( (simdi - $(dosya_zamani "$YEDEK_KOK/SON-DURUM.txt")) / 3600 ))
    case "$ilk" in
      TAMAM*) echo "✔ Son çalışma: $ilk (${yas} saat önce)" ;;
      UYARI*) echo "⚠ Son çalışma uyarılı: $ilk (${yas} saat önce)" ;;
      *)      echo "✘ Son çalışma başarısız: $ilk (${yas} saat önce)"; sonuc=7 ;;
    esac
    if [ "$yas" -gt 36 ]; then
      echo "✘ Son yedek 36 saatten eski — timer koşmuyor olabilir."
      sonuc=7
    fi
  else
    echo "✘ SON-DURUM.txt yok — betik hiç çalışmamış."
    sonuc=7
  fi

  # 2/3 — Veritabanı dump'ları: boyut, gerçekten geçerli arşiv mi, yaşı.
  local cift vt
  for cift in $VERITABANLARI; do
    vt="${cift%%:*}"
    local son
    son="$(en_yeni "$YEDEK_KOK/db" "${vt}-*.dump")"
    if [ -z "$son" ]; then
      if grep -q "ATLANDI.*${vt}" "$YEDEK_KOK/SON-DURUM.txt" 2>/dev/null; then
        echo "· $vt: yedek yok ama ATLANDI olarak kayıtlı (proje kurulu değil) — sorun değil"
      else
        echo "✘ $vt: hiç dump yok"
        sonuc=7
      fi
      continue
    fi
    local boyut girdi
    boyut="$(wc -c < "$son" | tr -d ' ')"
    girdi="$(dump_girdi_sayisi "$son")"
    yas=$(( (simdi - $(dosya_zamani "$son")) / 3600 ))
    if ! dump_saglam "$son"; then
      echo "✘ $vt: $(basename "$son") BOZUK (${boyut} bayt, ${girdi} girdi)"
      sonuc=7
    elif [ "$yas" -gt 36 ]; then
      echo "✘ $vt: $(basename "$son") geçerli ama ${yas} saat eski"
      sonuc=7
    else
      echo "✔ $vt: $(basename "$son") — ${boyut} bayt, ${girdi} girdi, ${yas} saat önce"
    fi
  done

  # 4 — Yükleme anlık görüntüleri (proje başına)
  local proje goruntu tti_goruntu=""
  for cift in $YUKLEMELER; do
    proje="${cift%%:*}"
    goruntu="$(en_yeni_dizin "$YEDEK_KOK/yukleme/$proje")"
    if [ -z "$goruntu" ]; then
      echo "✘ Yükleme ($proje): anlık görüntü yok"
      sonuc=7
      continue
    fi
    yas=$(( (simdi - $(dosya_zamani "$goruntu")) / 3600 ))
    local adet
    adet="$(find "$goruntu" -type f 2>/dev/null | wc -l | tr -d ' ')"
    if [ "$yas" -gt 36 ]; then
      echo "✘ Yükleme ($proje): $(basename "$goruntu") — ${adet} dosya ama ${yas} saat eski"
      sonuc=7
    else
      echo "✔ Yükleme ($proje): $(basename "$goruntu") — ${adet} dosya, ${yas} saat önce"
    fi
    [ "$proje" = "tti" ] && tti_goruntu="$goruntu"
  done

  # Aşağıdaki çapraz kontrol TTI'ye ÖZGÜDÜR (`hakedis.ek` tablosunu okur). Her projenin kendi
  # veri modeli farklı olduğu için genelleştirilmedi; yeni bir proje benzer bir kontrol isterse
  # kendi betiğine yazar.
  if [ -n "$tti_goruntu" ]; then
    goruntu="$tti_goruntu"

    # 5 — ÇAPRAZ KONTROL: veritabanındaki her ek dosyası anlık görüntüde gerçekten var mı ve
    #     boyutu tutuyor mu? Sessiz veri kaybını yakalayan asıl kontrol budur — dosya sayısı
    #     doğru görünürken yanlış dosyaların yedeklenmiş olması mümkündür.
    # ⚠️ Boru hattı DEĞİL heredoc: `... | while` alt kabuk açar ve `eksik` sayacı dışarı çıkmaz,
    #    kontrol her zaman "temiz" görünürdü.
    local eksik=0 yol beklenen gercek
    while IFS='|' read -r yol beklenen; do
      [ -n "$yol" ] || continue
      if [ ! -f "$goruntu/$yol" ]; then
        [ "$eksik" -lt 10 ] && echo "  ✘ eksik: $yol"
        eksik=$((eksik + 1))
      else
        gercek="$(wc -c < "$goruntu/$yol" | tr -d ' ')"
        if [ "$gercek" != "$beklenen" ]; then
          [ "$eksik" -lt 10 ] && echo "  ✘ boyut tutmuyor: $yol ($gercek ≠ $beklenen)"
          eksik=$((eksik + 1))
        fi
      fi
    done <<EOF
$(docker exec "$DB_KAPSAYICI" psql -U tti_hakedis -d "$DB_ADI" -tA -F'|' \
    -c "select depo_yolu, boyut_bayt from hakedis.ek order by id" 2>/dev/null || true)
EOF
    if [ "$eksik" -gt 0 ]; then
      echo "  ✘ $eksik ek dosyası anlık görüntüde eksik ya da bozuk"
      sonuc=7
    else
      echo "  ✔ veritabanındaki tüm ek dosyaları anlık görüntüde ve boyutları tutuyor"
    fi
  fi

  # 6 — Disk
  local bos
  bos="$(bos_alan_mb "$YEDEK_KOK")"
  if [ "$bos" -lt "$ASGARI_BOS_MB" ]; then
    echo "✘ Boş disk ${bos} MB — asgari ${ASGARI_BOS_MB} MB"
    sonuc=7
  else
    echo "✔ Boş disk: ${bos} MB"
  fi

  echo
  if [ "$sonuc" -eq 0 ]; then
    echo "SONUÇ: yedek güvenilir"
  else
    echo "SONUÇ: ⚠ YEDEK GÜVENİLİR DEĞİL — yukarıdaki ✘ satırlarına bakın"
  fi
  return "$sonuc"
}

# --- Taşınabilir yardımcılar (GNU'ya özel bayrak yok) ---
dosya_zamani() { # değişiklik zamanı, unix saniye
  if stat -f%m "$1" >/dev/null 2>&1; then stat -f%m "$1"; else stat -c%Y "$1" 2>/dev/null || echo 0; fi
}
bos_alan_mb() { df -Pk "$1" | awk 'NR==2 {print int($4/1024)}'; }

# ⚠️ `ls` eşleşme bulamazsa 1 döner ve `set -o pipefail` bunu tüm boru hattına taşır; `set -e`
#    ile birlikte betiği OLDURUR. "Henüz hiç yedek yok" tamamen normal bir durum olduğu için
#    burada bastırılıyor. (Çağıran `|| true` içindeyse hata görünmez, doğrudan çağrıldığında
#    görünür — bu yüzden kaynağında kapatmak tek tutarlı yol.)
en_yeni() { # <dizin> <glob> → en yeni dosyanın yolu (ad sıralaması = tarih sıralaması)
  { ls -1 "$1"/$2 2>/dev/null || true; } | sort -r | head -1
}
en_yeni_dizin() { { ls -1d "$1"/20* 2>/dev/null || true; } | sort -r | head -1; }

# Bir dump dosyası gerçekten sağlam mı? İki ayrı bozulma biçimi var ve TEK bir kontrol ikisini
# birden yakalamıyor:
#   · `--list` YALNIZCA arşivin başındaki içindekiler tablosunu okur. `docker exec -t`'nin soktuğu
#     \r'yi yakalar, ama SONDAN kırpılmış bir dosyayı "kabul" eder — TOC yerindedir.
#   · `-f /dev/null` arşivin TAMAMINI çözer, dolayısıyla yarım yazmayı da yakalar.
# Ölçüldü: 178 KB'lık dump için tam okuma 0,09 sn — maliyeti yok sayılır.
dump_saglam() {
  local dosya="$1"
  [ "$(wc -c < "$dosya" | tr -d ' ')" -ge 1024 ] || return 1
  docker exec -i "$DB_KAPSAYICI" pg_restore --list  < "$dosya" >/dev/null 2>&1 || return 1
  docker exec -i "$DB_KAPSAYICI" pg_restore -f /dev/null < "$dosya" >/dev/null 2>&1 || return 1
  return 0
}
dump_girdi_sayisi() {
  { docker exec -i "$DB_KAPSAYICI" pg_restore --list < "$1" 2>/dev/null || true; } | grep -cv '^;' || true
}
gunluk_dizinler() { { ls -1d "$1"/20* 2>/dev/null || true; } | sort -r; }
aylik_dizinler() { { ls -1d "$1"/20*-01 2>/dev/null || true; } | sort -r; }

# ==============================================================================================
# Yedek alma
# ==============================================================================================
yedek_al() {
  # --- 1. Ön koşullar ---
  mkdir -p "$YEDEK_KOK" 2>/dev/null || { echo "HATA yedek kökü oluşturulamadı: $YEDEK_KOK" >&2; exit 1; }
  chmod 750 "$YEDEK_KOK" 2>/dev/null || true

  # Kilit — iki çalışma aynı geçici dosyaya yazarsa ikisi de bozulur. `flock` macOS'ta yok;
  # `mkdir` iki platformda da atomik.
  if ! mkdir "$KILIT" 2>/dev/null; then
    local kilit_yas
    kilit_yas=$(( $(date +%s) - $(dosya_zamani "$KILIT" 2>/dev/null || echo 0) ))
    if [ "$kilit_yas" -gt "$KILIT_BAYAT_SN" ]; then
      log "UYARI  bayat kilit devralınıyor (${kilit_yas} sn)"
      rmdir "$KILIT" 2>/dev/null || true
      mkdir "$KILIT" 2>/dev/null || true
    else
      echo "HATA başka bir yedek çalışması sürüyor (kilit: $KILIT)" >&2
      exit 6
    fi
  fi
  trap temizle EXIT

  mkdir -p "$YEDEK_KOK/db" "$YEDEK_KOK/yukleme" "$YEDEK_KOK/roller" \
           "$YEDEK_KOK/sunucu-ayarlari" "$GECICI"
  DURUM_DOSYASI="$GECICI/durum.txt"
  : > "$DURUM_DOSYASI"

  log "BASLADI TTI yedek — $TARIH"
  log "        yedek kökü : $YEDEK_KOK"

  if ! command -v docker >/dev/null 2>&1; then
    hata "docker bulunamadı"; exit 1
  fi
  if ! command -v rsync >/dev/null 2>&1; then
    hata "rsync bulunamadı — kurun: dnf install -y rsync"; exit 1
  fi

  # Yükleme kaynakları konteynerin GERÇEKTEN kullandığı host yolundan okunur (`docker inspect`).
  # `.env`'e bakmak, yanlış ya da boş bir klasörü yedeklemeye açık kapı bırakırdı.
  local cift proje kapsayici kaynak
  for cift in $YUKLEMELER; do
    proje="${cift%%:*}"
    kapsayici="${cift##*:}"
    kaynak="$(yukleme_kaynagi_bul "$kapsayici")"
    log "        yükleme ($proje) : ${kaynak:-BULUNAMADI}"
  done

  # --- 2. Disk ---
  local bos
  bos="$(bos_alan_mb "$YEDEK_KOK")"
  if [ "$bos" -lt "$ASGARI_BOS_MB" ]; then
    hata "boş disk ${bos} MB, asgari ${ASGARI_BOS_MB} MB — HİÇBİR ŞEY yazılmadı/silinmedi"
    durum_yaz "HATA"
    exit 4
  fi

  # --- 3. Veritabanının hazır olmasını bekle ---
  # Reboot sonrası `Persistent=true` yedeği HEMEN ateşler; docker daemon kalkmış olsa bile
  # konteyner + crash recovery 10-60 sn sürebilir. Bu döngü o yarışı kazanır.
  local bekledi=0
  if ! docker exec "$DB_KAPSAYICI" pg_isready -U tti_hakedis -d "$DB_ADI" -q >/dev/null 2>&1; then
    adim "veritabanı bekleniyor (en çok ${HAZIR_BEKLE_SN} sn)"
    while [ "$bekledi" -lt "$HAZIR_BEKLE_SN" ]; do
      sleep 5
      bekledi=$((bekledi + 5))
      if docker exec "$DB_KAPSAYICI" pg_isready -U tti_hakedis -d "$DB_ADI" -q >/dev/null 2>&1; then
        break
      fi
    done
    if ! docker exec "$DB_KAPSAYICI" pg_isready -U tti_hakedis -d "$DB_ADI" -q >/dev/null 2>&1; then
      hata "veritabanı ${HAZIR_BEKLE_SN} sn içinde hazır olmadı (konteyner: $(docker inspect -f '{{.State.Status}}' "$DB_KAPSAYICI" 2>/dev/null || echo 'yok'))"
      durum_yaz "HATA"
      exit 2
    fi
    log "        veritabanı ${bekledi} sn sonra hazır"
  fi

  # --- 4. Veritabanı dumpları ---
  local sahip
  for cift in $VERITABANLARI; do
    veritabani_dump "${cift%%:*}" "${cift##*:}" || true
  done

  # --- 5. Roller ---
  roller_dump || true

  # --- 6. Makineye özel ayarlar ---
  sunucu_ayarlari || true

  # --- 7. Yükleme anlık görüntüleri (proje başına) ---
  for cift in $YUKLEMELER; do
    proje="${cift%%:*}"
    kapsayici="${cift##*:}"
    kaynak="$(yukleme_kaynagi_bul "$kapsayici")"
    if [ -z "$kaynak" ]; then
      atlandi "$proje yükleme kökü bulunamadı (konteyner çalışmıyor olabilir)"
      continue
    fi
    yukleme_goruntusu "$proje" "$kaynak" || true
  done

  # --- 8. Rotasyon — YALNIZCA başarılı adımlardan sonra ---
  rotasyon

  # --- 9. Kapanış ---
  local sure durum
  sure=$(( $(date +%s) - BASLANGIC ))
  if [ "$HATA_KODU" -eq 0 ]; then
    if grep -q '^UYARI' "$DURUM_DOSYASI"; then durum="UYARI"; else durum="TAMAM"; fi
  else
    durum="HATA"
  fi
  durum_yaz "$durum"
  log "BITTI  durum=$durum sure=${sure}s bos=${bos}MB"
  exit "$HATA_KODU"
}

# --- Adım: bir şemanın dump'ı ---
veritabani_dump() {
  local vt="$1" sahip="$2"
  local gecici="$GECICI/${vt}.dump" stderr="$GECICI/${vt}.err"

  # Bir proje henüz kurulmamış olabilir; olmayan bir proje yüzünden diğerlerinin yedeği düşmemeli.
  local var
  var="$(docker exec "$DB_KAPSAYICI" psql -U tti_hakedis -d "$DB_ADI" -tAc \
      "select 1 from pg_database where datname='$vt'" 2>/dev/null || true)"
  if [ -z "$var" ]; then
    atlandi "$vt veritabanı yok (ilgili proje henüz kurulmamış)"
    return 0
  fi

  adim "$vt veritabanı dökülüyor"
  # `-t` YOK (bkz. başlıktaki 2 numaralı kusur). `--lock-wait-timeout`: uygulama uzun bir işlem
  # tutuyorsa dump sonsuza kadar asılı kalmasın, systemd zaman aşımına sürüklenmesin.
  if ! docker exec "$DB_KAPSAYICI" pg_dump -U "$sahip" -d "$vt" -Fc \
        --lock-wait-timeout=60s > "$gecici" 2>"$stderr"; then
    hata "$vt dump alınamadı — ÖNCEKİ YEDEK KORUNDU"
    sed 's/^/         /' "$stderr" >&2 || true
    rm -f "$gecici"
    hata_kaydet 3
    return 1
  fi

  # Doğrulama, dosya asıl adına taşınmadan ÖNCE. Host'a postgresql-client kurmayı gerektirmez —
  # kontrol konteynerin kendi pg_restore'uyla yapılır.
  local boyut girdi
  boyut="$(wc -c < "$gecici" | tr -d ' ')"
  girdi="$(dump_girdi_sayisi "$gecici")"
  if ! dump_saglam "$gecici"; then
    hata "$vt dump doğrulamayı geçemedi (${boyut} bayt, ${girdi} girdi) — ÖNCEKİ YEDEK KORUNDU"
    rm -f "$gecici"
    hata_kaydet 3
    return 1
  fi

  mv -f "$gecici" "$YEDEK_KOK/db/${vt}-${TARIH}.dump"
  chmod 640 "$YEDEK_KOK/db/${vt}-${TARIH}.dump" 2>/dev/null || true
  tamam "$vt dump — ${boyut} bayt, ${girdi} girdi"
}

roller_dump() {
  local gecici="$GECICI/roller.sql"
  adim "roller dökülüyor"
  if ! docker exec "$DB_KAPSAYICI" pg_dumpall -U tti_hakedis --globals-only > "$gecici" 2>/dev/null; then
    hata "roller alınamadı"
    rm -f "$gecici"; hata_kaydet 3; return 1
  fi
  if ! grep -q 'CREATE ROLE' "$gecici"; then
    hata "roller dosyası CREATE ROLE içermiyor — doğrulama başarısız"
    rm -f "$gecici"; hata_kaydet 3; return 1
  fi
  mv -f "$gecici" "$YEDEK_KOK/roller/roller-${TARIH}.sql"
  # Rol parola özetlerini içerir — açık paylaşıma gönderilmez.
  chmod 600 "$YEDEK_KOK/roller/roller-${TARIH}.sql" 2>/dev/null || true
  tamam "roller"
}

sunucu_ayarlari() {
  if [ ! -d "$SUNUCU_AYAR_DIZINI" ]; then
    atlandi "makineye özel ayar dizini yok ($SUNUCU_AYAR_DIZINI)"
    return 0
  fi
  local gecici="$GECICI/ayar.tar" hedef="$YEDEK_KOK/sunucu-ayarlari/sunucu-ayarlari-${TARIH}.tar"
  adim "makineye özel ayarlar paketleniyor"
  if ! tar cf "$gecici" -C "$SUNUCU_AYAR_DIZINI" . 2>/dev/null; then
    hata "ayar dizini paketlenemedi"; rm -f "$gecici"; hata_kaydet 3; return 1
  fi
  mv -f "$gecici" "$hedef"
  chmod 600 "$hedef" 2>/dev/null || true   # .env parola içerir
  tamam "makineye özel ayarlar"
}

# Konteynerin `/yukleme` bağlamasının host tarafındaki yolu. Boş dönerse konteyner yok demektir.
yukleme_kaynagi_bul() {
  docker inspect "$1" \
    --format '{{range .Mounts}}{{if eq .Destination "/yukleme"}}{{.Source}}{{end}}{{end}}' \
    2>/dev/null || true
}

yukleme_goruntusu() {
  local proje="$1" kaynak="$2"
  if [ ! -d "$kaynak" ]; then
    hata "$proje yükleme kaynağı yok: $kaynak — ASIL VERİ YEDEKLENMEDİ"
    hata_kaydet 5; return 1
  fi

  local kok onceki hedef gecici_dizin
  kok="$YEDEK_KOK/yukleme/$proje"
  mkdir -p "$kok"
  onceki="$(en_yeni_dizin "$kok")"
  hedef="$kok/$TARIH"
  # Geçici dizin NOKTA ile başlar: `20*` glob'una takılmaz, yarım kalan bir çalışma bir sonraki
  # gün "önceki anlık görüntü" sanılmaz.
  gecici_dizin="$kok/.gecici-$TARIH"

  rm -rf "$gecici_dizin"
  adim "$proje yükleme anlık görüntüsü alınıyor"

  local cikis=0
  if [ -n "$onceki" ] && [ "$onceki" != "$hedef" ]; then
    # --link-dest: değişmemiş dosya kopyalanmaz, dünkü kopyaya sabit bağlantı verilir.
    # 7 günlük görüntü ≈ 1 kopya + değişenler. JPEG sıkışmadığı için tar'ın anlamı yoktu.
    rsync -a --link-dest="$onceki" "$kaynak/" "$gecici_dizin/" || cikis=$?
  else
    rsync -a "$kaynak/" "$gecici_dizin/" || cikis=$?
  fi

  # 24 = "kaynak dosya aktarım sırasında kayboldu" — kullanıcı yedek sırasında ek silmiş olabilir.
  if [ "$cikis" -ne 0 ] && [ "$cikis" -ne 24 ]; then
    hata "$proje yükleme anlık görüntüsü alınamadı (rsync $cikis) — ASIL VERİ YEDEKLENMEDİ"
    rm -rf "$gecici_dizin"; hata_kaydet 5; return 1
  fi
  [ "$cikis" -eq 24 ] && uyari "$proje: yedek sırasında bazı dosyalar kayboldu (rsync 24)"

  rm -rf "$hedef"
  mv "$gecici_dizin" "$hedef"
  # ⚠️ `rsync -a` kaynak dizinin zaman damgasını da kopyalar; dokunulmazsa anlık görüntünün yaşı
  #    YEDEĞİN değil KAYNAĞIN yaşını gösterir ve tazelik denetimi anlamsızlaşır. Yalnızca en üst
  #    dizin damgalanıyor — içindeki dosyaların kendi tarihleri gerçek yükleme tarihleridir.
  touch "$hedef"

  local adet
  adet="$(find "$hedef" -type f 2>/dev/null | wc -l | tr -d ' ')"
  tamam "$proje yükleme anlık görüntüsü — ${adet} dosya"
}

# --- Rotasyon — her zaman BAŞARILI yedekten sonra. Ters sıra, "yer açayım" diye son iyi yedeği
#     silip ardından bozuk bir yedek yazmak demektir; yedeklemenin en pahalı hatası budur. ---
rotasyon() {
  adim "rotasyon"

  # Veritabanı dump'ları ve roller: ayın 1'i dosyaları uzun saklanır, diğerleri GUN_DB gün.
  if [ "$HATA_KODU" -ne 3 ]; then
    find "$YEDEK_KOK/db" "$YEDEK_KOK/roller" "$YEDEK_KOK/sunucu-ayarlari" \
         -type f ! -name '*-01.dump' ! -name '*-01.sql' ! -name '*-01.tar' \
         -mtime +"$GUN_DB" -delete 2>/dev/null || true
    find "$YEDEK_KOK/db" "$YEDEK_KOK/roller" "$YEDEK_KOK/sunucu-ayarlari" \
         -type f -mtime +"$AYLIK_GUN_DB" -delete 2>/dev/null || true
  else
    uyari "dump adımı düştü — dump rotasyonu atlandı (dünkü yedek korunuyor)"
  fi

  # Yükleme anlık görüntüleri: PROJE BAŞINA en yeni GUN_YUKLEME günlük + en yeni AYLIK_SAKLA aylık.
  # Rotasyon proje başına yapılır; bir projenin yedeği düştüğünde diğerininki dönmeye devam eder.
  if [ "$HATA_KODU" -ne 5 ]; then
    local cift proje kok d ad
    for cift in $YUKLEMELER; do
      proje="${cift%%:*}"
      kok="$YEDEK_KOK/yukleme/$proje"
      [ -d "$kok" ] || continue
      for d in $(gunluk_dizinler "$kok" | awk -v n="$GUN_YUKLEME" 'NR>n'); do
        ad="$(basename "$d")"
        case "$ad" in *-01) continue ;; esac
        rm -rf "$d"
      done
      for d in $(aylik_dizinler "$kok" | awk -v n="$AYLIK_SAKLA" 'NR>n'); do
        rm -rf "$d"
      done
    done
  else
    uyari "yükleme adımı düştü — görüntü rotasyonu atlandı"
  fi
}

# SON-DURUM.txt — yedek klasörüne bakan biri log komutu bilmeden görsün diye. Atomik yazılır.
durum_yaz() {
  local durum="$1" gecici="$YEDEK_KOK/.SON-DURUM.tmp"
  {
    printf '%s %s\n' "$durum" "$(date '+%Y-%m-%d %H:%M:%S')"
    echo "----------------------------------------"
    [ -n "$DURUM_DOSYASI" ] && [ -f "$DURUM_DOSYASI" ] && cat "$DURUM_DOSYASI"
    echo "----------------------------------------"
    echo "yedek kökü : $YEDEK_KOK"
    echo "boş disk   : $(bos_alan_mb "$YEDEK_KOK") MB"
    echo "çıkış kodu : $HATA_KODU"
  } > "$gecici" 2>/dev/null || true
  mv -f "$gecici" "$YEDEK_KOK/SON-DURUM.txt" 2>/dev/null || true
}

# ==============================================================================================
case "${1:-}" in
  dogrula) dogrula_calistir ;;
  ""|yedek) yedek_al ;;
  *) echo "Kullanım: bash yedek.sh [dogrula]" >&2; exit 1 ;;
esac
