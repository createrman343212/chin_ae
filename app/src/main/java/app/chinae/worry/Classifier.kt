package app.chinae.worry

enum class Category { ACTIONABLE, UNCHANGEABLE, UNKNOWN }

/** masterplan 5.2 규칙 기반 분류 */
object Classifier {
    private val DEADLINE = listOf("내일", "오늘", "오전", "오후", "까지", "마감", "이번 주", "다음 주")
    private val ACTION = listOf("해야", "보내", "답장", "정리", "제출", "끝내", "준비", "확인", "맞춰", "맞추", "고쳐", "올려")
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
        val u = if (attitude && person) 2 else if (attitude) 1 else 0
        return when {
            a > u -> Category.ACTIONABLE
            u > a -> Category.UNCHANGEABLE
            else -> Category.UNKNOWN
        }
    }
}
