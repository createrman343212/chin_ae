package app.chinae

import app.chinae.trip.LatLng
import app.chinae.trip.Places
import app.chinae.trip.TripRules
import app.chinae.trip.TripType
import app.chinae.voice.Command
import app.chinae.voice.CommandParser
import app.chinae.worry.Category
import app.chinae.worry.Classifier
import app.chinae.worry.KeyExtractor
import app.chinae.worry.Segmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TripRulesTest {
    private val home = LatLng(37.5000, 127.0000)
    private val work = LatLng(37.4000, 127.1000)
    private val places = Places(home, work)
    private val mon = LocalDateTime.of(2026, 11, 9, 18, 0)
    private fun at(h: Int) = mon.withHour(h)

    @Test fun radius() {
        assertTrue(TripRules.near(LatLng(37.5010, 127.0), home)) // 약 111m
        assertFalse(TripRules.near(LatLng(37.5020, 127.0), home)) // 약 222m
        assertFalse(TripRules.near(null, home))
    }

    @Test fun destinationWins() {
        assertEquals(TripType.HOME, TripRules.classify(at(8), home, home, places))
        assertEquals(TripType.WORK, TripRules.classify(at(18), work, work, places))
    }

    @Test fun startAndHourWithoutDestination() {
        assertEquals(TripType.HOME, TripRules.classify(at(18), work, null, places))
        assertEquals(TripType.WORK, TripRules.classify(at(8), home, null, places))
        assertEquals(TripType.FREE, TripRules.classify(at(18), home, null, places))
        assertEquals(TripType.FREE, TripRules.classify(at(13), work, null, places))
    }

    @Test fun offDays() {
        assertEquals(TripType.FREE, TripRules.classify(LocalDateTime.of(2026, 11, 7, 18, 0), work, home, places)) // 토요일
        assertEquals(TripType.FREE, TripRules.classify(LocalDateTime.of(2026, 12, 25, 8, 0), home, work, places)) // 성탄절
    }

    @Test fun askTiming() {
        assertTrue(TripRules.shouldAskEntrust(5, 8, alone = true, alreadyAsked = false))
        assertFalse(TripRules.shouldAskEntrust(4, 20, alone = true, alreadyAsked = false))
        assertFalse(TripRules.shouldAskEntrust(5, 7, alone = true, alreadyAsked = false))
        assertFalse(TripRules.shouldAskEntrust(5, null, alone = true, alreadyAsked = false))
        assertFalse(TripRules.shouldAskEntrust(5, 20, alone = false, alreadyAsked = false))
        assertFalse(TripRules.shouldAskEntrust(5, 20, alone = true, alreadyAsked = true))

        assertTrue(TripRules.shouldStartReturn(3, 10, alone = true, alreadyAsked = false, pending = 1))
        assertFalse(TripRules.shouldStartReturn(3, 10, alone = true, alreadyAsked = false, pending = 0))
        assertFalse(TripRules.shouldStartReturn(3, 9, alone = true, alreadyAsked = false, pending = 1))
    }

    @Test fun homeArrival() {
        assertTrue(TripRules.isHomeArrival(home, home, gearPark = true))
        assertFalse(TripRules.isHomeArrival(home, home, gearPark = false))
        assertFalse(TripRules.isHomeArrival(work, home, gearPark = true))
    }
}

class WorryTest {
    @Test fun segmenter() {
        assertEquals(
            listOf("보고서를 내일까지 보내야 해", "팀장님이 또 무리하게 일정을 잡았어"),
            Segmenter.split("보고서를 내일까지 보내야 해. 그리고 팀장님이 또 무리하게 일정을 잡았어"),
        )
        // 에뮬레이터 ON_DEVICE STT 실제 출력(마침표 없음)
        assertEquals(
            listOf("내일 오전까지 보고서를 보내야 해", "팀장님이 또 무리하게 일정을 잡았어"),
            Segmenter.split("내일 오전까지 보고서를 보내야 해 그리고 팀장님이 또 무리하게 일정을 잡았어"),
        )
        assertEquals(
            listOf("회의 자료 정리해야 해", "메일도 보내야 돼"),
            Segmenter.split("회의 자료 정리해야 해 또 메일도 보내야 돼"),
        )
        assertEquals(listOf("음 보고서 마감이 내일이야"), Segmenter.split("음. 보고서 마감이 내일이야"))
        assertEquals(emptyList<String>(), Segmenter.split("  "))
    }

    @Test fun classifier() {
        assertEquals(Category.ACTIONABLE, Classifier.classify("내일 오전까지 보고서 보내야 해"))
        assertEquals(Category.UNCHANGEABLE, Classifier.classify("팀장님이 왜 맨날 무시하는지 서운해"))
        assertEquals(Category.UNKNOWN, Classifier.classify("그냥 좀 피곤해"))
        assertEquals(Category.UNCHANGEABLE, Classifier.classify("내일 비 온다는데 출근길 막힐까 봐 걱정돼"))
        assertEquals(Category.UNCHANGEABLE, Classifier.classify("내일 날씨가 안 좋다는데 걱정이야"))
        assertEquals(Category.ACTIONABLE, Classifier.classify("내일 발표 준비가 걱정돼"))
        assertEquals(
            Category.UNCHANGEABLE,
            Classifier.classify("내일까지 보고서 보내야 해", listOf("보고서"), mapOf("보고서" to Category.UNCHANGEABLE)),
        )
    }

    @Test fun keys() {
        assertEquals(listOf("보고서", "메일"), KeyExtractor.topicKeys("보고서를 팀장님에게 메일로 보내야 해"))
        assertEquals(listOf("세미나"), KeyExtractor.topicKeys("세미나에서 말해야 해", setOf("세미나")))
        assertEquals("팀장님", KeyExtractor.personKey("팀장님이 화를 냈어"))
        assertEquals("민수님", KeyExtractor.personKey("민수님이 화를 냈어"))
        assertNull(KeyExtractor.personKey("그냥 피곤해"))
    }
}

class CommandParserTest {
    @Test fun parse() {
        assertEquals(Command.RESOLVED, CommandParser.parse("그건 다 해결됐어"))
        assertEquals(Command.MARK_UNCHANGEABLE, CommandParser.parse("그건 바꿀 수 없는 일이야"))
        assertEquals(Command.MARK_ACTIONABLE, CommandParser.parse("할 수 있는 일이야"))
        assertEquals(Command.TODAY, CommandParser.parse("오늘 처리할게"))
        assertEquals(Command.LET_GO, CommandParser.parse("이건 내려놓을래"))
        assertEquals(Command.STOP, CommandParser.parse("그만하자"))
        assertNull(CommandParser.parse("뭐라고?"))
        assertNull(CommandParser.parse("   "))
    }
}
