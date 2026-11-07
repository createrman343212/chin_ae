package app.chinae.ritual

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import app.chinae.R
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Pleos 차량 잠금음. 현대차그룹 저작물이라 앱에 넣지 않고 차량에 있는 파일을 그 자리에서 재생함.
 * 경로는 Pleos Connect Emulator 2.1.0에서 확인. 없으면 앱의 합성음(res/raw/lock_chime.wav)으로 대신함
 */
private val VEHICLE_LOCK = File("/product/media/audio/notifications/snd_vehicle_lock.ogg")

/** 잠금음을 한 번 재생하고 끝날 때까지 기다림 */
suspend fun playChime(context: Context) = suspendCancellableCoroutine { c ->
    val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    val player = vehicleLock(attrs) ?: MediaPlayer.create(context, R.raw.lock_chime, attrs, 0)
    if (player == null) { c.resume(Unit); return@suspendCancellableCoroutine }
    player.setOnCompletionListener { it.release(); if (c.isActive) c.resume(Unit) }
    c.invokeOnCancellation { runCatching { player.stop(); player.release() } }
    player.start()
}

private fun vehicleLock(attrs: AudioAttributes): MediaPlayer? {
    if (!VEHICLE_LOCK.canRead()) return null
    val p = MediaPlayer()
    return runCatching { p.setAudioAttributes(attrs); p.setDataSource(VEHICLE_LOCK.path); p.prepare(); p }
        .onFailure { Log.w("Chime", "vehicle lock sound", it); p.release() }
        .getOrNull()
}
