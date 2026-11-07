package app.chinae.worry

/** masterplan 5.3 주제어와 인물 키 */
object KeyExtractor {
    val WORK_NOUNS = setOf(
        "보고서", "메일", "자료", "회의", "발표", "견적", "계약", "일정", "마감", "수치",
        "기획서", "결재", "출장", "면접", "평가", "프로젝트", "답장", "정산", "예산",
    )
    val PERSON_WORDS = listOf("팀장님", "부장님", "과장님", "차장님", "실장님", "사장님", "대표님", "상무님",
        "거래처", "고객", "동료", "상사", "후배", "선배", "담당자")

    private val PARTICLE = Regex("(으로|에게|에서|까지|부터|한테|이랑|은|는|이|가|을|를|에|의|와|과|도|만|랑|로)$")
    private val NAME_NIM = Regex("""([가-힣]{2,4})님""")

    fun stem(word: String) = word.trim(',', '.', '?', '!').replace(PARTICLE, "")

    /** knownTopics: 지난 기록에서 2회 이상 나온 단어(DB에서 계산해 넘김) */
    fun topicKeys(text: String, knownTopics: Set<String> = emptySet()): List<String> =
        text.split(Regex("""\s+""")).map(::stem)
            .filter { it.length >= 2 && (it in WORK_NOUNS || it in knownTopics) }
            .distinct()

    fun personKey(text: String): String? =
        PERSON_WORDS.firstOrNull { it in text } ?: NAME_NIM.find(text)?.value
}
