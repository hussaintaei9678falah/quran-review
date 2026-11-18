package com.quranreview.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.Locale

enum class ModelState { NONE, LOADING, READY, FAILED }
enum class RState { IDLE, LISTENING, PAUSED, FINISHED }

/** المحرّك: يحمّل النموذج، يستمع للميكروفون، يقارن بالنص، وينبّه عند الخطأ. */
object Reviewer : RecognitionListener {

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private var initialized = false

    @Volatile var modelProgress = 0
    var modelState = ModelState.NONE
    var modelMessage = ""
    private var model: Model? = null

    var state = RState.IDLE
    var tracker: Tracker? = null
    var statusText = ""
    var lastWasError = false
    var showText = false
    var speakError = true
    var onChange: (() -> Unit)? = null

    private var lastAyat: List<Ayah> = emptyList()
    private var lastSens = 1

    private var recognizer: Recognizer? = null
    private var speech: SpeechService? = null
    private var utterDone = 0
    private var tone: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private var ttsOk = false
    private var lastSignal = 0L
    private var signalling = false
    private var signalId = 0

    fun init(ctx: Context) {
        if (initialized) return
        initialized = true
        app = ctx.applicationContext
        try { tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100) } catch (e: Throwable) { tone = null }
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts
                if (t != null) {
                    val r = t.setLanguage(Locale("ar"))
                    ttsOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { main.post { resumeAfterSignal(-1) } }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { main.post { resumeAfterSignal(-1) } }
                    })
                }
            }
        }
    }

    private fun notifyUi() { onChange?.invoke() }

    // ---------------------------------------------------------------- النموذج

    fun prepare() {
        if (modelState == ModelState.LOADING || modelState == ModelState.READY) return
        modelState = ModelState.LOADING
        modelMessage = "جاري تجهيز التطبيق…"
        modelProgress = 0
        notifyUi()
        Thread {
            try {
                QuranData.loadAyat(app)
                val dir = File(app.filesDir, "model-ar")
                val marker = File(app.filesDir, "model-ar.ready")
                if (!marker.exists()) {
                    postMessage("تجهيز النموذج الصوتي لأول مرة (دقائق قليلة، مرة واحدة فقط)…")
                    dir.deleteRecursively()
                    val total = countAssets("model-ar")
                    var done = 0
                    copyAssets("model-ar", dir) {
                        done++
                        if (done % 3 == 0 || done == total) {
                            modelProgress = if (total > 0) done * 100 / total else 0
                            main.post { notifyUi() }
                        }
                    }
                    marker.writeText("ok")
                }
                postMessage("تحميل النموذج في الذاكرة…")
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                val m = Model(dir.absolutePath)
                main.post {
                    model = m
                    modelState = ModelState.READY
                    modelMessage = "جاهز"
                    notifyUi()
                }
            } catch (e: Throwable) {
                main.post {
                    modelState = ModelState.FAILED
                    modelMessage = "تعذّر تجهيز النموذج: " + (e.message ?: e.javaClass.simpleName)
                    notifyUi()
                }
            }
        }.start()
    }

    fun retryPrepare() {
        if (modelState == ModelState.FAILED) modelState = ModelState.NONE
        prepare()
    }

    private fun postMessage(msg: String) { main.post { modelMessage = msg; notifyUi() } }

    private fun countAssets(path: String): Int {
        val list = app.assets.list(path)
        if (list == null || list.isEmpty()) return 1
        var n = 0
        for (c in list) n += countAssets("$path/$c")
        return n
    }

    private fun copyAssets(path: String, dest: File, tick: () -> Unit) {
        val list = app.assets.list(path)
        if (list == null || list.isEmpty()) {
            dest.parentFile?.mkdirs()
            app.assets.open(path).use { input ->
                dest.outputStream().use { out -> input.copyTo(out, 1 shl 16) }
            }
            tick()
            return
        }
        dest.mkdirs()
        for (c in list) copyAssets("$path/$c", File(dest, c), tick)
    }

    // ---------------------------------------------------------------- المراجعة

    /** يبدأ المراجعة. يعيد رسالة خطأ أو null عند النجاح. */
    fun begin(ayat: List<Ayah>, sens: Int): String? {
        val m = model ?: return "النموذج الصوتي غير جاهز بعد"
        if (ayat.isEmpty()) return "لم يتم اختيار آيات"
        stopInternal()
        try {
            val t = Tracker(ayat, sens)
            val rec = Recognizer(m, 16000.0f)
            val sp = SpeechService(rec, 16000.0f)
            recognizer = rec
            speech = sp
            tracker = t
            lastAyat = ayat
            lastSens = sens
            utterDone = 0
            lastWasError = false
            statusText = "اقرأ الآن…"
            state = RState.LISTENING
            if (!sp.startListening(this)) {
                stopInternal()
                state = RState.IDLE
                return "تعذّر تشغيل الميكروفون"
            }
        } catch (e: Throwable) {
            stopInternal()
            state = RState.IDLE
            return "تعذّر تشغيل الميكروفون: " + (e.message ?: "")
        }
        notifyUi()
        return null
    }

    fun restart(): String? = begin(lastAyat, lastSens)

    fun togglePause() {
        when (state) {
            RState.LISTENING -> {
                speech?.setPause(true)
                state = RState.PAUSED
                statusText = "متوقف مؤقتاً"
            }
            RState.PAUSED -> {
                speech?.setPause(false)
                state = RState.LISTENING
                statusText = "اقرأ…"
            }
            else -> {}
        }
        notifyUi()
    }

    fun jump(delta: Int) {
        val t = tracker ?: return
        if (state == RState.FINISHED) return
        t.jumpAyah(delta)
        lastWasError = false
        utterDone = 0
        statusText = "اقرأ…"
        notifyUi()
    }

    /** إيقاف كامل والعودة للشاشة الرئيسية */
    fun stop() {
        stopInternal()
        state = RState.IDLE
        tracker = null
        app.stopService(Intent(app, ReviewService::class.java))
        notifyUi()
    }

    private fun stopInternal() {
        signalling = false
        signalId++
        try { speech?.stop() } catch (e: Throwable) {}
        try { speech?.shutdown() } catch (e: Throwable) {}
        try { recognizer?.close() } catch (e: Throwable) {}
        speech = null
        recognizer = null
    }

    private fun finish() {
        stopInternal()
        state = RState.FINISHED
        statusText = "تمّت المراجعة ✓"
        try { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 500) } catch (e: Throwable) {}
        app.stopService(Intent(app, ReviewService::class.java))
        notifyUi()
    }

    // ---------------------------------------------------------------- نتائج التعرّف

    override fun onPartialResult(hypothesis: String?) { handleText(extract(hypothesis, "partial"), false) }
    override fun onResult(hypothesis: String?) { handleText(extract(hypothesis, "text"), true) }
    override fun onFinalResult(hypothesis: String?) { handleText(extract(hypothesis, "text"), true) }
    override fun onError(exception: Exception?) {
        statusText = "خطأ في الميكروفون: " + (exception?.message ?: "")
        notifyUi()
    }
    override fun onTimeout() {}

    private fun extract(json: String?, key: String): String {
        if (json == null) return ""
        return try { JSONObject(json).optString(key, "") } catch (e: Throwable) { "" }
    }

    private fun handleText(text: String, isFinal: Boolean) {
        val t = tracker ?: return
        if (state != RState.LISTENING) {
            if (isFinal) utterDone = 0
            return
        }
        val ws = text.trim().split(Regex("\\s+"))
            .map { ArabicText.normalize(it) }
            .filter { it.isNotEmpty() }
        // الكلمة الأخيرة في النتيجة الجزئية قد تتغير، فلا نحكم عليها حتى تثبت
        val limit = if (isFinal) ws.size else ws.size - 1
        var i = utterDone
        var errored = false
        var progressed = false
        while (i < limit) {
            val r = t.feed(ws[i])
            i++
            if (r == Feed.ERROR) errored = true
            if (r == Feed.OK) progressed = true
            if (t.finished) break
        }
        utterDone = if (isFinal) 0 else maxOf(utterDone, i)

        if (errored) {
            statusText = "خطأ"
            lastWasError = true
            signalError()
        } else if (progressed) {
            statusText = "أحسنت… أكمل"
            lastWasError = false
        }
        if (t.finished) finish() else notifyUi()
    }

    // ---------------------------------------------------------------- التنبيه

    private fun signalError() {
        val now = System.currentTimeMillis()
        if (now - lastSignal < 1500) return
        lastSignal = now
        signalling = true
        val id = ++signalId
        speech?.setPause(true) // لكي لا يلتقط الميكروفون صوت التنبيه نفسه
        try { tone?.startTone(ToneGenerator.TONE_PROP_NACK, 350) } catch (e: Throwable) {}
        main.postDelayed({
            if (id != signalId || !signalling) return@postDelayed
            if (speakError && ttsOk) {
                try {
                    tts?.speak("خطأ", TextToSpeech.QUEUE_FLUSH, null, "err")
                } catch (e: Throwable) {}
                main.postDelayed({ resumeAfterSignal(id) }, 2000)
            } else {
                resumeAfterSignal(id)
            }
        }, 450)
    }

    private fun resumeAfterSignal(id: Int) {
        if (!signalling) return
        if (id != -1 && id != signalId) return
        signalling = false
        if (state == RState.LISTENING) speech?.setPause(false)
    }
}
