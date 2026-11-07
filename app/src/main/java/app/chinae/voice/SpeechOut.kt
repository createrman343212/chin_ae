package app.chinae.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Pleos TTS ON_DEVICE. 한 문장을 말하고 끝날 때까지 기다림 */
class SpeechOut(context: Context) : Speaker {
    private val tts = TextToSpeech(context, Mode.ON_DEVICE)
    @Volatile private var ready = false
    @Volatile private var done: CompletableDeferred<Unit>? = null

    fun start() = tts.initialize(object : OnInitializeListener {
        override fun onSuccess() {
            // initialize 성공 뒤에 addEventListener를 불러야 함(SDK 내부 lateinit, docs/context.md)
            tts.addEventListener(object : EventListener {
                override fun onStart() {}
                override fun onReady() {}
                override fun onDone() { done?.complete(Unit) }
                override fun onStop() { done?.complete(Unit) }
                override fun onError(type: ErrorType) { Log.w(TAG, "tts $type"); done?.complete(Unit) }
                override fun onUpdatedRms(rms: Double) {}
            })
            tts.setLocale(Locale.KOREA)
            ready = true
        }
        override fun onFailure(type: ErrorType) = Log.w(TAG, "tts init $type").let {}
    })

    override suspend fun say(text: String) {
        // shortcut: TTS를 못 쓰면 조용히 넘어감. 정차 중 화면 자막(10장)은 UI 작업 때 추가
        if (!ready) return
        val d = CompletableDeferred<Unit>().also { done = it }
        try {
            // isFinal=true여야 바로 읽음(false는 서비스에 쌓이기만 함)
            tts.speak(text, true)
            withTimeoutOrNull(SAY_TIMEOUT_MS) { d.await() }
        } catch (e: CancellationException) {
            tts.stop() // 동승자 탑승 등으로 대화가 끊기면 말도 바로 멈춤
            throw e
        }
    }

    fun release() = runCatching { tts.release() }

    private companion object {
        const val TAG = "SpeechOut"
        const val SAY_TIMEOUT_MS = 20_000L
    }
}
