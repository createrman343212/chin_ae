package app.chinae.data

import app.chinae.worry.Classifier
import app.chinae.worry.KeyExtractor
import app.chinae.worry.Segmenter
import java.util.concurrent.TimeUnit

/** 맡기기 발화 → 걱정 n건 저장, 보존 정리 (masterplan 5장, 6.4) */
class WorryRepository(private val db: ChinaeDb) {
    val pendingCount = db.worries().pendingCount()

    // shortcut: 주제어는 업무 사전만 씀. "2회 이상 나온 단어"(5.3)는 품사 구분 없이 세면 동사가 섞여서, 형태소 처리 전까지 보류
    suspend fun entrust(utterance: String, now: Long): List<Worry> {
        // 같은 주제어를 여러 번 바로잡았으면 최근 것이 이김(createdAt 순으로 덮어씀)
        val corrections = db.worries().corrected().flatMap { w -> w.topicKeys.map { it to w.category } }.toMap()
        val worries = Segmenter.split(utterance).map { s ->
            val keys = KeyExtractor.topicKeys(s)
            Worry(
                createdAt = now, rawText = s, rawExpiresAt = now + RAW_TTL_MS,
                category = Classifier.classify(s, keys, corrections),
                topicKeys = keys, personKey = KeyExtractor.personKey(s),
            )
        }
        val ids = db.worries().insertAll(worries)
        return worries.zip(ids) { w, id -> w.copy(id = id) }
    }

    /** 시동 시점과 하루 1회. 원문 7일, 나머지 90일 */
    suspend fun purge(now: Long) {
        db.worries().clearExpiredRaw(now)
        db.worries().deleteOlderThan(now - KEEP_MS)
    }

    /** 설정의 "모든 기록 지우기" */
    suspend fun wipeAll() {
        db.worries().deleteAll()
        db.notes().deleteAll()
    }

    companion object {
        val RAW_TTL_MS = TimeUnit.DAYS.toMillis(7)
        val KEEP_MS = TimeUnit.DAYS.toMillis(90)
    }
}
