package app.chinae.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import app.chinae.worry.Category

enum class WorryStatus { PENDING, RESOLVED, TODAY, LET_GO, DEFERRED }

/** masterplan 6.1. 시간은 모두 epoch ms */
@Entity
data class Worry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    /** 원문. 정리되거나 rawExpiresAt이 지나면 null (6.4) */
    val rawText: String?,
    val rawExpiresAt: Long,
    val category: Category,
    /** 사용자가 바로잡은 분류면 true. 같은 주제어의 다음 분류에 우선 적용 */
    val corrected: Boolean = false,
    val topicKeys: List<String>,
    val personKey: String?,
    val status: WorryStatus = WorryStatus.PENDING,
    val returnedAt: Long? = null,
)

/** 내일 아침의 나에게. 전달하면 행 삭제 */
@Entity
data class NoteToSelf(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val text: String,
)

class Converters {
    @TypeConverter fun fromKeys(v: List<String>) = v.joinToString("\u001F")
    @TypeConverter fun toKeys(v: String) = if (v.isEmpty()) emptyList() else v.split("\u001F")
    @TypeConverter fun fromCategory(v: Category) = v.name
    @TypeConverter fun toCategory(v: String) = Category.valueOf(v)
    @TypeConverter fun fromStatus(v: WorryStatus) = v.name
    @TypeConverter fun toStatus(v: String) = WorryStatus.valueOf(v)
}
