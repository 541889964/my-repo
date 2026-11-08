package com.superrouter.app

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SoundManager {
    // 音效模式：0=关闭，1=清脆，2=机械，3=水滴，4=打字机
    var soundMode by mutableStateOf(1) 
    private var toneGen: ToneGenerator? = null

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 80)
        } catch (_: Throwable) {}
    }

    fun play() {
        if (soundMode == 0) return
        try {
            when (soundMode) {
                1 -> toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
                2 -> toneGen?.startTone(ToneGenerator.TONE_DTMF_5, 40)
                3 -> toneGen?.startTone(ToneGenerator.TONE_CDMA_PIP, 40)
                4 -> toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 30)
            }
        } catch (_: Throwable) {}
    }
}
