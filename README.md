# 📚 Akıllı Kitaplık (Smart Bookshelf)

**Geliştirici / Author:** Adem Uludağ  
**Lisans / License:** [PolyForm Noncommercial License 1.0.0](LICENSE) *(Kişisel ve eğitim amaçlı kullanıma/özelleştirmeye açık, ticari kullanıma kapalı)*

---

## 📥 APK İndir

- **Doğrudan Son Sürüm APK İndir:** [akilli-kitaplik-latest.apk](https://github.com/0xygen-08/akilli-kitaplik_/releases/latest/download/akilli-kitaplik-latest.apk)
- **Tüm Sürümler (Releases):** [GitHub Releases](https://github.com/0xygen-08/akilli-kitaplik_/releases)

### Bu Çatalda (Fork) Yapılan Güncellemeler
- **Editoryal Tasarım Yenilemesi:** Sıcak kağıt / koyu okuma odası renk paleti, serif başlık tipografisi, cilt sırtlı minyatür kitap kartları, `Kaldığın Yerden Devam Et` öne çıkan okuma kartı ve dokunsal mikro-etkileşimler.
- **Tam Ekran Alt Sistem Çubuğu Gizleme:** Uygulama genelinde Android alt gezinme çubuğu (geri / ana ekran / son uygulamalar) kenardan kaydırmayla geçici açılacak şekilde gizlendi; okuyucu modunda kontroller kapatıldığında durum çubuğu da gizlenerek tam ekran okuma sağlandı.

---

**Akıllı Kitaplık**, Android cihazınızdaki tüm depolama alanını (`Documents`, `Download`, `LocalSend`, `Telegram`, `WhatsApp` vb.) tarayarak PDF ve EPUB belgelerini sizin belirlediğiniz kriterlere göre otomatik sınıflandıran, birbirine karışmayan ayrık listelerde gecikmesiz olarak sunan ve yerleşik okuyucusuyla doğrudan açan hafif bir Android uygulamasıdır.

---

## ✨ Temel Özellikler

1. **Özelleştirilebilir Kriterler ve Birbirine Karışmayan Çoklu Listeler**:
   - Varsayılan **Kitaplar** listesine ek olarak **Ders Notlarım**, **Makaleler** gibi dilediğiniz kadar yeni liste oluşturabilir ve her listeyi yeniden isimlendirebilirsiniz.
   - Her liste için ayrı ayrı:
     - **Minimum ve Maksimum Sayfa Sayısı**,
     - **Belgenin İsminde Aranan Kelimeler (Whitelist)**,
     - **Kelime Kara Listesi (Blacklist)** *(ör. `not`, `vize`, `final`, `hw`, `rapor`, `fatura`, `lab`, `sunum`)*,
     - **İlk Sayfalarda Aranan Kelimeler** *(ör. `İçindekiler`, `Contents`, `Yayınevi`, `Chapter`, `Hafta`, `Ders`)* ve
     - **Gömülü Meta Veri (`Yazar`, `Yayıncı`, `ISBN`)** kriterleri tanımlanabilir.
   - **Mükerrer Kayıt Engeli:** Aynı belge iki farklı listede **asla** bulunamaz. Bir listede yer alan belge, diğer listelerin taramalarında otomatik olarak atlanır.

2. **Sıfır Gecikmeli Açılış ve Pil Dostu Tarama**:
   - Uygulama her açıldığında cihazı yeniden taramaz; yerel SQLite veritabanındaki mevcut listeleri **anında (0 ms bekleme ile)** ekrana getirir.
   - Yeni belgelerin taranması yalnızca kullanıcının **Şimdi Tara** butonuna basmasıyla veya tercihe bağlı olarak **haftada bir kez** arka planda (`WorkManager`, düşük pil korumalı) gerçekleştirilir.

3. **Her Listeye Özel Klasör Oluşturup Kopyalamadan Toparlama**:
   - Seçili liste için **`'<Liste İsmi>' Klasöründe Toparla`** butonuna basıldığında `Documents/<Liste İsmi>` (ör. `Documents/Kitaplar` veya `Documents/Ders Notlarım`) klasörü oluşturulur ve yalnızca o listedeki belgeler diskte ikinci bir kopya oluşturulmadan (`move` / `rename`) ilgili klasöre taşınır.

4. **Tek Tıkla Kaldırma ve Elle Belge Ekleme**:
   - Filtrelerden kaçan alakasız belgeler tek tıkla listeden kaldırılır ve `ignored_files` tablosuna işlenerek sonraki taramalarda tekrar listelenmez.
   - Tanınmayan veya özel belgeler **+ Elle Ekle** butonuyla doğrudan aktif listeye eklenebilir.

5. **Soru Sormadan Açılan Yerleşik PDF & EPUB Okuyucu**:
   - Listeden bir belgeye dokunulduğunda hiçbir onay veya ayar penceresi çıkmadan doğrudan yerleşik okuyucu açılır.
   - **Kalınan Sayfayı Hatırlama:** Sayfa değiştikçe ve belge kapatıldığında açık olan sayfa otomatik kaydedilir; tekrar açıldığında doğrudan kaldığı sayfadan devam eder.
   - **Zengin Jest Desteği:** Sağa/sola kaydırma (`HorizontalPager`), yukarı/aşağı dikey kaydırma (`VerticalPager`), sol/sağ kenara dokunarak sayfa çevirme, çift dokunuşla ve iki parmakla yakınlaştırma (pinch-to-zoom), PDF gece okuma modu ve EPUB yazı boyutu ayarı.

6. **Karanlık / Aydınlık Tema ve Çoklu Dil Desteği**:
   - Üst bardan tek dokunuşla **Aydınlık**, **Karanlık** ve **Sistem** temaları arasında geçiş yapılabilir.
   - Uygulama adı cihaz diline göre otomatik uyum sağlar (Türkçe: *Akıllı Kitaplık*, İngilizce: *Smart Bookshelf* ve 13+ ek dil).

---

## 🛠️ Kaynak Koddan Derleme (Build)

Projeyi kendi bilgisayarınızda derlemek için **JDK 17+** ve **Android SDK (API 34)** yeterlidir:

```bash
git clone https://github.com/Adem-Uludag/akilli-kitaplik.git
cd akilli-kitaplik
./gradlew assembleDebug
```

Derlenen APK dosyası `app/build/outputs/apk/debug/app-debug.apk` konumunda oluşacaktır.

---

## ⚖️ Telif Hakkı, Lisans ve Marka Bildirimi

**Copyright (c) 2026 Adem Uludağ. Tüm hakları saklıdır.**

Bu proje **[PolyForm Noncommercial License 1.0.0](LICENSE)** altında lisanslanmıştır:
- **İzin Verilenler:** Kaynak kodunu inceleyebilir, çatallayabilir (fork), kişisel veya eğitim amaçlı olarak kendiniz için özelleştirip ücretsiz kullanabilirsiniz.
- **Yasaklananlar:** Bu yazılımı veya türetilmiş sürümlerini **ticari amaçla kullanmak, satmak, ücretli dağıtmak veya içine reklam/abonelik ekleyerek gelir elde etmek kesinlikle yasaktır.**
- **Atıf Zorunluluğu:** Kodu kullanırken veya paylaşırken `Copyright (c) 2026 Adem Uludağ` telif bildirimini ve [LICENSE](LICENSE) dosyasını korumanız zorunludur.
- **Marka ve Logo Hakları:** **"Akıllı Kitaplık"** / **"Smart Bookshelf"** uygulama adı, `com.ademuludag.kitaplik` paket kimliği ve uygulama logosu **Adem Uludağ**'a aittir. Kodu çatallayıp (fork) kendi değiştirilmiş sürümünü paylaşanlar kendi uygulama isimlerini, logolarını ve paket kimliklerini kullanmalıdır.
