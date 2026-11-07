package app.chinae.vehicle

import app.chinae.trip.LatLng

/** 판정에 쓰는 차량 신호 묶음. null은 신호가 아직 없거나 차량이 지원하지 않음 */
data class VehicleSnapshot(
    val gearPark: Boolean? = null,
    val alone: Boolean? = null,
    val location: LatLng? = null,
    val destination: LatLng? = null,
    val remainMin: Int? = null,
)
