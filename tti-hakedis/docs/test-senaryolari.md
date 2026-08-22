# Elle Test Senaryoları — Faz H + I (tarayıcı)

> **Neden bu dosya var:** `deploy/dogrula-faz-*.sh` betikleri sunucunun ve arayüzün *arkasındaki*
> sözleşmeyi doğruluyor; makinede tarayıcı otomasyonu olmadığı için ekranların kendisi (tıklama,
> çizim, yerleşim) hiç doğrulanmadı. Faz H ve Faz I'nin DoD'lerinde açık kalan tek madde budur ve
> **Faz K'ye geçmeden önce** buradaki maddelerin tamamı işaretlenmelidir.
>
> Adres: **http://localhost:8081/** · Sunucunun ayakta olması yeterli (`docker compose ps`).

| Rol | Kullanıcı | Parola |
|---|---|---|
| Yönetici | `yonetici` | `TTI000000` |
| Bursa sorumlusu | `bursa` | `TTI777777` |
| Ankara sorumlusu | `ankara` | `TTI444444` |
| İzmir sorumlusu | `izmir` | `TTI666666` |

**Başlangıç durumu:** Bursa'da `01` SP numaralı bir iş ve `BURSA_2026_FIZIBILITE_001` belgesi
(4.348,57 ₺) *Satın Alma Onayı Beklemede* durumunda, onay kuyruğunda. Edirne'de iptal edilmiş bir iş
duruyor (iptal görünürlüğünü kontrol etmek için).

⚠️ **Test sırasında `SIFIRLA=1` ile doğrulama betiği koşturmayın** — tüm bölgelerin iş ve belge
kayıtlarını siler.

---

## A. Oturum ve yetki

- [ ] **A1** Çıkış yapmadan `http://localhost:8081/` açıldığında giriş ekranı geliyor; boş alanla
      "Giriş" düğmesi kapalı ve altında neden yazıyor.
- [ ] **A2** Yanlış parola → "Kullanıcı adı veya parola hatalı." (hangisinin yanlış olduğu söylenmiyor).
- [ ] **A3** Girişten sonra sağ üstte ad soyad + bölge kodu görünüyor.
- [ ] **A4** Sayfa yenilendiğinde (⌘R) tekrar giriş istemiyor — oturum sürüyor.
- [ ] **A5** "Çıkış" → giriş ekranına dönüyor. Geri tuşu oturumu **geri getirmiyor** (hash tabanlı
      yönlendirmede geri tuşu yalnızca adres çubuğundaki hash'i değiştirir; oturum düştüğü için ekran
      giriş formunda kalır — doğru davranış budur).
- [ ] **A6** `bursa` ile girişte üst şeritte **yalnızca** "Belgeler" ve "İşler" var; "Onay Kuyruğu"
      ve "Kullanıcılar" **yok**.
- [ ] **A7** `yonetici` ile girişte dört sekme de var.
- [ ] **A8** ⭐ `bursa` oturumundayken adres çubuğuna elle `#/kullanicilar` yazıldığında **yetki
      hatası** görünüyor — *"Yükleniyor…"da takılı kalmıyor* (31 Temmuz düzeltmesi). Yetki sunucuda;
      menüyü gizlemek yalnızca kolaylık.

## B. Bölge yalıtımı

- [ ] **B1** `ankara` ile giriş → Belgeler listesinde **hiçbir** Bursa/Edirne kaydı yok.
- [ ] **B2** `ankara` oturumundayken adres çubuğuna elle bir Bursa belgesinin adresini yazın
      (`#/belgeler/<bursa_belge_id>`) → "Belge bulunamadı" (404), veri sızmıyor.
- [ ] **B3** `yonetici` ile aynı listede tüm bölgeler görünüyor ve **Bölge** sütunu var.
- [ ] **B4** `bursa` oturumunda Bölge süzgeci **görünmüyor** (seçilecek tek bölge var).

## C0. İş listesi süzgeçleri

- [ ] **C0.1** İşler ekranında **İş durumu** süzgeci var: Tümü / Aktif / İptal edilmiş.
- [ ] **C0.2** "İptal edilmiş" seçilince yalnızca iptal edilmiş işler kalıyor.
- [ ] **C0.3** Tabloda **Bölge** sütunu var.
- [ ] **C0.4** `yonetici` ile **Bölge süzgeci** de görünüyor ve çalışıyor; `bursa` ile görünmüyor.
- [ ] **C0.5** ⭐ Belgeler sütununda her rozetin yanında **tür** yazıyor
      (`Ön Fizibilite · İptal Edildi`), aynı türden iki kayıt artık ayırt edilebiliyor.

## C. Yeni iş (Ekran 1)

- [ ] **C1** "Yeni İş" → Bölge alanı bölge sorumlusunda **salt okunur** ve kendi bölgesi yazılı.
- [ ] **C2** ⭐ **Kable listesi:** kutuya tıklayınca (hiç yazmadan) bölgenin tüm kableleri listeleniyor
      ve üstünde kaç kayıt olduğu yazıyor; yazmaya başlayınca liste anında süzülüyor.
      Bölge başına beklenen sayı: İst-Avrupa 309 · İst-Anadolu 272 · Ankara 86 · İzmir 86 ·
      Edirne 83 · Bursa 74 · Kütahya-Şırnak 33.
- [ ] **C3** Listeden bir kable seçilince mavi bilgi şeridinde **Uç A / Uç B adresleri** görünüyor.
- [ ] **C4** Zorunlu alanlardan biri boşken "İşi Oluştur" **kapalı** ve altında neden yazıyor.
- [ ] **C5** **Aynı SP No ile ikinci iş**: aynı SP'yi (`01`) tekrar kullanıp farklı bir kable seçin →
      iş **oluşuyor** (engellenmiyor) ve sonuç ekranında sarı uyarı şeridi:
      *"Bu bölgede bu SP No ile … iş daha var."*
- [ ] **C6** **Aynı kable ile ikinci iş**: aynı kableyi tekrar seçin → `AYNI_KABLE_VAR` uyarısı,
      "Ek Aralığı ve Route alanlarının doğru olduğundan emin olun" diyor.
- [ ] **C7** Uyarı yokken "İşi Oluştur" doğrudan iş detayına gidiyor (ara ekran görünmüyor).

## D. Poz tablosu ve para gösterimi

- [ ] **D1** Fizibilite belgesi açın → mavi şerit: *"Birim fiyatlar … salt okunurdur; yalnızca miktar
      girilir."*
- [ ] **D2** **Grup satırları** (`1 — HAVAİ GÜZERGAH İMALATLARI`) dolgulu, kalın ve **giriş alanı yok**.
- [ ] **D3** Br Fiyat sütunu **yuvarlanmamış** görünüyor (`371,86716779978428 ₺`).
- [ ] **D4** ⭐ **Kuruş kontrolü:** poz `1.2`'ye `2` yazın → Tutar `743,73 ₺`.
      Ekrandaki fiyatı miktarla çarpınca **aynı sonucu** buluyorsunuz (dünkü 1 kuruş farkı bunun içindi).
- [ ] **D5** Miktar yazarken Tutar sütununda `…` görünüyor, yazmayı bırakınca gerçek tutar geliyor;
      alt şeritte "Kaydedilmemiş değişiklik var" → "Kaydedildi".
- [ ] **D6** Ondalık: `12,5` ve `12.5` ikisi de kabul ediliyor, aynı sonucu veriyor.
- [ ] **D7** ⭐ **Giriş maskesi:** miktar kutusuna harf ya da ikinci bir ayırıcı (`12,5,7`)
      **yazılamıyor** — tuş hiç kabul edilmiyor.
- [ ] **D7b** ⭐ Geçersiz bir değer bir şekilde kalırsa hücre **kırmızı kenarlıklı** oluyor, üstteki
      şerit hangi poz numaralarının bozuk olduğunu yazıyor ve **Gönder çalışmıyor** (31 Temmuz
      düzeltmesi: eskiden belge geçersiz girdiyle kapanıyordu).
- [ ] **D8** Miktarı silip boş bırakınca satır belgeden çıkıyor (Tutar `—` oluyor, toplam düşüyor).
- [ ] **D9** Alt **toplam şeridi** sayfa kaydırılırken ekranda sabit kalıyor.
- [ ] **D10** Tarayıcıyı tablet genişliğine daraltın → tablo yatay kayıyor, sayfa bozulmuyor.
- [ ] **D11** Hiç miktar girilmemişken "Gönder" **kapalı**.
- [ ] **D11b** ⭐ İş listesi açılışta **yalnızca aktif işleri** gösteriyor; "Tümü" ya da
      "İptal edilmiş" seçilince iptaller de görünüyor (kayıt silinmiyor).
- [ ] **D12** ⭐ **Gönderim animasyonu:** "Onaya Gönder"e basınca ekranı karartan katman çıkıyor —
      önce dönen halka, sonra çizilen tik ve *"Ön Fizibilite belgesi sisteme yüklendi"*; altında
      sunucudan gelen durum metni. Sağ üstte **X**, altında **"İşler Sayfasına Git"** ve "Kapat"
      düğmeleri var; katman kendiliğinden kapanmıyor.
- [ ] **D13** Gönderim hata verirse (ör. eksik EK1) katman kapanıyor ve **kırmızı hata şeridi**
      görünüyor — katmanın altında kalmıyor.

## E. Önizleme ve indirme

- [ ] **E1** Önizleme bölümünde **yalnızca miktarı girilmiş** satırlar listeleniyor.
- [ ] **E2** Üstte iş bilgileri (SP No, Route, Ek Aralığı, KableID, Uç A/B, sözleşme) doğru.
- [ ] **E3** **PDF — Fiyatlı** indiriliyor; dosya adı `TTI_BURSA_2026_FIZIBILITE_001_FIYATLI.pdf`.
- [ ] **E4** PDF'i açın: Türkçe harfler doğru (`İ`, `ş`, `ğ`), tablo düzgün, **Br Fiyat yuvarlanmamış**,
      `Br Fiyat × Miktar = Tutar` elle tutuyor.
- [ ] **E5** **PDF — Teklif**: birim fiyat, tutar ve toplam **boş**; miktarlar ve sütunlar yerinde.
- [ ] **E6** **Excel — Fiyatlı**: sözleşmenin **tüm** satırları var (grup başlıkları ve boş kalemler
      dahil), bilgi bloğu tablonun üstünde, tutar hücreleri gerçek sayı (formül yok).
- [ ] **E7** Fizibilite Excel'i **6 sütun** ("Fizibilite Miktar/Tutar"); keşif Excel'i kaynağın
      **8 sütunu**.
- [ ] **E8** Listede satır içi indirme düğmeleri çalışıyor ve **belgeyi açmıyor** (satıra tıklama
      ile karışmıyor).
- [ ] **E9** ⭐ **Hakedişte yalnızca "PDF" ve "Excel"** düğmesi var — fiyatlı/fiyatsız ayrımı yok.
      Fizibilite ve keşifte dört düğme duruyor.
- [ ] **E10** ⭐ **Hakediş Excel'i:** "1. Keşif Talep" ve "1.Keşif Tutar" **dolu**; "Saha Uygulama" ve
      "Kesin Hakediş Tutar" da dolu; toplam satırında **hem keşif hem hakediş toplamı** var ve
      "Toplam" etiketi keşif tutar sütununu ezmiyor.
- [ ] **E11** ⭐ **Hakediş PDF'i:** sayfa **yatay**, 8 sütun; keşif başlıkları görünüyor; birim fiyat
      tek satırda, sarmıyor. Fizibilite ve keşif PDF'i **dikey** ve 6 sütun.

## F. Aşama kuralı ve sıra

- [ ] **F1** Fizibilite göndermeden "1. Keşif → Belge Oluştur" → sarı şerit:
      *"…önce bu işin ön fizibilite belgesi gönderilip kapanmalı."*
- [ ] **F2** Fizibilite **onay beklerken** keşif açmayı deneyin → *"…satın alma onayı bekliyor;
      yönetici pazarlık fiyatlarını yükleyip onayladığında keşif açılabilecek."*
- [ ] **F3** ⭐ **Bölge sırası:** aynı bölgede iki iş açın, ikisinin de fizibilitesini onaylatın,
      **birinci** işin keşfini tamamlamadan **ikinci** işin keşfini açmayı deneyin → *"Bu bölgede
      keşif sırası SP-… işinde (#…); önce onun keşif belgesini tamamlayın."*
- [ ] **F4** Aynı işe ikinci bir fizibilite **arayüzden açılamıyor**: bir tür için belge varsa satırda
      "Belge Oluştur" yerine "Aç" düğmesi çıkıyor, yani kural hiç tetiklenmiyor. Kontrol edilecek olan
      düğmenin durumu — hata mesajı değil. *(Sunucu kuralı `dogrula-faz-d.sh` ile kilitli.)*
- [ ] **F5** Gönderilmiş bir belgeye girince miktar alanları **kapalı** ve mavi şerit
      *"… artık değiştirilemez."* diyor.

## G. Hakediş: ekler ve imza

- [ ] **G1** Hakediş belgesinde EK1 yokken sarı uyarı: *"EK1 güzergâh dosyası (.kmz/.kml) olmadan
      hakediş gönderilemez."*
- [ ] **G2** `.txt` dosyası EK1 olarak yüklenmeye çalışılınca **reddediliyor**, anlaşılır mesaj.
- [ ] **G3** `.kml` yüklenince listede ad, boyut ve tarih görünüyor; **ikinci EK1** yüklenemiyor
      (dosya seçici kayboluyor).
- [ ] **G4** EK2 olarak **birden fazla** görsel aynı anda seçilip yüklenebiliyor.
- [ ] **G5** Yüklenen dosya "İndir" ile geri alınabiliyor ve **aynı dosya** geliyor.
- [ ] **G6** "Sil" → dosya listeden çıkıyor.
- [ ] **G7** ⭐ **İmza tuvali (fare):** çizim yapılıyor, "Temizle" çalışıyor, boşken "İmzayı Kaydet"
      kapalı.
- [ ] **G8** İmza kaydedilince yeşil şerit: *"… tarafından imzalandı — tarih"* ve imza görüntüsü
      ekranda görünüyor.
- [ ] **G9** Yeniden çizip kaydedince **öncekinin üzerine yazıyor** (eski imza görünmüyor).
- [ ] **G10** ⭐ **Dokunmatik:** tablet ya da dokunmatik ekranda parmakla çizim çalışıyor.
- [ ] **G11** ⭐ **Ön koşul kilidi:** miktar, EK1 ve imzadan biri eksikken "Gönder" **pasif**;
      yanında eksikler madde madde yazılı ve eksik olan kart (poz tablosu / ek dosyalar / imza)
      **kırmızı kenarlıklı**. Üçü tamamlanınca düğme açılıyor.
- [ ] **G11b** İmza tuvali EK1 olmadan da kullanılabiliyor (sıra dayatılmıyor — kullanıcı kararı).
- [ ] **G12** Gönderilen hakedişin PDF'inde **imza bloğu** (görüntü + ad + tarih) basılı;
      fizibilite PDF'inde imza bloğu **hiç yok**.
- [ ] **G13** PDF'in altında EK1 dosya adı ve EK2 **adedi** yazıyor; görsellerin kendisi basılmıyor.
- [ ] **G14** ⭐ **Gönderimden sonra indirme:** hakediş gönderildikten sonra sayfanın **en altında**
      "Belgenin Ek Dosyaları" bölümü çıkıyor; EK1, her EK2 görseli ve imza için **İndir** düğmesi var.
- [ ] **G15** Aynı bölümde yükleme alanı, "Sil" düğmesi ve imza tuvali **yok** — belge artık
      değiştirilemez.
- [ ] **G16** İmza görüntüsü gönderimden sonra da ekranda görünüyor.

## H. Yönetici: onay kuyruğu ve fiyat listesi

- [ ] **H1** `yonetici` → "Onay Kuyruğu" → yalnızca *Satın Alma Onayı Beklemede* fizibiliteler.
- [ ] **H2** Süzgeçler çalışıyor: tür, durum, bölge, yıl — her biri listeyi daraltıyor.
- [ ] **H3** Anlamsız bileşim (Hakediş + Onay bekliyor) → "Bu süzgeçle eşleşen belge yok."
- [ ] **H4** Belgeyi açınca **Satın Alma Onayı** paneli görünüyor (bölge sorumlusunda görünmüyor).
- [ ] **H5** "Şablonu İndir" → `.xlsx` iniyor; Excel'de açınca fiyat sütunu **boş** ve kimlik sütunu var.
- [ ] **H6** Dosya seçmeden "Önizle" **kapalı**; önizleme yapmadan "Onayla" **kapalı** ve altında
      *"Onaylamadan önce önizleme zorunludur."* yazıyor.
- [ ] **H7** Şablonu doldurup yükleyin → önizlemede yeşil şerit, **86 kalem eşleşti**, eşleştirme
      yolu *"kalıcı kimlik sütunu"*, referans → yeni fiyat tablosu dolu.
- [ ] **H8** ⭐ **Yanlış dosya:** bir PDF'i (ya da rastgele bir dosyayı) yükleyin → kırmızı şerit
      *"Dosya geçerli bir .xlsx değil ya da bozuk"*; **500 hatası yok**, "Onayla" kapalı kalıyor.
- [ ] **H9** ⭐ **Bozuk içerik:** şablondan bir kalemin fiyatını silip yükleyin → sorun tablosu
      hangi satırın neden tutmadığını gösteriyor, "Onayla" **açılmıyor**.
- [ ] **H10** Dosyayı değiştirince önizleme sıfırlanıyor ve "Onayla" tekrar kapanıyor.
- [ ] **H11** Referansın **10 katı** bir fiyat yazıp yükleyin → sarı uyarı çıkıyor ama onay
      **engellenmiyor** (akıl kontrolü, kısıt değil).
- [ ] **H12** "Fiyat Listesini Yükle ve Onayla" → durum *Fizibilite Onaylandı* oluyor.
- [ ] **H13** ⭐ **Aynı belgeyi tekrar onaylamayı** deneyin → *"…zaten yüklenmiş"* (409).
- [ ] **H14** `bursa` ile girin → aynı iş için **1. Keşif** belgesi artık açılabiliyor ve poz
      tablosunda **pazarlık fiyatları** görünüyor (referans fiyatlar değil).

## I. Yönetici: iptal

- [ ] **I1** Belge ekranında "Bu Belgeyi İptal Et" → kırmızı çerçeveli panel açılıyor; metin
      sonucu açıkça yazıyor (**sıra açılmaz**, kayıt silinmez, yeni belge yeni numara alır).
- [ ] **I2** Gerekçe boşken onay düğmesi **kapalı** ve altında neden yazıyor.
- [ ] **I3** "Vazgeç" → hiçbir şey olmuyor.
- [ ] **I4** Gerekçe yazıp iptal edin → belge *İptal* rozetiyle **listede kalıyor**, kaybolmuyor.
- [ ] **I5** Aynı iş yeni bir keşif belgesi açabiliyor ve numara **002** oluyor (001 yeniden
      kullanılmıyor).
- [ ] **I6** İş detayında "Bu İşi İptal Et" → metin **bölge sırasının açılacağını** söylüyor
      (belge iptalinden farklı).
- [ ] **I7** İş iptal edilince açık taslakları da iptal oluyor; işe yeni belge açılamıyor.
- [ ] **I8** **Tamamlanmış** bir belgede iptal düğmesi **hiç görünmüyor**.
- [ ] **I9** `bursa` ile bir belge ekranında iptal düğmesi **görünmüyor**.

## J. Yönetici: kullanıcılar

- [ ] **J1** Liste 8 kullanıcıyı gösteriyor; yönetici satırında Bölge = "tüm bölgeler".
- [ ] **J2** "Parola Sıfırla" → satır altında alan açılıyor; 8 karakterden kısa parolada "Kaydet"
      **kapalı**.
- [ ] **J3** Parola alanı **maskelenmemiş** (yönetici kullanıcıya iletmek için görmeli).
- [ ] **J4** Parola değiştirilince yeşil şerit *"Yeni parolayı kendisine iletin."* diyor.
- [ ] **J5** ⭐ O kullanıcıyla **yeni parolayla** giriş yapılabiliyor, **eski parola** çalışmıyor.
- [ ] **J6** "Hesabı Kapat" → rozet *Kapalı*; o kullanıcı giriş yapamıyor.
- [ ] **J7** "Hesabı Aç" → geri açılıyor ve giriş çalışıyor.
- [ ] **J8** ⭐ Yönetici **kendi** hesabını kapatmaya çalışınca → *"Kendi hesabınızı kapatamazsınız;
      sistemde yönetici kalmaz."*
- [ ] **J9** ⭐ **Kullanıcı adı değiştirme:** satırdaki "Kullanıcı Adını Değiştir" ile ad değişiyor;
      kullanıcı **yeni adla** giriş yapabiliyor, eski adla yapamıyor.
- [ ] **J10** Başka bir kullanıcının adı (büyük/küçük harf farklı olsa bile) verilince
      *"Bu kullanıcı adı zaten kullanılıyor"* çıkıyor.
- [ ] **J11** Aktif kullanıcının durum rozeti **yeşil**, kapalı olanınki kırmızı.

> ℹ️ **"Parolayı göster" yapılmadı** — parolalar BCrypt özeti olarak saklanıyor, geri döndürülemez.
> Unutulursa yenisi belirlenir. Test sırasında bir kullanıcının parolasını değiştirirseniz doğrulama
> betikleri o kullanıcıyla giriş yapamaz; seed parolasını geri koymayı unutmayın.

## K. Uçtan uca (kapanış senaryosu)

- [ ] **K1** Sıfırdan tek seferde: `bursa` yeni iş açar → fizibilite miktar girer → **Onaya Gönder**
      → `yonetici` kuyruktan indirir, şablonu doldurup yükler, önizler, **onaylar** → `bursa` keşif
      miktarlarını girip gönderir → hakediş miktarları + EK1 + EK2 + imza → gönderir.
      Her adımda durum metni doğru değişiyor.
- [ ] **K2** Üç belgenin PDF ve Excel'i indiriliyor, tutarlar tutarlı.
- [ ] **K3** Arşiv klasöründe (`deploy/arsiv/BURSA/2026/…`) üretilen dosyalar duruyor.
- [ ] **K4** Yüklediğiniz EK1/EK2 ve imza `deploy/yukleme/BURSA/2026/<belgeNo>/` altında duruyor.

---

## Bulunanlar

Sorun çıkarsa buraya yazın; düzeltildikçe işaretlenir.

| # | Senaryo | Ne oldu | Durum |
|---|---|---|---|
|  |  |  |  |
