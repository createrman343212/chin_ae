package app.chinae.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimVehicleTest {
    @Test fun `경로를 정하고 출발해 P에 두면 목적지에 도착한 것으로 봄`() {
        SimVehicle.snapshot.value = VehicleSnapshot(gearPark = true, alone = true, location = SimVehicle.WORK)
        SimVehicle.route(toHome = true)
        SimVehicle.gear(park = false)
        SimVehicle.route(toHome = false) // 주행 중에는 목적지를 못 바꿈
        assertEquals(SimVehicle.HOME, SimVehicle.snapshot.value.destination)
        SimVehicle.gear(park = true)
        val s = SimVehicle.snapshot.value
        assertEquals(SimVehicle.HOME, s.location)
        assertNull(s.destination)
        assertNull(s.remainMin)
    }

    @Test fun `집 도착은 주행 중에만 집 위치와 P로 바꿈`() {
        SimVehicle.snapshot.value = VehicleSnapshot(gearPark = true, alone = true, location = SimVehicle.WORK)
        SimVehicle.arriveHome()
        assertEquals(SimVehicle.WORK, SimVehicle.snapshot.value.location)
        SimVehicle.route(toHome = false); SimVehicle.gear(park = false)
        SimVehicle.arriveHome()
        val s = SimVehicle.snapshot.value
        assertEquals(true, s.gearPark)
        assertEquals(SimVehicle.HOME, s.location)
        assertNull(s.destination)
    }
}
