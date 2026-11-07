package app.chinae.data

import android.content.Context
import androidx.core.content.edit
import app.chinae.trip.LatLng
import app.chinae.trip.Places
import java.time.DayOfWeek

enum class Tone { BANMAL, HAEYO }

/**
 * 말투, 호칭, 근무일, 직접 지정한 집과 회사 (masterplan 6.1 Settings, 3.3).
 * DataStore 대신 SharedPreferences: 값 몇 개라 플랫폼 기본으로 충분
 */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var tone: Tone
        get() = p.getString("tone", null)?.let(Tone::valueOf) ?: Tone.BANMAL
        set(v) = p.edit { putString("tone", v.name) }

    /** 친애가 사용자를 부르는 이름. null이면 호칭 없이 말함 */
    var alias: String?
        get() = p.getString("alias", null)
        set(v) = p.edit { putString("alias", v) }

    var workdays: Set<DayOfWeek>
        get() = p.getStringSet("workdays", null)?.map(DayOfWeek::valueOf)?.toSet()
            ?: DayOfWeek.entries.filter { it.value <= 5 }.toSet()
        set(v) = p.edit { putStringSet("workdays", v.map { it.name }.toSet()) }

    /** 직접 지정한 장소. 내비 즐겨찾기보다 우선 */
    var manualPlaces: Places
        get() = Places(latLng(p.getString("home", null)), latLng(p.getString("work", null)))
        set(v) = p.edit {
            putString("home", v.home?.let { "${it.lat},${it.lng}" })
            putString("work", v.work?.let { "${it.lat},${it.lng}" })
        }

    var onboardingDone: Boolean
        get() = p.getBoolean("onboardingDone", false)
        set(v) = p.edit { putBoolean("onboardingDone", v) }

    fun clear() = p.edit { clear() }

    private fun latLng(s: String?) = s?.split(",")?.let { LatLng(it[0].toDouble(), it[1].toDouble()) }
}
