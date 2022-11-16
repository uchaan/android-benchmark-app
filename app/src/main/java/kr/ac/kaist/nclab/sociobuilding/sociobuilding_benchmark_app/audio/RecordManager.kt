package kr.ac.kaist.nclab.sociobuilding.sociobuilding_benchmark_app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Singleton

@Singleton
class RecordManager(private val listener: RecordListener) {
    private val recordingInProgress: AtomicBoolean = AtomicBoolean(false)
    private val audioSource: Int = MediaRecorder.AudioSource.UNPROCESSED
    private var audioRecord: AudioRecord? = null

    companion object {
        // Audio record params
        const val SAMPLING_RATE_IN_HZ = 44100
        const val CHANNEL_CONFIG: Int = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT: Int = AudioFormat.ENCODING_PCM_16BIT

        private const val BUFFER_SIZE_FACTOR = 1
        val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR
        private const val ERROR_RECORDER_NULL = -11
    }

    init {
        audioRecord = AudioRecord(
            audioSource,
            SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )
    }

    fun startRecording(): Boolean {
//        App.onRecord = true
        if(audioRecord?.state == AudioRecord.STATE_UNINITIALIZED) {
            Timber.d("AudioRecord object is not initialized.")
            stopRecording()
            return false
        }

        if (audioRecord?.state == AudioRecord.RECORDSTATE_RECORDING) {
            Timber.d("Audio recording already in progress ... ")
            return false
        }

        if (recordingInProgress.get()) {
            Timber.d("Recording already in Progress")
            stopRecording()
            return false
        }

        try {
            audioRecord?.let {
                Timber.d("start AudioRecord")
                it.startRecording()
                recordingInProgress.set(true)
                Thread(getRecordingRunnable(), "Recording Thread").start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            return false
        }

        return audioRecord != null
    }

    fun stopRecording() {
        Timber.d("stop AudioRecord")
        recordingInProgress.set(false)
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
    }

    private fun getRecordingRunnable(): Runnable {
        return Runnable {

            val bos = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)

            try {
                while (recordingInProgress.get()) {
                    val result = audioRecord?.read(
                        buffer, 0, BUFFER_SIZE, AudioRecord.READ_BLOCKING
                    ) ?: ERROR_RECORDER_NULL
                    if (result < 0) {
                        Timber.d(getErrorMessage(result))
                        continue
                    }
                    bos.write(buffer, 0, result)
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            listener.didRecordAudio(bos.toByteArray())
            // for memory manage
//            bos.close()
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
            AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
            AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
            AudioRecord.ERROR -> "ERROR"
            ERROR_RECORDER_NULL -> "ERROR_RECORDER_NULL"
            else -> "Unknown ($errorCode)"
        }
    }

    private fun byteToShort(byteArray: ByteArray): ShortArray {
        val shortOut = ShortArray(byteArray.size / 2)
        val byteBuffer = ByteBuffer.wrap(byteArray)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()[shortOut]
        return shortOut
    }

    fun getSettingsText(): String {
        val audioRecord = AudioRecord(
            audioSource,
            SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )
        val audioSource = when (audioRecord.audioSource) {
            MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            else -> "UNKNOWN"
        }
        audioRecord.release()
        return audioSource
    }

    interface RecordListener {
        fun didRecordAudio(buffer: ByteArray)
    }
}
