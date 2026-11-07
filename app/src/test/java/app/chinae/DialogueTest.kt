package app.chinae

import app.chinae.voice.DialogueEngine
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

    private class Fake(private val reply: String?) : Speaker, Listener {
        val said = mutableListOf<String>()
        override suspend fun say(text: String) { said += text }
        override suspend fun listen() = reply
    }

    private fun engine(f: Fake, saved: MutableList<String> = mutableListOf()) =
        DialogueEngine(f, f, { banmal }) { text -> saved += text; 2 }

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
