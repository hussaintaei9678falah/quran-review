package com.quranreview.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bg = Color.parseColor("#0B1210")
    private val card = Color.parseColor("#16221D")
    private val green = Color.parseColor("#2ECC71")
    private val textC = Color.parseColor("#ECEFF1")
    private val muted = Color.parseColor("#90A4AE")
    private val red = Color.parseColor("#FF5252")
    private val amber = Color.parseColor("#FFB300")

    private lateinit var prefs: SharedPreferences
    private var inReview = false

    // الشاشة الرئيسية
    private var homeStatus: TextView? = null
    private var homeBar: ProgressBar? = null
    private var startBtn: Button? = null
    private var modeGroup: RadioGroup? = null
    private var surahSp: Spinner? = null
    private var fromSp: Spinner? = null
    private var toSp: Spinner? = null
    private var juzSp: Spinner? = null
    private var sensGroup: RadioGroup? = null
    private var speakCb: CheckBox? = null
    private var showCb: CheckBox? = null

    // شاشة المراجعة
    private var rvTitle: TextView? = null
    private var rvInfo: TextView? = null
    private var rvStatus: TextView? = null
    private var rvText: TextView? = null
    private var rvMain: Button? = null
    private var rvToggleText: Button? = null

    private var pendingAyat: List<Ayah>? = null
    private var pendingSens = 1

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        QuranData.loadSurahs(applicationContext)
        Reviewer.init(applicationContext)
        Reviewer.prepare()
        if (Reviewer.state != RState.IDLE) showReview() else showHome()
    }

    override fun onResume() {
        super.onResume()
        Reviewer.onChange = { refresh() }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        Reviewer.onChange = null
    }

    private fun refresh() {
        if (inReview) refreshReview() else refreshHome()
    }

    // ================================================================ أدوات الواجهة

    private fun txt(s: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun btn(s: String, fill: Int, size: Float = 18f): Button {
        val b = Button(this)
        b.text = s
        b.textSize = size
        b.setTextColor(Color.WHITE)
        b.isAllCaps = false
        val d = GradientDrawable()
        d.setColor(fill)
        d.cornerRadius = dp(16).toFloat()
        b.background = d
        return b
    }

    private fun lp(w: Int, h: Int, top: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.topMargin = dp(top)
        return p
    }

    private fun section(title: String): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val d = GradientDrawable()
        d.setColor(card)
        d.cornerRadius = dp(16).toFloat()
        box.background = d
        box.setPadding(dp(16), dp(14), dp(16), dp(14))
        box.addView(txt(title, 16f, green, true))
        return box
    }

    private fun radio(id: Int, s: String): RadioButton {
        val r = RadioButton(this)
        r.id = id
        r.text = s
        r.setTextColor(textC)
        r.textSize = 16f
        return r
    }

    // ================================================================ الشاشة الرئيسية

    private fun showHome() {
        inReview = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        scroll.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(30), dp(18), dp(30))
        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        col.addView(txt("مراجعة القرآن", 32f, textC, true))
        col.addView(txt("اقرأ من حفظك، والتطبيق يتابعك وينبّهك عند الخطأ — بدون إنترنت", 15f, muted), lp(-1, -2, 4))

        val status = txt("", 15f, amber)
        homeStatus = status
        col.addView(status, lp(-1, -2, 16))
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        homeBar = bar
        col.addView(bar, lp(-1, dp(8), 6))

        // ---- ماذا تراجع
        val what = section("ماذا تريد أن تراجع؟")
        val mg = RadioGroup(this)
        mg.orientation = RadioGroup.HORIZONTAL
        mg.addView(radio(101, "سورة / آيات"))
        mg.addView(radio(102, "جزء كامل"))
        modeGroup = mg
        what.addView(mg, lp(-1, -2, 6))

        val surahPanel = LinearLayout(this)
        surahPanel.orientation = LinearLayout.VERTICAL
        val sSp = Spinner(this)
        val names = QuranData.surahs.map { it.number.toString() + ". " + it.name }
        sSp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        surahSp = sSp
        surahPanel.addView(sSp, lp(-1, -2, 4))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(txt("من آية", 15f, textC))
        val fSp = Spinner(this)
        fromSp = fSp
        row.addView(fSp, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(txt("إلى آية", 15f, textC))
        val tSp = Spinner(this)
        toSp = tSp
        row.addView(tSp, LinearLayout.LayoutParams(0, -2, 1f))
        surahPanel.addView(row, lp(-1, -2, 6))
        what.addView(surahPanel, lp(-1, -2, 4))

        val juzPanel = LinearLayout(this)
        juzPanel.orientation = LinearLayout.VERTICAL
        val jSp = Spinner(this)
        jSp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, (1..30).map { "الجزء $it" })
        juzSp = jSp
        juzPanel.addView(jSp, lp(-1, -2, 4))
        juzPanel.visibility = View.GONE
        what.addView(juzPanel, lp(-1, -2, 4))
        col.addView(what, lp(-1, -2, 18))

        sSp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                fillAyahSpinners(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        mg.setOnCheckedChangeListener { _, checked ->
            surahPanel.visibility = if (checked == 101) View.VISIBLE else View.GONE
            juzPanel.visibility = if (checked == 102) View.VISIBLE else View.GONE
        }
        mg.check(if (prefs.getInt("mode", 101) == 102) 102 else 101)
        sSp.setSelection(prefs.getInt("surah", 0).coerceIn(0, 113))
        jSp.setSelection(prefs.getInt("juz", 29).coerceIn(0, 29))
        fillAyahSpinners(sSp.selectedItemPosition)

        // ---- الحساسية
        val sens = section("حساسية كشف الخطأ")
        val sg = RadioGroup(this)
        sg.orientation = RadioGroup.VERTICAL
        sg.addView(radio(201, "عالية — تنبّه لأي كلمة مختلفة (قد تُخطئ أحياناً)"))
        sg.addView(radio(202, "متوسطة — الموصى بها"))
        sg.addView(radio(203, "متسامحة — تنبيهات أقل، تفوّتها بعض الأخطاء"))
        sensGroup = sg
        sg.check(201 + prefs.getInt("sens", 1).coerceIn(0, 2))
        sens.addView(sg, lp(-1, -2, 6))
        col.addView(sens, lp(-1, -2, 14))

        // ---- خيارات
        val opts = section("خيارات")
        val cb1 = CheckBox(this)
        cb1.text = "انطق كلمة «خطأ» مع صوت التنبيه (يحتاج صوتاً عربياً على الهاتف)"
        cb1.setTextColor(textC)
        cb1.isChecked = prefs.getBoolean("speak", true)
        speakCb = cb1
        opts.addView(cb1, lp(-1, -2, 4))
        val cb2 = CheckBox(this)
        cb2.text = "إظهار نص الآية أثناء المراجعة (أخفِه لتختبر حفظك)"
        cb2.setTextColor(textC)
        cb2.isChecked = prefs.getBoolean("show", false)
        showCb = cb2
        opts.addView(cb2, lp(-1, -2, 2))
        col.addView(opts, lp(-1, -2, 14))

        val sb = btn("ابدأ المراجعة", green, 22f)
        sb.setOnClickListener { onStartClicked() }
        startBtn = sb
        col.addView(sb, lp(-1, dp(72), 22))

        col.addView(txt("نصيحة: ضع الهاتف قريباً منك، وقلّل ضجيج المكان لدقة أفضل.", 13f, muted), lp(-1, -2, 14))

        setContentView(scroll)
        refreshHome()
    }

    private fun fillAyahSpinners(surahIndex: Int) {
        val s = QuranData.surahs.getOrNull(surahIndex) ?: return
        val nums = (1..s.ayahCount).map { it.toString() }
        fromSp?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nums)
        toSp?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nums)
        fromSp?.setSelection(0)
        toSp?.setSelection(s.ayahCount - 1)
    }

    private fun refreshHome() {
        val st = homeStatus ?: return
        val bar = homeBar ?: return
        val sb = startBtn ?: return
        when (Reviewer.modelState) {
            ModelState.READY -> {
                st.text = "✓ جاهز — يعمل بدون إنترنت"
                st.setTextColor(green)
                bar.visibility = View.GONE
                sb.isEnabled = true
                sb.alpha = 1f
                sb.text = "ابدأ المراجعة"
            }
            ModelState.FAILED -> {
                st.text = Reviewer.modelMessage
                st.setTextColor(red)
                bar.visibility = View.GONE
                sb.isEnabled = true
                sb.alpha = 1f
                sb.text = "إعادة المحاولة"
            }
            else -> {
                val p = Reviewer.modelProgress
                st.text = Reviewer.modelMessage + (if (p > 0) "  $p%" else "")
                st.setTextColor(amber)
                bar.visibility = View.VISIBLE
                bar.progress = p
                sb.isEnabled = false
                sb.alpha = 0.4f
                sb.text = "ابدأ المراجعة"
            }
        }
    }

    private fun onStartClicked() {
        if (Reviewer.modelState == ModelState.FAILED) {
            Reviewer.retryPrepare()
            refreshHome()
            return
        }
        if (Reviewer.modelState != ModelState.READY) return

        val mode = modeGroup?.checkedRadioButtonId ?: 101
        val ayat: List<Ayah>
        if (mode == 101) {
            val s = (surahSp?.selectedItemPosition ?: 0) + 1
            var from = (fromSp?.selectedItemPosition ?: 0) + 1
            var to = (toSp?.selectedItemPosition ?: 0) + 1
            if (from > to) { val t = from; from = to; to = t }
            ayat = QuranData.rangeSurah(s, from, to)
        } else {
            ayat = QuranData.rangeJuz((juzSp?.selectedItemPosition ?: 0) + 1)
        }
        val sens = (sensGroup?.checkedRadioButtonId ?: 202) - 201
        prefs.edit()
            .putInt("mode", mode)
            .putInt("surah", surahSp?.selectedItemPosition ?: 0)
            .putInt("juz", juzSp?.selectedItemPosition ?: 0)
            .putInt("sens", sens)
            .putBoolean("speak", speakCb?.isChecked ?: true)
            .putBoolean("show", showCb?.isChecked ?: false)
            .apply()
        Reviewer.speakError = speakCb?.isChecked ?: true
        Reviewer.showText = showCb?.isChecked ?: false

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingAyat = ayat
            pendingSens = sens
            val perms = if (Build.VERSION.SDK_INT >= 33)
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(Manifest.permission.RECORD_AUDIO)
            requestPermissions(perms, 7)
            return
        }
        startReview(ayat, sens)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 7) return
        val micIdx = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        val granted = micIdx >= 0 && grantResults.size > micIdx && grantResults[micIdx] == PackageManager.PERMISSION_GRANTED
        val a = pendingAyat
        if (granted && a != null) {
            startReview(a, pendingSens)
        } else {
            Toast.makeText(this, "إذن الميكروفون مطلوب لتعمل المراجعة", Toast.LENGTH_LONG).show()
        }
        pendingAyat = null
    }

    private fun startReview(ayat: List<Ayah>, sens: Int) {
        val err = Reviewer.begin(ayat, sens)
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, ReviewService::class.java))
        showReview()
    }

    // ================================================================ شاشة المراجعة

    private fun showReview() {
        inReview = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(dp(16), dp(28), dp(16), dp(16))

        val title = txt("", 17f, muted)
        rvTitle = title
        root.addView(title, lp(-1, -2))
        val info = txt("", 15f, muted)
        rvInfo = info
        root.addView(info, lp(-1, -2, 2))

        val status = txt("", 40f, green, true)
        status.gravity = Gravity.CENTER
        rvStatus = status
        root.addView(status, lp(-1, -2, 18))

        val sv = ScrollView(this)
        val tv = txt("", 34f, textC)
        tv.gravity = Gravity.CENTER
        tv.setLineSpacing(0f, 1.35f)
        tv.setPadding(dp(8), dp(12), dp(8), dp(12))
        rvText = tv
        sv.addView(tv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val prev = btn("السابقة", Color.parseColor("#37474F"), 15f)
        prev.setOnClickListener { Reviewer.jump(-1) }
        val again = btn("أعد الآية", Color.parseColor("#37474F"), 15f)
        again.setOnClickListener { Reviewer.jump(0) }
        val next = btn("التالية", Color.parseColor("#37474F"), 15f)
        next.setOnClickListener { Reviewer.jump(1) }
        val tog = btn("", Color.parseColor("#37474F"), 15f)
        tog.setOnClickListener { Reviewer.showText = !Reviewer.showText; refreshReview() }
        rvToggleText = tog
        for (b in listOf(prev, again, next, tog)) {
            val p = LinearLayout.LayoutParams(0, dp(56), 1f)
            p.marginStart = dp(4); p.marginEnd = dp(4)
            row.addView(b, p)
        }
        root.addView(row, lp(-1, -2, 10))

        val big = btn("", green, 26f)
        big.setOnClickListener { onMainButton() }
        rvMain = big
        root.addView(big, lp(-1, dp(110), 12))

        val end = btn("إنهاء المراجعة", Color.parseColor("#B71C1C"), 16f)
        end.setOnClickListener {
            Reviewer.stop()
            showHome()
        }
        root.addView(end, lp(-1, dp(52), 10))

        setContentView(root)
        refreshReview()
    }

    private fun onMainButton() {
        when (Reviewer.state) {
            RState.LISTENING, RState.PAUSED -> Reviewer.togglePause()
            RState.FINISHED -> {
                val err = Reviewer.restart()
                if (err != null) {
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                } else {
                    startForegroundService(Intent(this, ReviewService::class.java))
                }
            }
            else -> {}
        }
    }

    private fun refreshReview() {
        if (Reviewer.state == RState.IDLE) { showHome(); return }
        val t = Reviewer.tracker ?: return
        val ai = t.currentAyahIndex
        val a = t.ayat[ai]

        rvTitle?.text = QuranData.surahName(a.surah) + " · الآية " + a.number + "   (" + (ai + 1) + " من " + t.ayat.size + ")"
        rvInfo?.text = "الأخطاء: " + t.errors
        val st = rvStatus
        if (st != null) {
            st.text = Reviewer.statusText
            st.setTextColor(
                when {
                    Reviewer.state == RState.PAUSED -> amber
                    Reviewer.lastWasError -> red
                    else -> green
                }
            )
        }

        rvToggleText?.text = if (Reviewer.showText) "أخفِ النص" else "أظهر النص"

        val tv = rvText
        if (tv != null) {
            if (Reviewer.showText) {
                val start = t.ayahStart[ai]
                val sb = SpannableStringBuilder()
                for ((k, w) in a.words.withIndex()) {
                    val gi = start + k
                    val s0 = sb.length
                    sb.append(w.display)
                    val e0 = sb.length
                    val color = when {
                        Reviewer.lastWasError && gi == t.lastErrorIndex -> red
                        gi < t.pos -> green
                        gi == t.pos -> Color.WHITE
                        else -> Color.parseColor("#78909C")
                    }
                    sb.setSpan(ForegroundColorSpan(color), s0, e0, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (gi == t.pos && !t.finished) {
                        sb.setSpan(BackgroundColorSpan(Color.parseColor("#33FFFFFF")), s0, e0, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    sb.append(' ')
                }
                tv.text = sb
            } else if (Reviewer.state == RState.FINISHED) {
                tv.text = "عدد الأخطاء: " + t.errors
            } else {
                tv.text = "﴿ ••• ﴾"
            }
        }

        val big = rvMain
        if (big != null) {
            when (Reviewer.state) {
                RState.LISTENING -> { big.text = "⏸  إيقاف مؤقت"; big.isEnabled = true }
                RState.PAUSED -> { big.text = "▶  متابعة"; big.isEnabled = true }
                RState.FINISHED -> { big.text = "↻  أعد المراجعة"; big.isEnabled = true }
                else -> { big.text = ""; big.isEnabled = false }
            }
        }
    }
}
