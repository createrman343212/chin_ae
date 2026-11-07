package app.chinae.voice

import app.chinae.data.Worry
import app.chinae.worry.Category
import org.json.JSONObject

interface Speaker { suspend fun say(text: String) }

/** 한 번 듣기. 무응답이나 인식 실패면 null */
interface Listener { suspend fun listen(): String? }

/** 말투별 대사 묶음 assets/dialogue/{banmal,haeyo}.json (masterplan 4.1) */
class Lines(private val map: Map<String, String>) {
    /** {name} 자리에 값을 넣음. 값이 없는 {alias}는 앞의 쉼표와 함께 뺌 ("좋은 아침, {alias}." → "좋은 아침.") */
    operator fun get(key: String, vars: Map<String, String?> = emptyMap()): String =
        vars.entries.fold(map.getValue(key)) { s, (k, v) ->
            if (v == null) s.replace(Regex(""",?\s*\{$k\}"""), "") else s.replace("{$k}", v)
        }

    companion object {
        fun parse(json: String) = JSONObject(json).let { o -> Lines(o.keys().asSequence().associateWith(o::getString)) }
    }
}

/** 대화 흐름. 차량 신호와 SDK를 모르고 Speaker, Listener만 씀 (masterplan 4장) */
class DialogueEngine(
    private val speaker: Speaker,
    private val listener: Listener,
    private val lines: () -> Lines,
    /** 발화를 걱정으로 나눠 저장하고 저장한 건수를 돌려줌 */
    private val entrust: suspend (String) -> Int,
    private val alias: () -> String?,
    /** 돌려줄 걱정, 오래된 순 */
    private val pending: suspend () -> List<Worry>,
    /** 정리 명령을 걱정 하나에 반영 */
    private val settle: suspend (Long, Command) -> Unit,
) {
    /** 4.4 음성 명령 사전 */
    enum class Command { RESOLVED, TODAY, LET_GO, ACTIONABLE, UNCHANGEABLE, STOP }

    /** 4.2 맡기기: 질문 → 듣기 → 저장 → 건수만 말함(내용은 되읽지 않음) */
    suspend fun runEntrust() {
        val l = lines()
        speaker.say(l["ask_entrust"])
        val text = listener.listen()?.trim()
        if (text.isNullOrEmpty() || isDecline(text)) return // 빈 결과나 거절이면 아무 말 없이 끝
        val n = entrust(text)
        if (n > 0) speaker.say(l["ack_entrust", mapOf("n" to count(n))])
    }

    /** 4.3 돌려받기: 인사 → 한 건씩 들려주고 분류에 맞는 질문 → 명령 반영 */
    suspend fun runReturn() {
        val items = pending()
        if (items.isEmpty()) return
        val l = lines()
        speaker.say(l["greet_return", mapOf("alias" to alias(), "n" to count(items.size))])
        for ((i, w) in items.withIndex()) {
            // 원문이 7일 지나 지워졌으면 주제어만(6장)
            val what = (w.rawText ?: w.topicKeys.joinToString(" ")).trimEnd(' ', '.', '?', '!').ifBlank { null }
            speaker.say(l["return_item", mapOf("nth" to ordinal(i + 1), "text" to what)])
            val ask = if (w.category == Category.UNCHANGEABLE) "ask_let_go" else "ask_today" // 미분류는 오늘 처리부터 물음(5.2)
            speaker.say(l[ask])
            val cmd = command(listener.listen().orEmpty(), ask) ?: continue // 인식 실패: 다시 묻지 않고 다음 출근길로 이월
            if (cmd == Command.STOP) return // 남은 항목은 그대로 다음 출근길로
            settle(w.id, cmd)
        }
    }

    companion object {
        private fun norm(text: String) = text.replace(Regex("""[\s.,!?]"""), "")

        // 질문에 대한 짧은 긍정은 물어본 쪽 명령으로 봄
        private val YES = setOf("응", "어", "그래", "네", "예", "좋아", "그럴게", "그럴게요")
        // 순서가 우선순위. "할 수 있는 일"을 "할게"보다 먼저 봐야 함
        private val PHRASES = listOf(
            listOf("바꿀수없") to Command.UNCHANGEABLE,
            listOf("할수있") to Command.ACTIONABLE,
            listOf("해결", "끝났", "끝냈") to Command.RESOLVED,
            listOf("내려놓", "그냥둘") to Command.LET_GO,
            listOf("오늘", "이따") to Command.TODAY,
            listOf("그만", "나중에") to Command.STOP,
        )

        /** 발화 → 명령. 못 알아들으면 null */
        fun command(text: String, ask: String): Command? {
            val t = norm(text)
            if (t in YES) return if (ask == "ask_let_go") Command.LET_GO else Command.TODAY
            return PHRASES.firstOrNull { (ps, _) -> ps.any(t::contains) }?.second
        }

        /** 1 → "첫 번째" */
        fun ordinal(n: Int) = if (n == 1) "첫 번째" else if (n in 2..10) "${NATIVE[n - 1]} 번째" else "${n}번째"

        // 짧은 거절만. "나중에 보고서…"처럼 긴 문장 속 단어는 걱정 내용이라 거절로 보지 않음
        private val DECLINE = setOf("없어", "없어요", "없음", "아니", "아니야", "아니요", "괜찮아", "괜찮아요", "됐어", "됐어요", "그만", "나중에")

        fun isDecline(text: String) = norm(text) in DECLINE

        private val NATIVE = listOf("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")

        /** 2 → "두 개" */
        fun count(n: Int) = if (n in 1..10) "${NATIVE[n - 1]} 개" else "${n}개"
    }
}
