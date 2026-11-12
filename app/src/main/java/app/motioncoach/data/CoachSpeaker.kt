package app.motioncoach.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import app.motioncoach.R
import app.motioncoach.core.Maneuver

class CoachSpeaker(private val context: Context, private val onState: (String) -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    fun maneuver(kind: Maneuver, withTip: Boolean) {
        val clip = when (kind) {
            Maneuver.RIGHT -> if (withTip) R.raw.guide_right_tip else R.raw.guide_right
            Maneuver.LEFT -> if (withTip) R.raw.guide_left_tip else R.raw.guide_left
            Maneuver.BUMP -> if (withTip) R.raw.guide_bump_tip else R.raw.guide_bump
            Maneuver.DOWNHILL -> if (withTip) R.raw.guide_downhill_tip else R.raw.guide_downhill
        }
        play(clip)
    }

    fun rest() = play(R.raw.guide_rest)

    private fun play(clip: Int) {
        stop()
        val next = MediaPlayer()
        player = next
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (player === next && (change == AudioManager.AUDIOFOCUS_LOSS ||
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)) stop()
            }.build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stop()
            onState("다른 오디오가 사용 중이에요. 잠시 후 다시 들어보세요.")
            return
        }
        focus = request
        try {
            next.setAudioAttributes(attributes)
            context.resources.openRawResourceFd(clip).use { fd ->
                next.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            next.setOnCompletionListener { if (player === next) stop() }
            next.setOnErrorListener { _, _, _ ->
                if (player === next) playbackFailed()
                true
            }
            next.prepare()
            next.start()
            onState("내장 한국어 음성 · 소리 크기는 미디어 또는 차량 안내 볼륨에서 조절하세요.")
        } catch (_: java.io.IOException) {
            playbackFailed()
        } catch (_: IllegalStateException) {
            playbackFailed()
        }
    }

    private fun playbackFailed() {
        stop()
        onState("안내 음성을 재생하지 못했어요. 미리 듣기로 다시 확인해 주세요.")
    }

    fun stop() {
        player?.release()
        player = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }
}
