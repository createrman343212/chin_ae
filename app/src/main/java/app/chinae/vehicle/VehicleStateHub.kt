package app.chinae.vehicle

import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.chinae.trip.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** 좌석, 기어, 내비 신호를 VehicleSnapshot 하나로 합침 (masterplan 2장) */
class VehicleStateHub(
    context: Context,
    /** 내비 즐겨찾기(집, 회사)를 받을 곳. 시작할 때와 즐겨찾기가 바뀔 때 옴 */
    private val onBookmarks: (List<BookmarkInfo>) -> Unit = {},
) {
    private val vehicle = Vehicle(context)
    private val navi = NaviHelper(context)
    private val main = Handler(Looper.getMainLooper())
    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    val snapshot: StateFlow<VehicleSnapshot> = _snapshot

    /** 운전석을 뺀 좌석별 탑승 여부. 콜백 스레드가 섞일 수 있어 동기화 */
    private val others = mutableMapOf<SeatArea, Boolean>()

    private val poll = object : Runnable {
        // 내비는 요청해야 값을 보내므로 주기적으로 물음
        override fun run() {
            runCatching { navi.getCurrentLocationInfo(); navi.getDestinationInfo() }
                .onFailure { Log.w(TAG, "navi poll", it) }
            main.postDelayed(this, NAVI_POLL_MS)
        }
    }

    fun start() {
        vehicle.initialize()
        vehicle.getGear().registerGearSelection(object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                if (gear == null || gear == VehicleGear.GEAR_UNKNOWN) return
                _snapshot.update { it.copy(gearPark = gear == VehicleGear.GEAR_PARK) }
            }
            override fun onFailed(e: Exception) { Log.w(TAG, "gear", e) }
        })
        watchSeats()
        navi.addListener(object : NaviHelperEventListener {
            override fun onCurrentLocationInfo(info: CurrentLocationInfo) =
                _snapshot.update { it.copy(location = latLng(info.latitude, info.longitude)) }
            override fun onDestinationInfo(info: DestinationInfo) {
                val dest = latLng(info.latitude, info.longitude)
                // remainTime은 초 단위(에뮬레이터 실측: 16km 경로에 1260)
                _snapshot.update { it.copy(destination = dest, remainMin = dest?.let { info.remainTime / 60 }) }
            }
            override fun onRouteEnded() = clearRoute()
            override fun onRouteCancelled() = clearRoute()
            override fun onBookmarkInfo(list: List<BookmarkInfo>) = onBookmarks(list)
            override fun onBookMarkChanged() = navi.getBookmarkInfo()
        })
        navi.initialize({ navi.getBookmarkInfo(); main.post(poll) }, { e -> Log.w(TAG, "navi init", e) })
    }

    fun stop() {
        main.removeCallbacks(poll)
        runCatching { vehicle.release(); navi.release() }
    }

    private fun watchSeats() {
        val seat = vehicle.getSeat()
        seat.checkSeatOccupancyCapabilities({ areas: List<SeatArea> ->
            areas.filterNot { SeatValue.ROW_1_LEFT in it.values }.forEach { area ->
                seat.registerSeatOccupancy(area, object : SeatOccupancyListener {
                    override fun onSeatOccupancyUpdated(occupied: Boolean?) {
                        if (occupied == null) return
                        val alone = synchronized(others) {
                            others[area] = occupied != SEAT_OCCUPANCY_INVERTED
                            others.values.none { it }
                        }
                        _snapshot.update { it.copy(alone = alone) }
                    }
                    override fun onFailed(e: Exception) { Log.w(TAG, "seat $area", e) }
                })
            }
        }, { e -> Log.w(TAG, "seat capability", e) }) // 미지원이면 alone은 null로 남고 UI에서 1회 확인(3.2)
    }

    private fun clearRoute() = _snapshot.update { it.copy(destination = null, remainMin = null) }

    companion object {
        private const val TAG = "VehicleStateHub"
        private const val NAVI_POLL_MS = 10_000L

        /**
         * 에뮬레이터 Virtual Console에서 Occupied를 주면 SDK가 false를 줌(4좌석 모두, docs/context.md).
         * 콘솔 버그인지 SDK 의미인지 미확정. 실차나 공식 답변으로 확인되면 이 값만 바꿈
         */
        const val SEAT_OCCUPANCY_INVERTED = true

        /** 좌표가 0,0이면 값 없음으로 봄(에뮬레이터 기본값) */
        fun latLng(lat: Double, lng: Double) = if (lat == 0.0 && lng == 0.0) null else LatLng(lat, lng)
    }
}
