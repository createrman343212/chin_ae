package app.chinae.vehicle

import android.content.Context
import android.content.pm.PackageManager
import app.chinae.trip.LatLng
import app.chinae.trip.Places
import app.chinae.trip.TripEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 차가 아닌 기기(휴대폰)에서 차량 신호 대신 쓰는 화면 버튼 상태. 서비스와 화면이 같은 프로세스라 전역 하나.
 * 집·회사는 고정 좌표를 쓰고, 출발하면 남은 시간 20분 경로가 잡힌 것으로 봄
 */
object SimVehicle {
    val HOME = LatLng(37.4979, 127.0276)
    val WORK = LatLng(37.4127, 127.0947)
    val places = Places(HOME, WORK)
    const val ROUTE_MIN = 20

    val snapshot = MutableStateFlow(VehicleSnapshot(gearPark = true, alone = true, location = WORK))
    /** 시간 건너뛰기(분). 출발 5분 뒤 같은 판정을 기다리지 않고 보려고 씀 */
    val skipMin = MutableStateFlow(0L)

    fun isCar(context: Context) = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    /** 목적지를 정함. 정차 중에만 바꿈(출발점은 반대쪽) */
    fun route(toHome: Boolean) = snapshot.update {
        if (it.gearPark == false) it
        else it.copy(location = if (toHome) WORK else HOME, destination = if (toHome) HOME else WORK, remainMin = ROUTE_MIN)
    }

    /** P면 목적지에 도착한 것으로 봄(경로가 있으면 위치를 목적지로 옮기고 경로를 지움) */
    fun gear(park: Boolean) = snapshot.update {
        if (park && it.gearPark == false && it.destination != null)
            it.copy(gearPark = true, location = it.destination, destination = null, remainMin = null)
        else it.copy(gearPark = park)
    }

    /** 어디로 가던 중이든 바로 집에 도착해 P에 둠(잠금 의식 시험용) */
    fun arriveHome() = snapshot.update {
        if (it.gearPark != false) it else it.copy(gearPark = true, location = HOME, destination = null, remainMin = null)
    }

    fun passenger(on: Boolean) = snapshot.update { it.copy(alone = !on) }

    fun skip(min: Long) = skipMin.update { it + min }

    /** 기다리지 않고 바로 맡기기·돌려받기를 시작하라는 신호(서비스가 받음) */
    val talkNow = MutableSharedFlow<TripEvent>(extraBufferCapacity = 1)
}
