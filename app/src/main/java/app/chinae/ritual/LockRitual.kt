package app.chinae.ritual

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 앰비언트 조명. 색을 바꾸고, 원래 색으로 되돌리는 함수를 돌려줌. 조명을 못 쓰면 null */
fun interface Ambient { suspend fun paint(argb: Int): (suspend () -> Unit)? }

/** masterplan 3.1, S3: 집 반경에서 기어 P → 조명 전환, 잠금음, 10초 정적 */
class LockRitual(
    private val ambient: Ambient,
    private val chime: suspend () -> Unit,
    private val silenceMs: Long = 10_000,
) {
    // shortcut: "내일 아침의 나에게" 한 마디 질문은 고도화(11.29~) 때 정적 뒤에 붙임
    suspend fun run() {
        val restore = ambient.paint(WARM)
        try {
            chime()
            delay(silenceMs)
        } finally {
            // 기어가 P를 벗어나거나 동승자가 타서 끊겨도 조명은 원래대로
            withContext(NonCancellable) { restore?.invoke() }
        }
    }

    companion object {
        /** 서늘한 흰빛에서 바꿀 따뜻한 빛(10장) */
        const val WARM = 0xFFFFB46B.toInt()
    }
}
