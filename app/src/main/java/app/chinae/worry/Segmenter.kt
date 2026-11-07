package app.chinae.worry

/** masterplan 5.1. STT 최종 결과를 걱정 문장 단위로 나눔 */
object Segmenter {
    // "또"는 부사("담당자가 또 무리하게")와 겹쳐서 종결 어미 뒤에 올 때만 나눔
    private val SPLIT = Regex("""[.!?。\n]+|\s*(?<![가-힣])그리고요?(?![가-힣])\s*|(?<=[다요어아야지네해돼])\s+또\s+""")
    private const val MIN_LEN = 5

    fun split(text: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in text.split(SPLIT)) {
            val s = raw.trim().trimEnd(',', ' ')
            if (s.isEmpty()) continue
            if (s.replace(" ", "").length < MIN_LEN && out.isNotEmpty()) out[out.lastIndex] = out.last() + " " + s
            else out += s
        }
        // 첫 조각이 짧으면 다음 문장 앞에 붙임
        if (out.size > 1 && out[0].replace(" ", "").length < MIN_LEN) {
            out[1] = out[0] + " " + out[1]
            out.removeAt(0)
        }
        return out
    }
}
