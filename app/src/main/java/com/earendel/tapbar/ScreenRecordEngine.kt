package com.earendel.tapbar

import android.annotation.SuppressLint
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import java.io.FileDescriptor
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

class ScreenRecordEngine(
    private val projection: MediaProjection,
    private val rawWidth: Int,
    private val rawHeight: Int,
    private val dpi: Int,
    private val output: FileDescriptor,
    audio: Boolean
) {
    private class Pending(val track: Int, val data: ByteArray, val info: MediaCodec.BufferInfo)

    private var audioEnabled = audio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private var videoCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var display: VirtualDisplay? = null
    private var audioCodec: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var muxer: MediaMuxer? = null
    private var worker: Thread? = null
    private val pending = ArrayList<Pending>()
    private val trackIds = IntArray(2) { -1 }
    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    private var videoFormatAtMs = 0L
    private var muxerStarted = false
    private var wroteSample = false
    private var startElapsedNs = 0L
    private var firstVideoPtsUs = -1L
    private var videoOffsetUs = 0L
    private var audioOffsetUs = -1L
    private var audioFramesRead = 0L
    private var lastAudioPtsUs = -1L
    private var lastAudioOutUs = -1L
    private var audioEosSent = false
    private var failed = false

    @Volatile
    private var stopRequested = false

    @Volatile
    private var abandon = false

    @Volatile
    private var finished = false

    @Volatile
    private var success = false

    fun start() {
        try {
            val vc = MediaCodec.createEncoderByType(VIDEO_MIME)
            videoCodec = vc
            val caps = vc.codecInfo.getCapabilitiesForType(VIDEO_MIME).videoCapabilities
            var scale = min(1f, MAX_LONG_SIDE / max(rawWidth, rawHeight).toFloat())
            var w = align(rawWidth * scale, caps.widthAlignment)
            var h = align(rawHeight * scale, caps.heightAlignment)
            var tries = 0
            while (!caps.isSizeSupported(w, h) && tries < 4) {
                scale *= 0.75f
                w = align(rawWidth * scale, caps.widthAlignment)
                h = align(rawHeight * scale, caps.heightAlignment)
                tries++
            }
            val format = MediaFormat.createVideoFormat(VIDEO_MIME, w, h)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, (w * h * 4).coerceIn(MIN_BITRATE, MAX_BITRATE))
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAME_US)
            vc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = vc.createInputSurface()
            inputSurface = surface
            vc.start()
            muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (audioEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    setupAudio()
                } catch (t: Throwable) {
                    Log.e("Tapbar", "Device audio capture unavailable", t)
                    audioEnabled = false
                    releaseAudio()
                }
            }
            display = projection.createVirtualDisplay(
                "TapbarScreenRecord",
                w,
                h,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )
            startElapsedNs = SystemClock.elapsedRealtimeNanos()
            audioRecord?.startRecording()
            val thread = Thread({ loop() }, "TapbarScreenRecord")
            worker = thread
            thread.start()
        } catch (t: Throwable) {
            releaseAll()
            throw t
        }
    }

    fun stop(): Boolean {
        try {
            display?.release()
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not release display", t)
        }
        display = null
        stopRequested = true
        val thread = worker
        if (thread != null) {
            try {
                thread.join(STOP_JOIN_MS)
                if (thread.isAlive) {
                    abandon = true
                    thread.join(1000)
                }
            } catch (_: InterruptedException) {
            }
        }
        if (!finished) releaseAll()
        return success
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private fun setupAudio() {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val pcmFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(pcmFormat)
            .setBufferSizeInBytes(max(minBuffer * 4, 32768))
            .build()
        audioRecord = record
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("Audio capture not initialized")
        }
        val codec = MediaCodec.createEncoderByType(AUDIO_MIME)
        audioCodec = codec
        val format = MediaFormat.createAudioFormat(AUDIO_MIME, SAMPLE_RATE, 2)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, PCM_CHUNK)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    private fun loop() {
        val videoInfo = MediaCodec.BufferInfo()
        val audioInfo = MediaCodec.BufferInfo()
        val pcm = ByteArray(PCM_CHUNK)
        var videoDone = false
        var audioDone = !audioEnabled
        var eosSignaled = false
        try {
            while (!(videoDone && audioDone) && !abandon) {
                var worked = false
                if (stopRequested && !eosSignaled) {
                    eosSignaled = true
                    videoCodec?.signalEndOfInputStream()
                }
                if (!videoDone) {
                    val result = drainVideo(videoInfo)
                    if (result == DRAIN_EOS) videoDone = true
                    if (result != DRAIN_NONE) worked = true
                }
                if (!audioDone) {
                    if (feedAudio(pcm, eosSignaled)) worked = true
                    val result = drainAudio(audioInfo)
                    if (result == DRAIN_EOS) audioDone = true
                    if (result != DRAIN_NONE) worked = true
                    if (!muxerStarted && videoFormat != null && audioFormat == null &&
                        SystemClock.elapsedRealtime() - videoFormatAtMs > AUDIO_WAIT_MS
                    ) {
                        audioEnabled = false
                        audioDone = true
                        releaseAudio()
                        tryStartMuxer()
                    }
                }
                if (!worked) Thread.sleep(IDLE_SLEEP_MS)
            }
        } catch (t: Throwable) {
            Log.e("Tapbar", "Recording loop failed", t)
            failed = true
        } finally {
            finalizeOutput()
        }
    }

    private fun drainVideo(info: MediaCodec.BufferInfo): Int {
        val codec = videoCodec ?: return DRAIN_NONE
        val index = codec.dequeueOutputBuffer(info, 0)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            videoFormat = codec.outputFormat
            videoFormatAtMs = SystemClock.elapsedRealtime()
            tryStartMuxer()
            return DRAIN_OK
        }
        if (index < 0) return DRAIN_NONE
        val buffer = codec.getOutputBuffer(index)
        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
        if (info.size > 0 && buffer != null) {
            if (firstVideoPtsUs < 0) {
                firstVideoPtsUs = info.presentationTimeUs
                videoOffsetUs = nowOffsetUs()
            }
            info.presentationTimeUs = info.presentationTimeUs - firstVideoPtsUs + videoOffsetUs
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            emit(TRACK_VIDEO, buffer, info)
        }
        codec.releaseOutputBuffer(index, false)
        return if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) DRAIN_EOS else DRAIN_OK
    }

    private fun feedAudio(pcm: ByteArray, eos: Boolean): Boolean {
        val codec = audioCodec ?: return false
        val record = audioRecord ?: return false
        if (audioEosSent) return false
        if (eos) {
            val index = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (index < 0) return false
            codec.queueInputBuffer(index, 0, 0, max(lastAudioPtsUs, 0L), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            audioEosSent = true
            return true
        }
        val read = record.read(pcm, 0, pcm.size, AudioRecord.READ_NON_BLOCKING)
        if (read <= 0) return false
        val index = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (index < 0) return true
        val buffer = codec.getInputBuffer(index) ?: return true
        buffer.clear()
        val size = min(read, buffer.remaining())
        buffer.put(pcm, 0, size)
        if (audioOffsetUs < 0) audioOffsetUs = max(0L, nowOffsetUs() - framesToUs((size / BYTES_PER_FRAME).toLong()))
        val pts = audioOffsetUs + framesToUs(audioFramesRead)
        audioFramesRead += size / BYTES_PER_FRAME
        lastAudioPtsUs = pts
        codec.queueInputBuffer(index, 0, size, pts, 0)
        return true
    }

    private fun drainAudio(info: MediaCodec.BufferInfo): Int {
        val codec = audioCodec ?: return DRAIN_NONE
        val index = codec.dequeueOutputBuffer(info, 0)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            audioFormat = codec.outputFormat
            tryStartMuxer()
            return DRAIN_OK
        }
        if (index < 0) return DRAIN_NONE
        val buffer = codec.getOutputBuffer(index)
        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
        if (info.size > 0 && buffer != null) {
            if (info.presentationTimeUs <= lastAudioOutUs) info.presentationTimeUs = lastAudioOutUs + 1
            lastAudioOutUs = info.presentationTimeUs
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            emit(TRACK_AUDIO, buffer, info)
        }
        codec.releaseOutputBuffer(index, false)
        return if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) DRAIN_EOS else DRAIN_OK
    }

    private fun emit(track: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (muxerStarted) {
            muxer?.writeSampleData(trackIds[track], buffer, info)
            wroteSample = true
            return
        }
        val bytes = ByteArray(info.size)
        buffer.get(bytes)
        val copy = MediaCodec.BufferInfo()
        copy.set(0, bytes.size, info.presentationTimeUs, info.flags)
        pending.add(Pending(track, bytes, copy))
    }

    private fun tryStartMuxer() {
        if (muxerStarted) return
        val m = muxer ?: return
        val video = videoFormat ?: return
        val audio = audioFormat
        if (audioEnabled && audio == null) return
        trackIds[TRACK_VIDEO] = m.addTrack(video)
        if (audioEnabled && audio != null) trackIds[TRACK_AUDIO] = m.addTrack(audio)
        m.start()
        muxerStarted = true
        for (p in pending) {
            if (trackIds[p.track] < 0) continue
            m.writeSampleData(trackIds[p.track], ByteBuffer.wrap(p.data), p.info)
            wroteSample = true
        }
        pending.clear()
    }

    private fun finalizeOutput() {
        var ok = false
        try {
            if (muxerStarted) {
                muxer?.stop()
                ok = wroteSample && !failed
            }
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not finalize muxer", t)
        }
        success = ok
        releaseAll()
        finished = true
    }

    private fun nowOffsetUs(): Long = (SystemClock.elapsedRealtimeNanos() - startElapsedNs) / 1000L

    private fun framesToUs(frames: Long): Long = frames * 1_000_000L / SAMPLE_RATE

    private fun align(value: Float, alignment: Int): Int {
        val a = max(alignment, 2)
        return max(a, (value.toInt() / a) * a)
    }

    private fun releaseAudio() {
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null
        try {
            audioCodec?.stop()
        } catch (_: Throwable) {
        }
        try {
            audioCodec?.release()
        } catch (_: Throwable) {
        }
        audioCodec = null
    }

    private fun releaseAll() {
        try {
            display?.release()
        } catch (_: Throwable) {
        }
        display = null
        try {
            videoCodec?.stop()
        } catch (_: Throwable) {
        }
        try {
            videoCodec?.release()
        } catch (_: Throwable) {
        }
        videoCodec = null
        try {
            inputSurface?.release()
        } catch (_: Throwable) {
        }
        inputSurface = null
        releaseAudio()
        try {
            muxer?.release()
        } catch (_: Throwable) {
        }
        muxer = null
    }

    private companion object {
        const val VIDEO_MIME = "video/avc"
        const val AUDIO_MIME = "audio/mp4a-latm"
        const val MAX_LONG_SIDE = 1920f
        const val FRAME_RATE = 30
        const val REPEAT_FRAME_US = 500_000L
        const val MIN_BITRATE = 4_000_000
        const val MAX_BITRATE = 12_000_000
        const val SAMPLE_RATE = 44100
        const val AUDIO_BITRATE = 128_000
        const val PCM_CHUNK = 16384
        const val BYTES_PER_FRAME = 4
        const val INPUT_TIMEOUT_US = 5000L
        const val IDLE_SLEEP_MS = 5L
        const val AUDIO_WAIT_MS = 3000L
        const val STOP_JOIN_MS = 4000L
        const val TRACK_VIDEO = 0
        const val TRACK_AUDIO = 1
        const val DRAIN_NONE = 0
        const val DRAIN_OK = 1
        const val DRAIN_EOS = 2
    }
}
