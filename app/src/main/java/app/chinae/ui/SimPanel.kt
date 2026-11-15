package app.chinae.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.app.ActivityManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chinae.vehicle.SimVehicle
import app.chinae.trip.TripEvent
import androidx.compose.ui.Alignment
import app.chinae.voice.PhoneVoice
import androidx.compose.foundation.layout.widthIn
import app.chinae.vehicle.VehicleSnapshot

/** 휴대폰 시험용 차량 신호 버튼. 차(AAOS)에서는 보이지 않음 */
@Composable
fun SimPanel(s: VehicleSnapshot, skipMin: Long, modifier: Modifier = Modifier) {
    val parked = s.gearPark != false
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    fun place(p: Any?) = when (p) { SimVehicle.HOME -> "집"; SimVehicle.WORK -> "회사"; else -> "-" }
    Column(
        modifier.background(Color(0xFF101828), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "휴대폰 시험용 차량 신호 · 지금 ${place(s.location)}" + (s.destination?.let { " → ${place(it)}" } ?: "") +
                if (skipMin > 0) " · +${skipMin}분" else "",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "차량용 음성(Pleos)은 차와 에뮬레이터에서만 작동합니다. 휴대폰에서는 " +
                (if (PhoneVoice.ELEVEN) "ElevenLabs를" else "기본 음성을") + " 사용하고 있습니다.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 380.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sim("집으로 경로", s.destination == SimVehicle.HOME, parked) { SimVehicle.route(toHome = true) }
            Sim("회사로 경로", s.destination == SimVehicle.WORK, parked) { SimVehicle.route(toHome = false) }
            Sim("집 도착", false, !parked) { SimVehicle.arriveHome() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sim("기어 P", s.gearPark == true) { SimVehicle.gear(park = true) }
            Sim("기어 D", s.gearPark == false) { SimVehicle.gear(park = false) }
            Sim("동승자 탑승", s.alone == false) { SimVehicle.passenger(on = s.alone != false) }
            Sim("+5분", false) { SimVehicle.skip(5) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("바로 해 보기", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
            Sim("맡기기", false) { SimVehicle.talkNow.tryEmit(TripEvent.ASK_ENTRUST) }
            Sim("돌려받기", false) { SimVehicle.talkNow.tryEmit(TripEvent.START_RETURN) }
            Sim("앱 초기화", false) { confirmReset = true }
        }
    }
    // 심사용: 맡긴 걱정·설정을 모두 지우고 앱을 처음 상태로(시스템이 앱을 종료함)
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("앱을 처음 상태로 되돌릴까요?") },
        text = { Text("맡긴 걱정과 설정이 모두 지워지고 앱이 종료돼요. 다시 열면 첫 화면부터 시작해요.") },
        confirmButton = { TextButton({ context.getSystemService(ActivityManager::class.java).clearApplicationUserData() }) { Text("초기화") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("취소") } },
    )
}

@Composable
private fun Sim(label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) =
    FilterChip(on, onClick, { Text(label, fontSize = 15.sp) }, enabled = enabled)
