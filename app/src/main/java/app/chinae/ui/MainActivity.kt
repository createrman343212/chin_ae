package app.chinae.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.compose.ui.platform.LocalConfiguration
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import androidx.lifecycle.lifecycleScope
import app.chinae.data.ChinaeDb
import app.chinae.data.Settings
import app.chinae.data.WorryRepository
import app.chinae.service.ChinaeService
import app.chinae.voice.Caption
import app.chinae.vehicle.SimVehicle
import app.chinae.vehicle.VehicleStateHub
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val settings by lazy { Settings(this) }
    private val worries by lazy { WorryRepository(ChinaeDb.get(this)) }
    // 현재 위치와 기어만 쓰려고 화면용으로 따로 둠. 서비스의 허브와 별개라 화면이 닫히면 멈춤
    private val hub by lazy { VehicleStateHub(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면은 차 가로 화면용. 시험용 휴대폰에서도 가로로 보이게 함(차에서는 방향을 건드리지 않음)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        // 안내를 보고 시작하기를 눌러야 먼저 말을 걸기 시작함
        // shortcut: 시동 시 자동 시작(부팅 수신)은 없음, 앱을 한 번 열어야 함. 시연 흐름 확정 후 추가
        if (settings.onboardingDone) ChinaeService.start(this)
        // 차량 권한이 없거나 SDK가 없어도 설정 화면은 떠야 함(위치·주행 표시만 빠짐)
        val sim = !SimVehicle.isCar(this)
        // 휴대폰 시험: 차량 신호는 화면 버튼으로, 음성은 기본 음성 인식으로(마이크 권한 필요)
        if (sim && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        else if (!sim) runCatching { hub.start() }.onFailure { Log.w("MainActivity", "vehicle", it) }
        setContent {
            val s by (if (sim) SimVehicle.snapshot else hub.snapshot).collectAsState()
            val skip by SimVehicle.skipMin.collectAsState()
            val pending by worries.pendingCount.collectAsState(0)
            val caption by Caption.line.collectAsState()
            val demo = remember { File(filesDir, "demo").exists() }
            val d = LocalDensity.current
            // 레이아웃은 폭 1050dp 기준. 차 화면(약 1680dp)은 1.6배로 키우고 좁은 화면은 맞춰 줄임
            val scale = (LocalConfiguration.current.screenWidthDp / LAYOUT_WIDTH_DP).coerceAtMost(1.6f)
            ChinaeTheme {
                CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) {
                Surface(Modifier.fillMaxSize()) { Box {
                    SettingsScreen(
                        settings, driving = s.gearPark == false, location = s.location, pending = pending,
                        // 차에서는 주행 중 자막을 띄우지 않음(10장). 휴대폰 시험에서는 늘 띄움
                        caption = caption.takeIf { sim || s.gearPark != false },
                        onStart = { ChinaeService.start(this@MainActivity) },
                        onWipe = { lifecycleScope.launch { worries.wipeAll() } },
                    )
                    // 녹음 음성 입력(files/demo)을 쓰는 시연에서는 그 사실을 화면에 표시(13.3)
                    if (sim) SimPanel(s, skip, Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 16.dp))
                    if (demo) Text("시연 모드 · 녹음 음성 입력", Modifier.align(Alignment.BottomEnd).padding(12.dp), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                } }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { hub.stop() }
        super.onDestroy()
    }

    private companion object {
        const val LAYOUT_WIDTH_DP = 1050f
    }
}
