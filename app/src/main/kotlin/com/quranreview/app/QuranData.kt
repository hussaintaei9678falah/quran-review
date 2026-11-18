package com.quranreview.app

import android.content.Context

object QuranData {
    var surahs: List<Surah> = emptyList()
    var ayat: List<Ayah> = emptyList()

    fun loadSurahs(ctx: Context) {
        if (surahs.isNotEmpty()) return
        val list = ArrayList<Surah>()
        ctx.assets.open("surahs.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (l in lines) {
                val p = l.split('\t')
                if (p.size >= 3) list.add(Surah(p[0].trim().toInt(), p[1].trim(), p[2].trim().toInt()))
            }
        }
        surahs = list
    }

    /** تحميل نص القرآن كاملاً (يُستدعى في خيط خلفي) */
    fun loadAyat(ctx: Context) {
        if (ayat.isNotEmpty()) return
        val list = ArrayList<Ayah>(6300)
        ctx.assets.open("quran.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (l in lines) {
                val p = l.split('\t')
                if (p.size < 4) continue
                val ws = ArrayList<Word>()
                for (w in p[3].split(' ')) {
                    val n = ArabicText.normalize(w)
                    if (n.isNotEmpty()) ws.add(Word(w.trim(), n))
                }
                list.add(Ayah(p[0].trim().toInt(), p[1].trim().toInt(), p[2].trim().toInt(), ws))
            }
        }
        ayat = list
    }

    fun surahName(n: Int): String = surahs.firstOrNull { it.number == n }?.name ?: ""

    fun rangeSurah(s: Int, from: Int, to: Int): List<Ayah> =
        ayat.filter { it.surah == s && it.number in from..to }

    fun rangeJuz(j: Int): List<Ayah> = ayat.filter { it.juz == j }
}
