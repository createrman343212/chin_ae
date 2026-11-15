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
import app.chinae.trip.TripState
import app.chinae.trip.TripStateMachine
import app.chinae.ritual.Ambient
import app.chinae.ritual.LockRitual
import app.chinae.ritual.playChime
import app.chinae.vehicle.LightController
import app.chinae.vehicle.SimVehicle
import app.chinae.vehicle.VehicleStateHub
import app.chinae.voice.Caption
import app.chinae.voice.DialogueEngine
import app.chinae.voice.Lines
import app.chinae.voice.PhoneVoice
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
    // 차가 아니면(휴대폰 시험) 차량 신호·음성·조명 대신 화면 버튼(SimVehicle)과 기본 음성을 씀
    private val sim by lazy { !SimVehicle.isCar(this) }
    private val placeStore by lazy { PlaceStore { if (sim) SimVehicle.places else settings.manualPlaces } }
    private val hub by lazy { VehicleStateHub(this, placeStore::updateFromBookmarks) }
    // 휴대폰 시험은 요일과 상관없이 해 볼 수 있게 매일을 출근일로 봄
    private val fsm by lazy { TripStateMachine({ placeStore.places }, { if (sim) DayOfWeek.entries.toSet() else settings.workdays }) }
    private var pending = 0
    private val speechOut by lazy { SpeechOut(this) }
    private val speechIn by lazy { SpeechIn(this) }
    private val phone by lazy { PhoneVoice(this) }
    private val snapshot get() = if (sim) SimVehicle.snapshot else hub.snapshot
    private val dialogue by lazy {
        DialogueEngine(if (sim) phone else speechOut, if (sim) phone else speechIn,
            { Lines.parse(assets.open("dialogue/${settings.tone.name.lowercase()}.json").bufferedReader().use { it.readText() }) },
            { text ->
                val saved = worries.entrust(text, System.currentTimeMillis())
                Log.i(TAG, "entrust len=${text.length} saved=${saved.map { it.category }}") // 내용은 남기지 않음(9장)
                saved.size
            },
            alias = { settings.alias },
            pending = { worries.pending() },
            settle = { id, cmd ->
                worries.settle(id, cmd, System.currentTimeMillis())
                Log.i(TAG, "settle $id $cmd")
            })
    }
    private var talk: Job? = null
    private val light by lazy { LightController(this) }
    private val ritual by lazy { LockRitual(if (sim) Ambient { null } else light, { playChime(this) }) }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        if (sim) phone /* 기본 TTS 준비를 미리 시작 */ else {
            // 권한이 없거나 SDK가 없어도 서비스가 죽지 않게 함(죽으면 화면까지 같이 꺼짐)
            runCatching { hub.start(); speechOut.start(); speechIn.start(); light.start() }.onFailure { Log.w(TAG, "vehicle", it) }
        }
        // 신호가 바뀔 때와, 정지 신호 없이 시간만 흐를 때(출발 후 5분 등) 모두 판정. 둘 다 메인 스레드라 순서가 섞이지 않음
        scope.launch { worries.pendingCount.collect { pending = it } }
        scope.launch { snapshot.collect { step() } }
        if (sim) scope.launch { SimVehicle.skipMin.collect { step() } }
        if (sim) scope.launch {
            SimVehicle.talkNow.collect { e ->
                // 동승자가 있으면 바로가기도 말을 걸지 않음(S2)
                if (SimVehicle.snapshot.value.alone == false) return@collect
                fsm.startNow(if (e == TripEvent.ASK_ENTRUST) TripState.ENTRUST_ASK else TripState.RETURN_ASK)
                onEvent(e)
            }
        }
        scope.launch { while (true) { delay(TICK_MS); step() } }
        // 보존 정리: 시작할 때와 하루 1회 (6.4)
        scope.launch { while (true) { worries.purge(System.currentTimeMillis()); delay(DAY_MS) } }
    }

    private fun step() {
        val now = LocalDateTime.now().plusMinutes(if (sim) SimVehicle.skipMin.value else 0)
        val before = fsm.state
        // 회고일은 금요일(12장 주간 회고)
        val events = fsm.update(snapshot.value, now, pending, reviewDay = now.dayOfWeek == DayOfWeek.FRIDAY)
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
            TripEvent.START_RETURN -> converse { dialogue.runReturn() }
        }
    }

    private fun converse(block: suspend () -> Unit) {
        talk?.cancel()
        talk = scope.launch {
            try { block() } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.w(TAG, "dialogue", t) // 내용은 남기지 않음(9장)
            } finally { fsm.conversationEnded(); Caption.line.value = null }
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
        if (sim) phone.release() else runCatching { hub.stop(); speechOut.release(); speechIn.release(); light.release() }
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
