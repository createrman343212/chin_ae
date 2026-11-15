package app.chinae.voice

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import app.chinae.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** 휴대폰 시험용 음성. Pleos 음성 서비스가 없는 기기에서 안드로이드 기본 TTS와 음성 인식으로 대신함 */
class PhoneVoice(private val context: Context) : Speaker, Listener {
    @Volatile private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts().language = Locale.KOREA else Log.w(TAG, "tts init $status")
    }
    private fun tts(): TextToSpeech = tts

    override suspend fun say(text: String) {
        Caption.line.value = text
        if (ELEVEN) eleven(text)?.let { play(it); return }
        if (!ready) { delay(1_500L + text.length * 120L); return }
        withTimeoutOrNull(SAY_TIMEOUT_MS) {
            suspendCancellableCoroutine { c ->
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { if (c.isActive) c.resume(Unit) }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) { if (c.isActive) c.resume(Unit) }
                })
                c.invokeOnCancellation { tts.stop() }
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chinae")
            }
        }
    }

    /**
     * 말이 5초 동안 없을 때까지 이어서 들음. 기본 인식기는 말이 잠깐만 멈춰도 끝나서, 끝날 때마다 다시 듣고 이어 붙임.
     * 처음 7초 안에 말이 없으면 무응답(3.4). 권한이 없거나 못 알아들으면 null
     */
    override suspend fun listen(): String? {
        val parts = mutableListOf<String>()
        withTimeoutOrNull(LISTEN_TIMEOUT_MS) {
            while (true) parts += once(if (parts.isEmpty()) FIRST_WAIT_MS else SILENCE_MS) ?: break
        }
        // 기본 인식기는 마침표를 안 붙여서, 말이 멈춘 곳마다 문장을 끊어 걱정을 문장별로 나눌 수 있게 함(Segmenter)
        return parts.joinToString(". ").ifBlank { null }
    }

    /** 한 번 듣고 결과를 돌려줌. waitMs 안에 말이 시작되지 않으면 null. SpeechRecognizer는 메인 스레드에서만 씀 */
    private suspend fun once(waitMs: Long): String? = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return@withContext null
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        val main = Handler(Looper.getMainLooper())
        try {
            withTimeoutOrNull(LISTEN_TIMEOUT_MS) {
                suspendCancellableCoroutine { c ->
                    var started = false
                    main.postDelayed({ if (!started && c.isActive) { r.cancel(); c.resume(null) } }, waitMs)
                    r.setRecognitionListener(object : RecognitionListener {
                        override fun onResults(b: Bundle?) {
                            val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            if (c.isActive) c.resume(text)
                        }
                        override fun onError(error: Int) { Log.w(TAG, "stt $error"); if (c.isActive) c.resume(null) }
                        override fun onReadyForSpeech(p: Bundle?) {}
                        override fun onBeginningOfSpeech() { started = true }
                        override fun onRmsChanged(v: Float) {}
                        override fun onBufferReceived(b: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(b: Bundle?) {}
                        override fun onEvent(t: Int, b: Bundle?) {}
                    })
                    c.invokeOnCancellation { r.cancel() }
                    r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
                        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS))
                }
            }
        } finally { main.removeCallbacksAndMessages(null); r.destroy() }
    }

    /** ElevenLabs로 합성한 mp3. 같은 문장은 캐시를 다시 씀. 실패하면 null(기본 TTS로 넘어감) */
    private suspend fun eleven(text: String): File? = withContext(Dispatchers.IO) {
        val f = File(context.cacheDir, "tts_${text.hashCode()}.mp3")
        if (f.exists()) return@withContext f
        runCatching {
            val c = URL("https://api.elevenlabs.io/v1/text-to-speech/${BuildConfig.ELEVENLABS_VOICE_ID}?output_format=mp3_44100_128")
                .openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("xi-api-key", BuildConfig.ELEVENLABS_API_KEY)
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(JSONObject().put("text", text).put("model_id", "eleven_multilingual_v2").toString().toByteArray()) }
            check(c.responseCode == 200) { "http ${c.responseCode}" }
            // 받다 끊기면 반쪽 파일이 캐시에 남지 않게 임시 파일에 받은 뒤 이름을 바꿈
            val tmp = File(context.cacheDir, f.name + ".part")
            c.inputStream.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            tmp.renameTo(f); f
        }.onFailure { Log.w(TAG, "elevenlabs ${it.message}") }.getOrNull()
    }

    private suspend fun play(f: File) = suspendCancellableCoroutine { c ->
        val p = MediaPlayer()
        runCatching { p.setDataSource(f.path); p.prepare() }.onFailure { p.release(); c.resume(Unit); return@suspendCancellableCoroutine }
        p.setOnCompletionListener { it.release(); if (c.isActive) c.resume(Unit) }
        c.invokeOnCancellation { runCatching { p.stop(); p.release() } }
        p.start()
    }

    fun release() = runCatching { tts.shutdown() }

    companion object {
        /** 키와 목소리를 넣고 빌드했을 때만 ElevenLabs를 씀 */
        val ELEVEN = BuildConfig.ELEVENLABS_API_KEY.isNotBlank() && BuildConfig.ELEVENLABS_VOICE_ID.isNotBlank()
        private const val TAG = "PhoneVoice"
        private const val SAY_TIMEOUT_MS = 20_000L
        private const val LISTEN_TIMEOUT_MS = 60_000L
        private const val FIRST_WAIT_MS = 7_000L
        private const val SILENCE_MS = 5_000L
    }
}
