package app.chinae.ui

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
import androidx.lifecycle.lifecycleScope
import app.chinae.data.ChinaeDb
import app.chinae.data.Settings
import app.chinae.data.WorryRepository
import app.chinae.service.ChinaeService
import app.chinae.voice.Caption
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
        runCatching { hub.start() }.onFailure { Log.w("MainActivity", "vehicle", it) }
        setContent {
            val s by hub.snapshot.collectAsState()
            val pending by worries.pendingCount.collectAsState(0)
            val caption by Caption.line.collectAsState()
            val d = LocalDensity.current
            // 레이아웃은 폭 1050dp 기준. 차 화면(약 1680dp)은 1.6배로 키우고 좁은 화면은 맞춰 줄임
            val scale = (LocalConfiguration.current.screenWidthDp / LAYOUT_WIDTH_DP).coerceAtMost(1.6f)
            ChinaeTheme {
                CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsScreen(
                        settings, driving = s.gearPark == false, location = s.location, pending = pending, caption = caption,
                        onStart = { ChinaeService.start(this) },
                        onWipe = { lifecycleScope.launch { worries.wipeAll() } },
                    )
                }
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
