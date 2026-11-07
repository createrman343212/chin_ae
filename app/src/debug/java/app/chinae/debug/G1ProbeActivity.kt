package app.chinae.debug

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.vehicle.Vehicle
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import app.chinae.vehicle.VehicleStateHub
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/** masterplan 15.1 게이트 G1 확인용. debug 빌드 전용, logcat 태그 G1 */
class G1ProbeActivity : Activity() {
    private lateinit var out: TextView
    private val tts by lazy { TextToSpeech(this, TtsMode.ON_DEVICE) }
    private val stt by lazy { SpeechToText(this, SttMode.ON_DEVICE) }
    private val vehicle by lazy { Vehicle(this) }
    private val navi by lazy { NaviHelper(this) }
    private val hub by lazy { VehicleStateHub(this) }
    private val scope = MainScope()

    private fun log(msg: String) {
        Log.i("G1", msg)
        runOnUiThread { out.append(msg + "\n") }
    }

    private inline fun guard(name: String, block: () -> Unit) =
        try { block() } catch (e: Throwable) { log("$name EXCEPTION ${e.javaClass.simpleName}: ${e.message}") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { textSize = 18f; setPadding(32, 32, 32, 32) }
        setContentView(out)
        guard("TTS") { probeTts() }
        guard("STT") { probeStt() }
        guard("LIGHT") { probeLight() }
        guard("NAVI") { probeNavi() }
        guard("SIGNALS") { watchSignals() }
    }

    /** Virtual Console(demo/vc.sh)로 좌석과 기어를 바꾸면 VehicleStateHub 스냅샷이 logcat에 찍힘 */
    private fun watchSignals() {
        hub.start()
        scope.launch { hub.snapshot.collect { log("HUB $it") } }
    }

    private fun probeTts() {
        tts.initialize(object : TtsInit {
            override fun onSuccess() {
                log("TTS init OK")
                guard("TTS speak") {
                    // initialize 전에 addEventListener를 부르면 SDK 내부 lateinit 예외
                    tts.addEventListener(object : EventListener {
                        override fun onStart() = log("TTS onStart")
                        override fun onReady() = log("TTS onReady")
                        override fun onDone() = log("TTS onDone PASS")
                        override fun onError(type: TtsError) = log("TTS onError $type")
                        override fun onStop() = log("TTS onStop")
                        override fun onUpdatedRms(rms: Double) {}
                    })
                    tts.setLocale(Locale.KOREA)
                    tts.speak("안녕, 오늘도 수고했어.", false)
                    log("TTS speak called")
                    out.postDelayed({ guard("TTS speak2") { tts.speak("두 번째 확인이야.", true); log("TTS speak2 called") } }, 3000)
                }
            }
            override fun onFailure(type: TtsError) = log("TTS init FAIL $type")
        })
    }

    private fun probeStt() {
        stt.addListener(object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) = log("STT onUpdated final=$isFinal '$text'")
            override fun onUpdatedEpdData(on: Long, off: Long) {}
            override fun onStartedRecognition() = log("STT onStartedRecognition PASS")
            override fun onEndedRecognition() = log("STT onEndedRecognition")
            override fun onError(type: SttError) = log("STT onError $type")
            override fun onReady() = log("STT onReady")
            override fun onUpdatedRms(rms: Double) {}
        })
        stt.initialize(object : SttInit {
            override fun onSuccess() {
                log("STT init OK")
                guard("STT request") {
                    stt.setLocale(Locale.KOREA)
                    // PCM이 있으면 마이크(request)와 섞이지 않게 sendAudio만 씀
                    if (filesDir.resolve("g1.pcm").exists()) sendPcm() else stt.request()
                }
            }
            override fun onFailure(type: SttError) = log("STT init FAIL $type")
        })
    }

    /** 16kHz 16bit mono PCM. 앱이 user 10으로 돌아서 /sdcard 대신 run-as로 files/에 넣음 */
    private fun sendPcm() = Thread {
        guard("STT sendAudio") {
            val pcm = filesDir.resolve("g1.pcm")
            pcm.readBytes().toList().chunked(3200).forEach { stt.sendAudio(it.toByteArray()); Thread.sleep(100) }
            stt.completeAudioSend()
            log("STT sendAudio done ${pcm.length()}B")
        }
    }.start()

    private fun probeLight() {
        vehicle.initialize()
        val light = vehicle.getLight()
        light.checkAmbientLightBrightnessColorCapabilities({ config, areas ->
            log("LIGHT capability $config areas=${areas.size}")
            val area = areas.firstOrNull() ?: return@checkAmbientLightBrightnessColorCapabilities
            light.setAmbientLightBrightnessColor(area, 0xFF3B5BDB.toInt(),
                { a, c -> log("LIGHT set PASS area=$a color=${Integer.toHexString(c)}") },
                { e -> log("LIGHT set FAIL ${e.message}") })
        }, { e -> log("LIGHT capability FAIL ${e.message}") })
    }

    private fun probeNavi() {
        navi.addListener(object : NaviHelperEventListener {
            override fun onBookmarkInfo(list: List<BookmarkInfo>) {
                log("NAVI bookmarks=${list.size}")
                list.forEach { log("NAVI  ${it.category} ${it.name} ${it.latitude},${it.longitude}") }
            }
            override fun onError(code: NaviErrorCode) = log("NAVI onError $code")
            override fun onNavigationStatus(s: NavigationStatus) = log("NAVI status $s")
            override fun onRouteStarted(info: RouteStartInfo) = log("NAVI routeStarted duration=${info.duration} distance=${info.distance}")
            override fun onDestinationInfo(info: DestinationInfo) =
                log("NAVI dest ${info.name} remainTime=${info.remainTime} remainDistance=${info.remainDistance}")
        })
        navi.initialize({
            log("NAVI init OK")
            guard("NAVI get") { navi.getBookmarkInfo() }
            // remainTime 단위 확인용: am start ... --ez route true
            if (intent.getBooleanExtra("route", false)) guard("NAVI route") {
                out.postDelayed({ navi.requestRoute(RouteInfo(latitude = 37.4979, longitude = 127.0276, poiName = "강남역", poiId = "", address = "", poiSubId = "")) }, 3000)
                out.postDelayed({ navi.getDestinationInfo() }, 20_000)
            }
        }, { e -> log("NAVI init FAIL ${e.message}") })
    }

    override fun onDestroy() {
        scope.cancel()
        guard("release") { tts.release(); stt.release(); vehicle.release(); navi.release(); hub.stop() }
        super.onDestroy()
    }
}
