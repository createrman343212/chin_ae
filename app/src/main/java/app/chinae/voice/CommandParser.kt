package app.chinae.voice

enum class Command { RESOLVED, TODAY, LET_GO, MARK_ACTIONABLE, MARK_UNCHANGEABLE, STOP }

/** masterplan 4.4 음성 명령 사전. 인식 못 하면 null(다음 항목으로 넘기고 이월) */
object CommandParser {
    // 순서가 우선순위: 분류 바로잡기를 먼저 봐야 "할 수 있는 일"이 다른 명령에 먹히지 않음
    private val RULES = listOf(
        Command.MARK_UNCHANGEABLE to listOf("바꿀수없"),
        Command.MARK_ACTIONABLE to listOf("할수있"),
        Command.RESOLVED to listOf("해결", "끝났", "끝냈"),
        Command.LET_GO to listOf("내려놓", "그냥둘", "그냥둬"),
        Command.TODAY to listOf("오늘처리", "오늘할", "이따할", "이따가할"),
        Command.STOP to listOf("그만", "나중에"),
    )

    fun parse(utterance: String): Command? {
        val s = utterance.replace(Regex("""\s+"""), "")
        if (s.isEmpty()) return null
        return RULES.firstOrNull { (_, keys) -> keys.any { it in s } }?.first
    }
}
