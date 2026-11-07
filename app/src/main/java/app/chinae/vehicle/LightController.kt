package app.chinae.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import android.content.Context
import android.util.Log
import app.chinae.ritual.Ambient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Pleos 앰비언트 조명. 지원하는 모든 영역의 색을 바꾸고 이전 색을 기억함 */
class LightController(context: Context) : Ambient {
    private val vehicle = Vehicle(context)

    fun start() = vehicle.initialize()
    fun release() = runCatching { vehicle.release() }

    // shortcut: 조명 미지원이면 null만 돌려줌. 화면 색 전환 폴백(10장)은 UI 작업 때 추가
    override suspend fun paint(argb: Int): (suspend () -> Unit)? {
        val light = vehicle.getLight()
        val areas = ipc<List<SeatArea>> { ok, fail -> light.checkAmbientLightBrightnessColorCapabilities({ _, a -> ok(a) }, fail) }
        if (areas.isNullOrEmpty()) return null
        val before = areas.associateWith { a -> ipc<Int?> { ok, fail -> light.getAmbientLightBrightnessColor(a, ok, fail) } }
        areas.forEach { set(it, argb) }
        // 읽은 색은 알파 없이 0x00RRGGBB로 옴(에뮬레이터 실측). 그대로 쓰면 반영이 안 돼 불투명으로 되돌림
        return { before.forEach { (a, c) -> if (c != null) set(a, c or OPAQUE) } }
    }

    private suspend fun set(area: SeatArea, argb: Int) =
        ipc<Unit> { ok, fail -> vehicle.getLight().setAmbientLightBrightnessColor(area, argb, { _, _ -> ok(Unit) }, fail) }

    /** Vehicle SDK는 IPC 콜백이라 코루틴으로 감싸고 타임아웃을 둠(masterplan 7.1). 실패나 시간 초과면 null */
    private suspend fun <T> ipc(call: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit): T? =
        withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine { c ->
                call({ if (c.isActive) c.resume(it) }, { e -> Log.w("LightController", "light", e); if (c.isActive) c.resume(null) })
            }
        }

    private companion object {
        const val OPAQUE = 0xFF000000.toInt()
    }
}
