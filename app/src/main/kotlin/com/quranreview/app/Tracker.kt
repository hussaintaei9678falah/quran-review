package com.quranreview.app

data class Word(val display: String, val norm: String)
data class Ayah(val surah: Int, val number: Int, val juz: Int, val words: List<Word>)
data class Surah(val number: Int, val name: String, val ayahCount: Int)

enum class Feed { NONE, OK, ERROR }

/**
 * يتتبع القراءة مقابل النص المتوقع.
 * sens: 0 = عالية (أي خطأ يُنبَّه فوراً) ، 1 = متوسطة ، 2 = منخفضة (أقل تنبيهات خاطئة)
 */
class Tracker(val ayat: List<Ayah>, val sens: Int) {
    val words = ArrayList<Word>()
    val ayahStart = IntArray(ayat.size)
    var pos = 0
    var errors = 0
    var lastErrorIndex = -1
    private var bad = 0
    private var bismAllowed = false
    private var bIdx = 0

    private val need = when (sens) { 0 -> 1; 1 -> 2; else -> 3 }
    private val tol = when (sens) { 0 -> -1; 1 -> 0; else -> 1 }

    init {
        for ((i, a) in ayat.withIndex()) {
            ayahStart[i] = words.size
            words.addAll(a.words)
        }
        bismAllowed = ayat.isNotEmpty() && ayat[0].number == 1 && ayat[0].surah != 1 && ayat[0].surah != 9
    }

    val finished: Boolean get() = pos >= words.size

    fun ayahIndexAt(p: Int): Int {
        if (ayat.isEmpty()) return 0
        val q = if (p >= words.size) words.size - 1 else p
        var idx = 0
        for (i in ayahStart.indices) { if (ayahStart[i] <= q) idx = i else break }
        return idx
    }

    val currentAyahIndex: Int get() = ayahIndexAt(pos)

    /** الانتقال إلى بداية آية (نسبةً للآية الحالية) */
    fun jumpAyah(delta: Int) {
        val target = (currentAyahIndex + delta).coerceIn(0, ayat.size - 1)
        pos = ayahStart[target]
        bad = 0
        bismAllowed = false
        lastErrorIndex = -1
    }

    fun restartCurrentAyah() = jumpAyah(0)

    fun feed(r: String): Feed {
        if (r.isEmpty() || pos >= words.size) return Feed.NONE

        if (bismAllowed && pos == 0) {
            if (bIdx < BISM.size && ArabicText.similar(r, BISM[bIdx], tol)) {
                bIdx++
                if (bIdx >= BISM.size) bismAllowed = false
                return Feed.NONE
            }
            bismAllowed = false
        }

        if (ArabicText.similar(r, words[pos].norm, tol)) {
            pos++; bad = 0
            return Feed.OK
        }
        if (pos + 1 < words.size && r.length >= 4) {
            // دمج كلمتين في كلمة واحدة من المُتعرِّف (يا أيها ← يايها): نسمح بفارق حرف واحد فقط
            val merged = words[pos].norm + words[pos + 1].norm
            if (ArabicText.distance(r, merged) <= 1) {
                pos += 2; bad = 0
                return Feed.OK
            }
        }
        for (k in 1..3) {
            val idx = pos + k
            if (idx < words.size && ArabicText.similar(r, words[idx].norm, tol)) {
                val errAt = pos
                val total = bad + k
                pos = idx + 1
                bad = 0
                if (total >= need) {
                    errors++; lastErrorIndex = errAt
                    return Feed.ERROR
                }
                return Feed.OK
            }
        }
        bad++
        if (bad >= need) {
            bad = 0
            errors++; lastErrorIndex = pos
            return Feed.ERROR
        }
        return Feed.NONE
    }

    companion object {
        val BISM = listOf("بسم", "الله", "الرحمن", "الرحيم")
    }
}
