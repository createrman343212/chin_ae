package app.chinae.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import app.chinae.R
import app.chinae.data.ChinaeDb
import app.chinae.data.Settings
import app.chinae.data.WorryRepository
import app.chinae.trip.PlaceStore
import app.chinae.trip.TripEvent
import app.chinae.trip.TripStateMachine
import app.chinae.ritual.LockRitual
import app.chinae.ritual.playChime
import app.chinae.vehicle.LightController
import app.chinae.vehicle.VehicleStateHub
import app.chinae.voice.DialogueEngine
import app.chinae.voice.Lines
import app.chinae.voice.SpeechIn
import app.chinae.voice.SpeechOut
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime

/** 상주 포그라운드 서비스. 차량 신호 → 상태 머신 → 대화 시점 이벤트 (masterplan 2.2, 12장) */
class ChinaeService : Service() {
    private val scope = MainScope()
    private val settings by lazy { Settings(this) }
    private val worries by lazy { WorryRepository(ChinaeDb.get(this)) }
    private val placeStore by lazy { PlaceStore { settings.manualPlaces } }
    private val hub by lazy { VehicleStateHub(this, placeStore::updateFromBookmarks) }
    private val fsm by lazy { TripStateMachine({ placeStore.places }, { settings.workdays }) }
    private var pending = 0
    private val speechOut by lazy { SpeechOut(this) }
    private val speechIn by lazy { SpeechIn(this) }
    private val dialogue by lazy {
        DialogueEngine(speechOut, speechIn,
            { Lines.parse(assets.open("dialogue/${settings.tone.name.lowercase()}.json").bufferedReader().use { it.readText() }) },
            { text ->
                val saved = worries.entrust(text, System.currentTimeMillis())
                Log.i(TAG, "entrust len=${text.length} saved=${saved.map { it.category }}") // 내용은 남기지 않음(9장)
                saved.size
            })
    }
    private var talk: Job? = null
    private val light by lazy { LightController(this) }
    private val ritual by lazy { LockRitual(light, { playChime(this) }) }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        hub.start()
        speechOut.start()
        speechIn.start()
        light.start()
        // 신호가 바뀔 때와, 정지 신호 없이 시간만 흐를 때(출발 후 5분 등) 모두 판정. 둘 다 메인 스레드라 순서가 섞이지 않음
        scope.launch { worries.pendingCount.collect { pending = it } }
        scope.launch { hub.snapshot.collect { step() } }
        scope.launch { while (true) { delay(TICK_MS); step() } }
        // 보존 정리: 시작할 때와 하루 1회 (6.4)
        scope.launch { while (true) { worries.purge(System.currentTimeMillis()); delay(DAY_MS) } }
    }

    private fun step() {
        val now = LocalDateTime.now()
        val before = fsm.state
        // 회고일은 금요일(12장 주간 회고)
        val events = fsm.update(hub.snapshot.value, now, pending, reviewDay = now.dayOfWeek == DayOfWeek.FRIDAY)
        if (fsm.state != before) Log.i(TAG, "state $before -> ${fsm.state}")
        events.forEach(::onEvent)
    }

    private fun onEvent(e: TripEvent) {
        Log.i(TAG, "event $e")
        when (e) {
            TripEvent.ASK_ENTRUST -> converse { dialogue.runEntrust() }
            // 동승자 탑승, 잠금 의식 중 출발: 말과 듣기를 바로 끊음(상태 머신은 이미 대화 상태를 빠져나옴)
            TripEvent.STOP_SPEAKING -> talk?.cancel()
            TripEvent.START_LOCK_RITUAL -> converse { ritual.run() }
            // shortcut: 돌려받기(11.12)는 아직 없어 대화 상태에 갇히지 않게 바로 끝냄
            TripEvent.START_RETURN -> fsm.conversationEnded()
        }
    }

    private fun converse(block: suspend () -> Unit) {
        talk?.cancel()
        talk = scope.launch {
            try { block() } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.w(TAG, "dialogue", t) // 내용은 남기지 않음(9장)
            } finally { fsm.conversationEnded() }
        }
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        scope.cancel()
        hub.stop()
        speechOut.release()
        speechIn.release()
        light.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ChinaeService"
        private const val CHANNEL = "chinae"
        private const val TICK_MS = 30_000L
        private const val DAY_MS = 86_400_000L

        fun start(context: Context) = context.startForegroundService(Intent(context, ChinaeService::class.java))
    }
}
