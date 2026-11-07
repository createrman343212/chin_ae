package app.chinae.trip

import app.chinae.vehicle.VehicleSnapshot
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime

enum class TripState { IDLE, COMMUTE_HOME, COMMUTE_WORK, FREE_DRIVE, ENTRUST_ASK, RETURN_ASK, LOCK_RITUAL }

enum class TripEvent { ASK_ENTRUST, START_RETURN, START_LOCK_RITUAL, STOP_SPEAKING }

/**
 * masterplan 3.1 상태 머신. 신호가 바뀔 때마다 update를 부르고, 대화가 끝나면 conversationEnded.
 * HomeParked는 머무르지 않는 판정 단계라 상태로 두지 않음
 */
class TripStateMachine(
    private val places: () -> Places,
    private val workdays: () -> Set<DayOfWeek> = { DayOfWeek.entries.filter { it.value <= 5 }.toSet() },
) {
    var state = TripState.IDLE
        private set
    private var departAt: LocalDateTime? = null
    private var asked = false // 출퇴근당 먼저 말 거는 건 1회
    private var last = VehicleSnapshot()

    fun update(s: VehicleSnapshot, now: LocalDateTime, pending: Int, reviewDay: Boolean): List<TripEvent> {
        val departed = last.gearPark == true && s.gearPark == false
        val parked = last.gearPark == false && s.gearPark == true
        last = s
        val out = mutableListOf<TripEvent>()

        if (s.alone == false && state in TALKING) {
            out += TripEvent.STOP_SPEAKING
            conversationEnded()
        }
        val elapsed = departAt?.let { Duration.between(it, now).toMinutes().toInt() } ?: 0
        val alone = s.alone == true

        when (state) {
            TripState.IDLE -> if (departed) {
                departAt = now
                asked = false
                state = when (TripRules.classify(now, s.location, s.destination, places(), workdays())) {
                    TripType.HOME -> TripState.COMMUTE_HOME
                    TripType.WORK -> TripState.COMMUTE_WORK
                    TripType.FREE -> TripState.FREE_DRIVE
                }
            }
            TripState.COMMUTE_HOME -> when {
                parked && TripRules.isHomeArrival(s.location, places().home, gearPark = true) ->
                    if (pending > 0 || reviewDay) {
                        state = TripState.LOCK_RITUAL
                        out += TripEvent.START_LOCK_RITUAL
                    } else state = TripState.IDLE
                TripRules.shouldAskEntrust(elapsed, s.remainMin, alone, asked) -> {
                    asked = true
                    state = TripState.ENTRUST_ASK
                    out += TripEvent.ASK_ENTRUST
                }
            }
            TripState.COMMUTE_WORK -> when {
                parked && TripRules.near(s.location, places().work) -> state = TripState.IDLE
                TripRules.shouldStartReturn(elapsed, s.remainMin, alone, asked, pending) -> {
                    asked = true
                    state = TripState.RETURN_ASK
                    out += TripEvent.START_RETURN
                }
            }
            // shortcut: 시동 꺼짐 신호를 아직 안 받아서 기어 P를 주행 종료로 봄. 중간 정차가 문제 되면 시동 신호 추가
            TripState.FREE_DRIVE -> if (parked) state = TripState.IDLE
            TripState.LOCK_RITUAL -> if (s.gearPark == false) {
                out += TripEvent.STOP_SPEAKING
                state = TripState.IDLE
            }
            TripState.ENTRUST_ASK, TripState.RETURN_ASK -> Unit // 대화 계층이 conversationEnded로 끝냄
        }
        return out
    }

    /** 맡기기·돌려받기·잠금 의식이 끝나거나 중단됐을 때 */
    fun conversationEnded() {
        state = when (state) {
            TripState.ENTRUST_ASK -> TripState.COMMUTE_HOME
            TripState.RETURN_ASK -> TripState.COMMUTE_WORK
            TripState.LOCK_RITUAL -> TripState.IDLE
            else -> state
        }
    }

    private companion object {
        val TALKING = setOf(TripState.ENTRUST_ASK, TripState.RETURN_ASK, TripState.LOCK_RITUAL)
    }
}
