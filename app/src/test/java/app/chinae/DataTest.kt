package app.chinae

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.chinae.data.ChinaeDb
import app.chinae.data.Settings
import app.chinae.data.WorryRepository
import app.chinae.data.WorryStatus
import app.chinae.trip.LatLng
import app.chinae.trip.Places
import app.chinae.trip.PlaceStore
import app.chinae.worry.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataTest {
    // 암호화(SQLCipher, Keystore)는 JVM에서 못 돌려서 평문 인메모리 DB로 DAO와 보존 규칙만 확인. 암호화는 에뮬레이터에서 확인
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChinaeDb::class.java)
        .allowMainThreadQueries().build()
    private val repo = WorryRepository(db)
    private val day = TimeUnit.DAYS.toMillis(1)
    private val t0 = 1_800_000_000_000L

    @After fun close() = db.close()

    @Test fun entrustSplitsAndCounts() = runTest {
        val saved = repo.entrust("내일 오전까지 보고서를 보내야 해 그리고 팀장님이 또 무리하게 일정을 잡았어", t0)
        assertEquals(2, saved.size)
        assertEquals(Category.ACTIONABLE, saved[0].category)
        assertEquals("팀장님", saved[1].personKey)
        assertEquals(2, repo.pendingCount.first())
    }

    @Test fun resolvingClearsRawImmediately() = runTest {
        val (a, b) = repo.entrust("보고서를 보내야 해 그리고 회의 자료도 준비해야 해", t0)
        db.worries().setStatus(a.id, WorryStatus.RESOLVED, t0 + 1)
        db.worries().setStatus(b.id, WorryStatus.TODAY, t0 + 1)
        val all = db.worries().since(0).associateBy { it.id }
        assertNull(all.getValue(a.id).rawText)
        assertEquals(b.rawText, all.getValue(b.id).rawText)
        assertEquals(0, repo.pendingCount.first())
    }

    @Test fun retentionBoundaries() = runTest {
        val (w) = repo.entrust("보고서를 보내야 해", t0)
        repo.purge(t0 + 7 * day - 1)
        assertEquals(w.rawText, db.worries().since(0).single().rawText)
        repo.purge(t0 + 7 * day)
        assertNull(db.worries().since(0).single().rawText)
        repo.purge(t0 + 90 * day)
        assertEquals(1, db.worries().since(0).size) // 90일째는 남고
        repo.purge(t0 + 90 * day + 1)
        assertEquals(0, db.worries().since(0).size) // 넘으면 행 삭제
    }

    @Test fun correctionWinsNextTime() = runTest {
        val (w) = repo.entrust("회의 때문에 머리 아파", t0)
        db.worries().correct(w.id, Category.UNCHANGEABLE)
        assertEquals(Category.UNCHANGEABLE, repo.entrust("내일 회의 준비해야 해", t0 + 1).single().category)
    }

    @Test fun settingsAndManualPlacesWin() {
        val s = Settings(ApplicationProvider.getApplicationContext())
        assertEquals(5, s.workdays.size)
        s.workdays = setOf(DayOfWeek.SATURDAY)
        s.manualPlaces = Places(LatLng(37.5, 127.0), null)
        assertEquals(setOf(DayOfWeek.SATURDAY), s.workdays)
        val store = PlaceStore { s.manualPlaces }
        store.updateFromBookmarks(emptyList())
        assertEquals(Places(LatLng(37.5, 127.0), null), store.places)
        s.clear()
    }
}
