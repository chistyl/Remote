package com.chi.tvremote.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Captures 8 kHz, 16-bit, mono PCM for Android TV Remote v2 voice sessions. */
class VoiceRecorder {
    suspend fun capture(stopRequested: AtomicBoolean, onChunk: suspend (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        val sampleRate = 8_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) throw IOException("手機不支援 8 kHz 麥克風錄音格式")

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, CHUNK_BYTES * 2)
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IOException("無法初始化手機麥克風")
        }

        val readBuffer = ByteArray(CHUNK_BYTES / 2)
        val pending = ByteArray(CHUNK_BYTES)
        var pendingSize = 0
        try {
            recorder.startRecording()
            while (!stopRequested.get() && currentCoroutineContext().isActive) {
                val count = recorder.read(readBuffer, 0, readBuffer.size)
                if (count < 0) throw IOException("麥克風讀取失敗：$count")
                if (count == 0) continue

                var sourceOffset = 0
                while (sourceOffset < count) {
                    val copyCount = minOf(CHUNK_BYTES - pendingSize, count - sourceOffset)
                    System.arraycopy(readBuffer, sourceOffset, pending, pendingSize, copyCount)
                    pendingSize += copyCount
                    sourceOffset += copyCount
                    if (pendingSize == CHUNK_BYTES) {
                        onChunk(pending.copyOf())
                        pendingSize = 0
                    }
                }
            }
            if (pendingSize > 0) onChunk(pending.copyOf(pendingSize))
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } catch (_: Exception) { }
            recorder.release()
        }
    }

    companion object {
        const val CHUNK_BYTES = 8_192
    }
}
