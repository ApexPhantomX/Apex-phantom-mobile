# Apex Phantom Mobile

Apex Phantom klanı için Android oyun yardımcısı: Oyun Kalkanı, canlı izleme, oyun içi panel, bağlantı ve saat araçları, Mobile Legends ve Honor of Kings için karakter, eşya ve build planner.

## Derleme

1. Bilgisayara **Android Studio** kur (güncel sürüm).
2. Android Studio’da **File › Open** ile bu klasörü (`ApexPhantomMobile`) aç. İlk açılışta gerekli dosyaları kendisi indirir, birkaç dakika sürebilir.
3. **Build › Build App Bundle(s) / APK(s) › Build APK(s)** ile APK’yı oluştur. Dosya `app/build/outputs/apk/debug/app-debug.apk` konumunda olur.
4. APK’yı telefona at ve kur. Telefon “bilinmeyen kaynak” izni isteyebilir.

## Telefonda ilk kurulum

1. Google Play’den **Shizuku** uygulamasını kur.
2. Telefonda **Geliştirici seçenekleri**ni aç, **Kablosuz hata ayıklama**yı etkinleştir.
3. Shizuku’yu aç ve “Kablosuz hata ayıklama ile başlat” adımlarını uygula.
4. Apex Phantom’u aç, Ana Sayfa’daki **İzin Ver** ile Shizuku iznini ver.
5. Oyun içi panel için **Diğer uygulamaların üzerinde göster** iznini ver (Ana Sayfa’daki kart bunu açar).
6. Bildirim iznini ver; kalkan arka planda çalışırken bildirim gösterir.

Telefon yeniden başlatılınca Shizuku’yu 3. adımdaki gibi tekrar başlatmak gerekir. Root gerekmez, garanti etkilenmez.

## Kalkanın yaptıkları (oyun açılınca uygular, kapanınca geri alır)

| Ayar | Telefonda yaptığı |
|---|---|
| Arka planı dondur | Seçilen moda göre üçüncü taraf uygulamaların arka plan çalışma iznini kaldırır (`RUN_ANY_IN_BACKGROUND`) ve durdurur. Sistem uygulamaları, klavye, ana ekran, Shizuku ve istisnalar dokunulmaz. |
| Pil tasarrufunu kapat | Pil Tasarrufu açıksa kapatır, oyun bitince açar. |
| Oyun modu: Performans | `cmd game mode performance` (Android 12+). |
| Yenileme hızını sabitle | `peak_refresh_rate` ve `min_refresh_rate` değerlerini ekranın en yüksek hızına çeker. |
| Animasyonları kapat | Üç animasyon ölçeğini 0 yapar. |
| Rahatsız Etmeyin | Öncelikli modu açar. |
| Arka plan mobil verisini kes | Durdurulan uygulamaları arka plan veri kısıtlama listesine ekler (yalnızca mobil veride etkili). |
| Konum taramalarını kapat | Konum için yapılan Wi-Fi ve Bluetooth taramalarını kapatır. |

Önceki değerler telefona kaydedilir; uygulama kapansa bile sonraki açılışta geri alınır. Oyundan 20 saniye uzak kalınınca ayarlar geri yüklenir.

## Build paylaşma

Build Planner’da **Klanla Paylaş**, build’i okunabilir bir metin ve sonunda `APB1:` ile başlayan bir kodla paylaşır (Discord, WhatsApp vb.). Klan üyesi mesajı kopyalayıp oyunun **Buildler** sekmesindeki **Kod ile Ekle**’ye yapıştırınca build aynen eklenir.

## Oyun verisi

Karakter ve eşya listeleri `app/src/main/assets/games/` içindeki `mlbb.json` ve `hok.json` dosyalarındadır (Ekim 2026). Yeni karakter veya eşya geldiğinde bu dosyaya bir satır eklemek yeterli. Karakter simgeleri uygulama tarafından çizilir (baş harfler, role göre renkli çerçeve).

Kaynaklar: mlbbhub.com, liquipedia.net, hokstats.gg, hub-game.com, honor-of-kings.fandom.com.
