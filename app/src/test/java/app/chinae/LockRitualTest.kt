package app.chinae

import app.chinae.ritual.Ambient
import app.chinae.ritual.LockRitual
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LockRitualTest {
    private val log = mutableListOf<String>()
    private val ambient = Ambient { c -> log += "paint ${Integer.toHexString(c)}"; { log += "restore" } }
    private fun ritual() = LockRitual(ambient, { log += "chime" })

    @Test fun lightChimeSilenceRestore() = runTest {
        val job = launch { ritual().run() }
        runCurrent()
        assertEquals(listOf("paint ffffb46b", "chime"), log)
        advanceTimeBy(9_999); runCurrent()
        assertEquals(2, log.size) // 10초 정적 동안 아무것도 안 함
        advanceTimeBy(2); runCurrent()
        assertEquals("restore", log.last())
        assertEquals(true, job.isCompleted)
    }

    @Test fun cancelRestoresLight() = runTest {
        val job = launch { ritual().run() }
        advanceTimeBy(3_000)
        job.cancel(); runCurrent() // 기어가 P를 벗어남
        assertEquals(listOf("paint ffffb46b", "chime", "restore"), log)
    }

    @Test fun noLightStillChimes() = runTest {
        LockRitual({ null }, { log += "chime" }, silenceMs = 1).run()
        assertEquals(listOf("chime"), log)
    }
}
