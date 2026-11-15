package app.chinae

import app.chinae.data.Worry
import app.chinae.voice.DialogueEngine
import app.chinae.voice.DialogueEngine.Command
import app.chinae.worry.Category
import app.chinae.voice.Lines
import app.chinae.voice.Listener
import app.chinae.voice.Speaker
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

// org.json이 JVM에서는 빈 껍데기라 Robolectric으로 돌림
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DialogueTest {
    private fun asset(name: String) = File("src/main/assets/dialogue/$name.json").readText()
    private val banmal = Lines.parse(asset("banmal"))

    private class Fake(vararg replies: String?) : Speaker, Listener {
        private val replies = ArrayDeque(replies.toList())
        val said = mutableListOf<String>()
        override suspend fun say(text: String) { said += text }
        override suspend fun listen() = replies.removeFirstOrNull()
    }

    private fun engine(
        f: Fake, saved: MutableList<String> = mutableListOf(),
        items: List<Worry> = emptyList(), settled: MutableList<Pair<Long, Command>> = mutableListOf(),
    ) = DialogueEngine(f, f, { banmal }, entrust = { text -> saved += text; 2 }, alias = { "민준" },
        pending = { items }, settle = { id, c -> settled += id to c })

    private fun worry(id: Long, raw: String?, cat: Category, keys: List<String> = emptyList()) =
        Worry(id = id, createdAt = 0, rawText = raw, rawExpiresAt = 0, category = cat, topicKeys = keys, personKey = null)

    @Test fun returnRoundTrip() = runTest {
        val items = listOf(
            worry(1, "보고서 수치가 안 맞아.", Category.ACTIONABLE),
            worry(2, null, Category.UNCHANGEABLE, listOf("거래처")),
            worry(3, "메일 답장", Category.UNKNOWN),
            worry(4, "자료 정리", Category.ACTIONABLE),
        )
        val f = Fake("응", "바꿀 수 없는 일이야", "음…", "그만")
        val settled = mutableListOf<Pair<Long, Command>>()
        engine(f, items = items, settled = settled).runReturn()
        assertEquals(listOf(
            "좋은 아침, 민준. 어제 맡긴 거 네 개 돌려줄게.",
            "첫 번째, 보고서 수치가 안 맞아.", "오늘 처리할 거야?",
            "두 번째, 거래처.", "이건 바꿀 수 없는 걱정이야. 내려놓을래?",
            "세 번째, 메일 답장.", "오늘 처리할 거야?",
            "네 번째, 자료 정리.", "오늘 처리할 거야?",
        ), f.said)
        // 못 알아들은 3번과 그만한 4번은 손대지 않아 다음 출근길로 이월
        assertEquals(listOf(1L to Command.TODAY, 2L to Command.UNCHANGEABLE), settled)
    }

    @Test fun returnCommands() {
        assertEquals(Command.LET_GO, DialogueEngine.command("네", "ask_let_go"))
        assertEquals(Command.RESOLVED, DialogueEngine.command("어제 해결했어", "ask_today"))
        assertEquals(Command.ACTIONABLE, DialogueEngine.command("할 수 있는 일이야", "ask_let_go"))
        assertEquals(Command.LET_GO, DialogueEngine.command("그냥 둘래", "ask_today"))
        assertEquals(Command.TODAY, DialogueEngine.command("이따 할게", "ask_let_go"))
        assertEquals(null, DialogueEngine.command("글쎄", "ask_today"))
    }

    @Test fun nothingPendingStaysSilent() = runTest {
        val f = Fake()
        engine(f).runReturn()
        assertEquals(emptyList<String>(), f.said)
    }

    @Test fun entrustRoundTrip() = runTest {
        val f = Fake("보고서 수치가 안 맞아 그리고 팀장님 메일에 답장 못 했어")
        val saved = mutableListOf<String>()
        engine(f, saved).runEntrust()
        assertEquals(listOf("오늘 두고 가고 싶은 거 있으면 나한테 맡겨.", "두 개, 잘 맡아 둘게."), f.said)
        assertEquals(1, saved.size)
    }

    @Test fun silenceOrDeclineEndsQuietly() = runTest {
        for (reply in listOf(null, "", "없어.", "아니요")) {
            val f = Fake(reply)
            val saved = mutableListOf<String>()
            engine(f, saved).runEntrust()
            assertEquals(1, f.said.size) // 질문만 하고 끝
            assertEquals(emptyList<String>(), saved)
        }
        assertEquals(false, DialogueEngine.isDecline("나중에 보고서 다시 봐야 해"))
    }

    @Test fun linesFillAndDropAlias() {
        val l = Lines(mapOf("greet" to "좋은 아침, {alias}."))
        assertEquals("좋은 아침, 민준.", l["greet", mapOf("alias" to "민준")])
        assertEquals("좋은 아침.", l["greet", mapOf("alias" to null)])
        assertEquals("11개", DialogueEngine.count(11))
    }

    @Test fun tonesHaveSameKeys() {
        fun keys(n: String) = JSONObject(asset(n)).keys().asSequence().toSet()
        assertEquals(keys("banmal"), keys("haeyo"))
    }
}
