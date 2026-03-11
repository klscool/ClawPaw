package com.example.clawpaw.hardware

import android.content.Context
import android.media.AudioManager
import org.json.JSONObject

/**
 * 音量：读取与设置媒体/铃声音量。
 */
object VolumeHelper {

    private fun getAudioManager(context: Context): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun getVolumeInfo(context: Context): JSONObject {
        val am = getAudioManager(context) ?: return JSONObject().put("error", "无 AudioManager")
        return JSONObject().apply {
            put("mediaVolume", am.getStreamVolume(AudioManager.STREAM_MUSIC))
            put("mediaMaxVolume", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            put("ringVolume", am.getStreamVolume(AudioManager.STREAM_RING))
            put("ringMaxVolume", am.getStreamMaxVolume(AudioManager.STREAM_RING))
        }
    }

    fun setMediaVolume(context: Context, volume: Int): Boolean {
        val am = getAudioManager(context) ?: return false
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volume.coerceIn(0, max), 0)
        return true
    }

    fun setRingVolume(context: Context, volume: Int): Boolean {
        val am = getAudioManager(context) ?: return false
        val max = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        am.setStreamVolume(AudioManager.STREAM_RING, volume.coerceIn(0, max), 0)
        return true
    }
}
