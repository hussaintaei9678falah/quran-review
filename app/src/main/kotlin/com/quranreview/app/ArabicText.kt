package com.quranreview.app

object ArabicText {

    /** يحوّل الكلمة إلى هيكل حروف مبسّط: بدون تشكيل ولا علامات، وتوحيد الألف والياء والتاء المربوطة. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val c: Char = when (ch) {
                '\u0671', '\u0623', '\u0625', '\u0622', '\u0672', '\u0673' -> '\u0627'
                '\u0649', '\u0626', '\u06CC' -> '\u064A'
                '\u0629' -> '\u0647'
                '\u0624' -> '\u0648'
                else -> ch
            }
            val isLetter = (c in '\u0621'..'\u063A') || (c in '\u0641'..'\u064A')
            if (isLetter && c != '\u0621') sb.append(c)
        }
        return sb.toString()
    }

    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(minOf(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** tol: -1 صارم، 0 متوسط، +1 متساهل */
    fun similar(a: String, b: String, tol: Int): Boolean {
        if (a == b) return true
        if (a.isEmpty() || b.isEmpty()) return false
        val len = maxOf(a.length, b.length)
        val base = when {
            len <= 3 -> 0
            len <= 5 -> 1
            len <= 8 -> 2
            else -> 3
        }
        val allowed = maxOf(0, base + tol)
        if (Math.abs(a.length - b.length) > allowed) return false
        return distance(a, b) <= allowed
    }
}
