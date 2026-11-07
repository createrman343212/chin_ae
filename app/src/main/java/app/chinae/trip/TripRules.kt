package app.chinae.trip

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

enum class TripType { HOME, WORK, FREE }

data class Places(val home: LatLng?, val work: LatLng?)

/** masterplan 3.2, 3.4 판정 규칙. 값은 모두 [초기값] */
object TripRules {
    const val PLACE_RADIUS_M = 150.0
    val HOME_DEPART_HOURS = 15..23
    val WORK_DEPART_HOURS = 5..11

    fun distanceM(a: LatLng, b: LatLng): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun near(p: LatLng?, place: LatLng?) =
        p != null && place != null && distanceM(p, place) <= PLACE_RADIUS_M

    // shortcut: 2026-12 ~ 2027-12 공휴일만 내장(대체공휴일 포함, 공식 달력 대조 [확인 필요]), 2028 이후 쓰려면 갱신
    private val HOLIDAYS = setOf(
        "2026-12-25",
        "2027-01-01", "2027-02-06", "2027-02-07", "2027-02-08", "2027-02-09",
        "2027-03-01", "2027-05-05", "2027-05-13", "2027-06-06", "2027-08-15", "2027-08-16",
        "2027-09-14", "2027-09-15", "2027-09-16", "2027-10-03", "2027-10-04",
        "2027-10-09", "2027-10-11", "2027-12-25", "2027-12-27",
    ).map(LocalDate::parse).toSet()

    fun isWorkday(
        date: LocalDate,
        workdays: Set<DayOfWeek> = DayOfWeek.entries.filter { it.value <= 5 }.toSet(),
    ) = date.dayOfWeek in workdays && date !in HOLIDAYS

    /** 출발 시점 판정. 쉬는 날에는 먼저 묻지 않으므로 FREE */
    fun classify(
        departAt: LocalDateTime,
        start: LatLng?,
        destination: LatLng?,
        places: Places,
        workdays: Set<DayOfWeek> = DayOfWeek.entries.filter { it.value <= 5 }.toSet(),
    ): TripType {
        if (!isWorkday(departAt.toLocalDate(), workdays)) return TripType.FREE
        if (near(destination, places.home)) return TripType.HOME
        if (near(destination, places.work)) return TripType.WORK
        val h = departAt.hour
        if (near(start, places.work) && h in HOME_DEPART_HOURS) return TripType.HOME
        if (near(start, places.home) && h in WORK_DEPART_HOURS) return TripType.WORK
        return TripType.FREE
    }

    fun shouldAskEntrust(elapsedMin: Int, remainMin: Int?, alone: Boolean, alreadyAsked: Boolean) =
        alone && !alreadyAsked && elapsedMin >= 5 && remainMin != null && remainMin >= 8

    fun shouldStartReturn(elapsedMin: Int, remainMin: Int?, alone: Boolean, alreadyAsked: Boolean, pending: Int) =
        alone && !alreadyAsked && pending > 0 && elapsedMin >= 3 && remainMin != null && remainMin >= 10

    fun isHomeArrival(current: LatLng?, home: LatLng?, gearPark: Boolean) = gearPark && near(current, home)
}
