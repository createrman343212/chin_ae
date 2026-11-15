package app.chinae.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/**
 * Pleos STT ON_DEVICE. 한 번 듣고 최종 문장을 돌려줌.
 * 시연용: files/demo/ 에 16kHz 16bit mono PCM이 있으면 마이크 대신 이름순으로 하나씩 넣고 지움
 * (run-as로만 넣을 수 있어 debuggable 빌드에서만 쓰임)
 */
class SpeechIn(context: Context) : Listener {
    private val stt = SpeechToText(context, Mode.ON_DEVICE)
    private val demoDir = File(context.filesDir, "demo")
    @Volatile private var ready = false
    @Volatile private var latest = ""
    @Volatile private var said = "" // 끝난 문장들
    @Volatile private var lastAt = 0L // 마지막으로 결과가 온 때
    @Volatile private var heard: CompletableDeferred<Unit>? = null
    @Volatile private var ended: CompletableDeferred<Unit>? = null

    fun start() {
        stt.addListener(object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) {
                // 한 문장 안에서는 누적 문자열로 오고, 문장이 끝나면(isFinal) 다음 문장은 새로 시작함(에뮬레이터 실측)
                latest = "$said $text".trim()
                lastAt = System.currentTimeMillis()
                if (isFinal) said = latest
                if (text.isNotBlank()) heard?.complete(Unit)
            }
            override fun onEndedRecognition() { ended?.complete(Unit) }
            override fun onError(type: ErrorType) { Log.w(TAG, "stt $type"); ended?.complete(Unit) }
            override fun onUpdatedEpdData(on: Long, off: Long) {}
            override fun onStartedRecognition() {}
            override fun onReady() {}
            override fun onUpdatedRms(rms: Double) {}
        })
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() { stt.setLocale(Locale.KOREA); ready = true }
            override fun onFailure(type: ErrorType) = Log.w(TAG, "stt init $type").let {}
        })
    }

    override suspend fun listen(): String? {
        if (!ready) return null // 10장: STT를 못 쓰면 맡기기 질문 결과 없음으로 처리
        latest = ""
        said = ""
        val h = CompletableDeferred<Unit>().also { heard = it }
        val e = CompletableDeferred<Unit>().also { ended = it }
        return try {
            coroutineScope {
                val pcm = demoDir.listFiles { f -> f.extension == "pcm" }?.minByOrNull { it.name }
                // request(마이크)와 sendAudio를 섞으면 엔진 오류라 둘 중 하나만(docs/context.md).
                // PCM은 다 보낸 뒤에야 결과가 나오므로(에뮬레이터 실측) 무응답 시간은 보낸 뒤부터 셈
                if (pcm != null) withContext(Dispatchers.IO) { feed(pcm) } else stt.request()
                // 7초 안에 말이 시작되지 않으면 무응답(3.4), 한 번 발화는 최대 60초
                if (withTimeoutOrNull(NO_RESPONSE_MS) { h.await() } == null) {
                    stt.stop()
                    return@coroutineScope null
                }
                // PCM은 보내는 중에 앞 문장의 끝 신호가 먼저 와서, 결과가 잠시 멈추면 끝으로 봄(에뮬레이터 실측)
                if (pcm != null) withTimeoutOrNull(MAX_UTTERANCE_MS) { while (System.currentTimeMillis() - lastAt < QUIET_MS) delay(100) }
                else if (withTimeoutOrNull(MAX_UTTERANCE_MS) { e.await() } == null) stt.stop()
                latest.ifBlank { null }
            }
        } catch (ce: CancellationException) {
            stt.stop()
            throw ce
        }
    }

    private suspend fun feed(pcm: File) {
        // 100ms(3200B) 단위로 실시간처럼 보냄
        pcm.readBytes().asList().chunked(3200).forEach { stt.sendAudio(it.toByteArray()); delay(100) }
        lastAt = System.currentTimeMillis()
        stt.completeAudioSend()
        pcm.delete()
    }

    fun release() = runCatching { stt.release() }

    private companion object {
        const val TAG = "SpeechIn"
        const val NO_RESPONSE_MS = 7_000L
        const val MAX_UTTERANCE_MS = 60_000L
        const val QUIET_MS = 2_000L
    }
}
