package app.chinae.voice

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
) {
    /** 4.2 맡기기: 질문 → 듣기 → 저장 → 건수만 말함(내용은 되읽지 않음) */
    suspend fun runEntrust() {
        val l = lines()
        speaker.say(l["ask_entrust"])
        val text = listener.listen()?.trim()
        if (text.isNullOrEmpty() || isDecline(text)) return // 빈 결과나 거절이면 아무 말 없이 끝
        val n = entrust(text)
        if (n > 0) speaker.say(l["ack_entrust", mapOf("n" to count(n))])
    }

    companion object {
        // 짧은 거절만. "나중에 보고서…"처럼 긴 문장 속 단어는 걱정 내용이라 거절로 보지 않음
        private val DECLINE = setOf("없어", "없어요", "없음", "아니", "아니야", "아니요", "괜찮아", "괜찮아요", "됐어", "됐어요", "그만", "나중에")

        fun isDecline(text: String) = text.replace(Regex("""[\s.,!?]"""), "") in DECLINE

        private val NATIVE = listOf("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")

        /** 2 → "두 개" */
        fun count(n: Int) = if (n in 1..10) "${NATIVE[n - 1]} 개" else "${n}개"
    }
}
