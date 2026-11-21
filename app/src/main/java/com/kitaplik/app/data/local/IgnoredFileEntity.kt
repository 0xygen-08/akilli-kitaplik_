package com.kitaplik.app.data.local

/**
 * Filtrelerden kaçan ve kullanıcının tek tıkla kitaplıktan kaldırdığı
 * alakasız belgeleri tutar. Böylece sonraki manuel veya haftalık taramalarda
 * aynı dosya tekrar kitaplığa eklenmez.
 */
data class IgnoredFileEntity(
    val filePath: String,
    val removedAtTimestamp: Long = System.currentTimeMillis()
)
