package app.chinae

import app.chinae.trip.LatLng
import app.chinae.trip.Places
import app.chinae.trip.TripEvent.*
import app.chinae.trip.TripState
import app.chinae.trip.TripStateMachine
import app.chinae.vehicle.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class TripStateMachineTest {
    private val home = LatLng(37.50, 127.00)
    private val work = LatLng(37.52, 127.05)
    private val mon18 = LocalDateTime.of(2026, 11, 9, 18, 0)
    private val mon8 = LocalDateTime.of(2026, 11, 9, 8, 0)
    private fun fsm() = TripStateMachine({ Places(home, work) })
    private val parkedAtWork = VehicleSnapshot(gearPark = true, alone = true, location = work)

    @Test fun commuteHomeAskThenLockRitual() {
        val m = fsm()
        m.update(parkedAtWork, mon18, 0, false)
        m.update(parkedAtWork.copy(gearPark = false, remainMin = 20), mon18, 0, false)
        assertEquals(TripState.COMMUTE_HOME, m.state)

        val driving = parkedAtWork.copy(gearPark = false, remainMin = 15)
        assertEquals(emptyList<Any>(), m.update(driving, mon18.plusMinutes(4), 0, false))
        assertEquals(listOf(ASK_ENTRUST), m.update(driving, mon18.plusMinutes(5), 0, false))
        m.conversationEnded()
        assertEquals(emptyList<Any>(), m.update(driving, mon18.plusMinutes(6), 1, false)) // 1회만

        val atHome = driving.copy(location = home, remainMin = 0)
        m.update(atHome, mon18.plusMinutes(20), 1, false)
        assertEquals(listOf(START_LOCK_RITUAL), m.update(atHome.copy(gearPark = true), mon18.plusMinutes(21), 1, false))
        assertEquals(listOf(STOP_SPEAKING), m.update(atHome.copy(gearPark = false), mon18.plusMinutes(21), 1, false))
        assertEquals(TripState.IDLE, m.state)
    }

    @Test fun homeArrivalWithoutPendingSkipsRitual() {
        val m = fsm()
        m.update(parkedAtWork, mon18, 0, false)
        m.update(parkedAtWork.copy(gearPark = false), mon18, 0, false)
        val atHome = parkedAtWork.copy(gearPark = false, location = home)
        m.update(atHome, mon18.plusMinutes(20), 0, false)
        assertEquals(emptyList<Any>(), m.update(atHome.copy(gearPark = true), mon18.plusMinutes(20), 0, false))
        assertEquals(TripState.IDLE, m.state)
    }

    @Test fun passengerStopsTalkAndBlocksAsk() {
        val m = fsm()
        val atHome = VehicleSnapshot(gearPark = true, alone = true, location = home)
        m.update(atHome, mon8, 2, false)
        val driving = atHome.copy(gearPark = false, remainMin = 25)
        m.update(driving, mon8, 2, false)
        assertEquals(TripState.COMMUTE_WORK, m.state)

        val withPassenger = driving.copy(alone = false)
        assertEquals(emptyList<Any>(), m.update(withPassenger, mon8.plusMinutes(3), 2, false))
        assertEquals(listOf(START_RETURN), m.update(driving, mon8.plusMinutes(4), 2, false))
        assertEquals(listOf(STOP_SPEAKING), m.update(withPassenger, mon8.plusMinutes(5), 2, false))
        assertEquals(TripState.COMMUTE_WORK, m.state)

        m.update(withPassenger.copy(location = work), mon8.plusMinutes(30), 2, false)
        m.update(withPassenger.copy(location = work, gearPark = true), mon8.plusMinutes(30), 2, false)
        assertEquals(TripState.IDLE, m.state)
    }

    @Test fun unknownSeatsNeverAsk() {
        val m = fsm()
        val s = parkedAtWork.copy(alone = null)
        m.update(s, mon18, 0, false)
        m.update(s.copy(gearPark = false, remainMin = 20), mon18, 0, false)
        assertEquals(emptyList<Any>(), m.update(s.copy(gearPark = false, remainMin = 15), mon18.plusMinutes(6), 0, false))
    }
}

class PlaceStoreTest {
    private fun bm(type: ai.pleos.playground.navi.constants.BookmarkType, lat: Double, lng: Double) =
        ai.pleos.playground.navi.data.BookmarkInfo(lat, lng, null, null, "", "", "", "", type, "")

    @Test fun homeOfficeAndEmptyDefaults() {
        val p = app.chinae.trip.PlaceStore.fromBookmarks(listOf(
            bm(ai.pleos.playground.navi.constants.BookmarkType.HOME, 37.5, 127.0),
            bm(ai.pleos.playground.navi.constants.BookmarkType.OFFICE, 0.0, 0.0), // 에뮬레이터 미등록 기본값
        ))
        assertEquals(LatLng(37.5, 127.0), p.home)
        assertEquals(null, p.work)
    }
}
