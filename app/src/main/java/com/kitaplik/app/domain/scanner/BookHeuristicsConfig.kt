package com.kitaplik.app.domain.scanner

import java.util.Locale

object BookHeuristicsConfig {

    /**
     * 40-50 sayfanın altındaki kısa dosyaları eleyen sayfa sayısı sınırı.
     */
    const val MIN_BOOK_PAGE_COUNT = 45

    /**
     * Hızlı metin analizinin yapılacağı ilk sayfa sayısı (ilk 3-8 sayfa).
     */
    const val FIRST_PAGES_TO_SCAN = 8

    /**
     * Hibrit puanlamada bir belgenin kitap sayılması için gereken minimum puan.
     */
    const val MIN_BOOK_SCORE = 50

    /**
     * Dosya isminde geçtiğinde ders notu, sınav, ödev, rapor, fatura veya sunum
     * olduğu anlaşılan ve kesin olarak dışlanan kara liste kelimeleri.
     */
    val BLACKLIST_FILENAME_KEYWORDS = listOf(
        "not",
        "notu",
        "notlar",
        "notlari",
        "notları",
        "dersnotu",
        "vize",
        "final",
        "butunleme",
        "bütünleme",
        "cikmis",
        "çıkmış",
        "hw",
        "homework",
        "odev",
        "ödev",
        "rapor",
        "raporu",
        "fatura",
        "e-fatura",
        "efatura",
        "dekont",
        "makbuz",
        "lab",
        "laboratuvar",
        "sunum",
        "sunumu",
        "slayt",
        "slide",
        "syllabus",
        "mufredat",
        "müfredat",
        "quiz",
        "katalog",
        "datasheet"
    )

    /**
     * İlk sayfalarda aranacak birincil kitap anahtar kelimeleri.
     */
    val PRIMARY_FIRST_PAGE_KEYWORDS = listOf(
        "içindekiler",
        "icindekiler",
        "contents",
        "table of contents",
        "yayınevi",
        "yayinevi",
        "yayınları",
        "yayinlari",
        "chapter",
        "bölüm",
        "bolum"
    )

    /**
     * İlk sayfalarda aranacak yardımcı kitap/künye anahtar kelimeleri.
     */
    val SECONDARY_FIRST_PAGE_KEYWORDS = listOf(
        "isbn",
        "önsöz",
        "onsoz",
        "preface",
        "introduction",
        "giriş",
        "birinci baskı",
        "ikinci baskı",
        "baskı",
        "basım",
        "telif hakkı",
        "all rights reserved",
        "copyright",
        "çeviren",
        "çeviri",
        "editör",
        "published",
        "edition",
        "roman",
        "kitap"
    )

    /**
     * Meta verideki (Author / Producer) otomatik araç isimlerini gerçek yazar/yayıncıdan
     * ayırt etmek için kullanılan jenerik yazılım kelimeleri.
     */
    val IGNORED_METADATA_CREATORS = listOf(
        "microsoft",
        "word",
        "powerpoint",
        "excel",
        "libreoffice",
        "openoffice",
        "scanner",
        "camscanner",
        "adobe",
        "quartz",
        "phantompdf",
        "wkhtmltopdf",
        "chrome",
        "firefox",
        "mac os",
        "windows",
        "unknown",
        "admin",
        "user"
    )

    val TURKISH_LOCALE: Locale = Locale.forLanguageTag("tr-TR")
}
