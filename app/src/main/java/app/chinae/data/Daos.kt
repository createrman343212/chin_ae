package app.chinae.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorryDao {
    @Insert suspend fun insertAll(worries: List<Worry>): List<Long>

    /** 돌려받기 순서: 오래된 것부터 (10장 "출근길 없이 이틀 경과") */
    @Query("SELECT * FROM Worry WHERE status IN ('PENDING','DEFERRED') ORDER BY createdAt")
    suspend fun pending(): List<Worry>

    @Query("SELECT COUNT(*) FROM Worry WHERE status IN ('PENDING','DEFERRED')")
    fun pendingCount(): Flow<Int>

    /** 정리되면(해결, 내려놓기) 원문을 바로 지움 (6.4 "정리 즉시") */
    @Query("""UPDATE Worry SET status = :status, returnedAt = :now,
        rawText = CASE WHEN :status IN ('RESOLVED','LET_GO') THEN NULL ELSE rawText END WHERE id = :id""")
    suspend fun setStatus(id: Long, status: WorryStatus, now: Long)

    @Query("UPDATE Worry SET category = :category, corrected = 1 WHERE id = :id")
    suspend fun correct(id: Long, category: app.chinae.worry.Category)

    @Query("SELECT * FROM Worry WHERE corrected = 1 ORDER BY createdAt")
    suspend fun corrected(): List<Worry>

    @Query("SELECT * FROM Worry WHERE createdAt >= :from ORDER BY createdAt")
    suspend fun since(from: Long): List<Worry>

    @Query("UPDATE Worry SET rawText = NULL WHERE rawText IS NOT NULL AND rawExpiresAt <= :now")
    suspend fun clearExpiredRaw(now: Long): Int

    @Query("DELETE FROM Worry WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM Worry") suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Insert suspend fun insert(note: NoteToSelf): Long
    @Query("SELECT * FROM NoteToSelf ORDER BY createdAt") suspend fun all(): List<NoteToSelf>
    @Query("DELETE FROM NoteToSelf WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM NoteToSelf") suspend fun deleteAll()
}
