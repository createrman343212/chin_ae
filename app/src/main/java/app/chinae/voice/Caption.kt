package app.chinae.voice

import kotlinx.coroutines.flow.MutableStateFlow

/** 지금 말하는 문장. 서비스와 화면이 같은 프로세스라 전역 흐름 하나로 넘김. 대화가 끝나면 null */
object Caption {
    val line = MutableStateFlow<String?>(null)
}
