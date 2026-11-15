package app.chinae.worry

enum class Category { ACTIONABLE, UNCHANGEABLE, UNKNOWN }

/** masterplan 5.2 규칙 기반 분류 */
object Classifier {
    private val DEADLINE = listOf("내일", "오늘", "오전", "오후", "까지", "마감", "이번 주", "다음 주")
    private val ACTION = listOf("해야", "보내", "답장", "정리", "제출", "끝내", "준비", "확인", "맞춰", "맞추", "고쳐", "올려")
    // 날씨·교통·시장처럼 내가 바꿀 수 없는 바깥 사정. "준비가"의 '비가'처럼 낱말 속 글자는 빼려고 앞 글자를 봄
    private val EXTERNAL = Regex("""날씨|태풍|미세먼지|폭염|한파|폭설|장마|(?<![가-힣])비(가|\s*(온|와|오|내))|(?<![가-힣])눈(이)?\s*(온|와|오|내)|막힐|막히|막혀|교통|정체|주가|환율""")
    private val ATTITUDE = listOf("무리", "왜", "서운", "짜증", "화를", "화내", "무시", "맨날", "항상", "억울", "속상")

    /** corrections: 사용자가 바로잡은 주제어별 분류. 겹치면 우선 적용 */
    fun classify(
        text: String,
        topicKeys: List<String> = emptyList(),
        corrections: Map<String, Category> = emptyMap(),
    ): Category {
        topicKeys.firstNotNullOfOrNull { corrections[it] }?.let { return it }

        val a = listOf(DEADLINE, ACTION, KeyExtractor.WORK_NOUNS.toList()).count { g -> g.any { it in text } }
        val attitude = ATTITUDE.any { it in text }
        val person = KeyExtractor.personKey(text) != null
        // 바깥 사정은 '내일' 같은 기한 말이 있어도 바꿀 수 없는 일로 봄
        val u = (if (attitude && person) 2 else if (attitude) 1 else 0) + if (EXTERNAL.containsMatchIn(text)) 2 else 0
        return when {
            a > u -> Category.ACTIONABLE
            u > a -> Category.UNCHANGEABLE
            else -> Category.UNKNOWN
        }
    }
}
