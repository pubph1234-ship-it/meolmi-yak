package app.motioncoach.core

import kotlin.math.abs
import kotlin.math.max

enum class DemoStyle(val label: String) { VARIABLE("변화가 큰 운전"), SMOOTH("미리 준비한 운전") }

enum class InputMode { DEMO, PLEOS }
enum class EventKind { ACCELERATION, BRAKING, STEERING, CUE, FEEDBACK }
enum class Maneuver(val title: String, val spoken: String) {
    LEFT("왼쪽 회전", "곧 왼쪽으로 회전합니다"),
    RIGHT("오른쪽 회전", "곧 오른쪽으로 회전합니다"),
    BUMP("방지턱", "곧 방지턱이 있습니다"),
    DOWNHILL("내리막 주의 구간", "곧 내리막 주의 구간입니다")
}
enum class Feeling(val label: String, val severity: Int) { OK("괜찮아요", 0), UNEASY("조금 불편해요", 1), REST("쉬고 싶어요", 2) }
data class MotionSample(val atMs: Long, val speedMps: Double?, val steeringDeg: Double?, val speedAtMs: Long = atMs, val steeringAtMs: Long = atMs)
data class RouteHint(val id: String, val kind: Maneuver, val distanceM: Double, val atMs: Long)
data class CoachEvent(val atMs: Long, val kind: EventKind, val title: String, val detail: String, val severity: Int = 0)
data class TripReport(
    val id: String, val createdAt: Long, val mode: InputMode, val durationMs: Long,
    val samples: List<MotionSample>, val events: List<CoachEvent>, val goal: String,
    val interrupted: Boolean = false, val scenario: String? = null
) {
    val feedbackCount get() = events.count { it.kind == EventKind.FEEDBACK }
    val changesCount get() = events.count { it.kind in setOf(EventKind.ACCELERATION, EventKind.BRAKING, EventKind.STEERING) }
}

/** Descriptive thresholds for this prototype, NOT validated nausea probabilities or a safety score. */
class CoachEngine {
    private var previous: MotionSample? = null
    private var previousSpeed: Pair<Long, Double>? = null
    private var previousSteering: Pair<Long, Double>? = null
    private var smoothedAccel: Double? = null
    private val lastChange = mutableMapOf<EventKind, Long>()
    private val cued = mutableSetOf<String>()
    private var lastCueMs = Long.MIN_VALUE
    val samples = mutableListOf<MotionSample>()
    val events = mutableListOf<CoachEvent>()
    var acceleration: Double? = null
        private set

    fun accept(sample: MotionSample): List<CoachEvent> {
        if (sample.atMs < 0 || (previous != null && sample.atMs <= previous!!.atMs)) return emptyList()
        val clean = sample.copy(
            speedMps = sample.speedMps?.takeIf { it.isFinite() && it >= 0 && it <= 100 },
            steeringDeg = sample.steeringDeg?.takeIf { it.isFinite() && abs(it) <= 1080 }
        )
        val added = mutableListOf<CoachEvent>()
        val oldSpeed = previousSpeed
        val dt = if (oldSpeed == null) 0.0 else (clean.speedAtMs - oldSpeed.first) / 1000.0
        if (clean.speedMps == null) {
            previousSpeed = null; acceleration = null; smoothedAccel = null
        } else if (oldSpeed == null || clean.speedAtMs > oldSpeed.first) {
          acceleration = null
          if (oldSpeed != null && dt in 0.15..2.0) {
            val raw = (clean.speedMps - oldSpeed.second) / dt
            if (abs(raw) <= 12) {
                smoothedAccel = smoothedAccel?.let { it * 0.55 + raw * 0.45 } ?: raw
                acceleration = smoothedAccel
                if (smoothedAccel!! > 2.2) emit(EventKind.ACCELERATION, clean.atMs, "가속 변화", "속도 변화에서 추정한 가속도가 커진 구간", added)
                if (smoothedAccel!! < -2.2) emit(EventKind.BRAKING, clean.atMs, "감속 변화", "주변 교통 상황에 따라 필요한 제동일 수 있어요", added)
            } else smoothedAccel = null
          } else smoothedAccel = null
          previousSpeed = clean.speedAtMs to clean.speedMps
        }
        val oldSteering = previousSteering
        val steeringDt = if (oldSteering == null) 0.0 else (clean.steeringAtMs - oldSteering.first) / 1000.0
        if (oldSteering != null && steeringDt in 0.15..2.0 && clean.speedMps != null && clean.speedMps > 3 && clean.steeringDeg != null) {
            if (abs(clean.steeringDeg - oldSteering.second) / steeringDt > 55) emit(EventKind.STEERING, clean.atMs, "조향 변화", "핸들 각도가 빠르게 바뀐 구간 · 횡가속도 측정값이 아니에요", added)
        }
        if (clean.steeringDeg == null) previousSteering = null
        else if (oldSteering == null || clean.steeringAtMs > oldSteering.first) previousSteering = clean.steeringAtMs to clean.steeringDeg
        previous = clean
        samples.add(clean)
        if (samples.size > 7200) samples.removeAt(0)
        return added
    }

    private fun emit(kind: EventKind, at: Long, title: String, detail: String, out: MutableList<CoachEvent>) {
        val previousAt = lastChange[kind]
        if (previousAt != null && at - previousAt < 5000) return
        lastChange[kind] = at
        CoachEvent(at, kind, title, detail).also { addEvent(it); out.add(it) }
    }

    fun cue(hint: RouteHint?, atMs: Long, gapMs: Long = 15000): CoachEvent? {
        if (hint == null || !hint.distanceM.isFinite() || hint.distanceM < 0) return null
        if (atMs - hint.atMs !in 0..2500) return null
        val sample = previous ?: return null
        if (atMs - sample.atMs !in 0..1500 || atMs - sample.speedAtMs !in 0..2500) return null
        val speed = sample.speedMps ?: return null
        if (speed < 2.0 || hint.id in cued) return null
        val eta = hint.distanceM / speed
        if (eta !in 3.0..12.0) return null
        if (lastCueMs != Long.MIN_VALUE && atMs - lastCueMs < gapMs) return null
        cued.add(hint.id)
        lastCueMs = atMs
        return CoachEvent(atMs, EventKind.CUE, hint.kind.spoken, "잠시 화면을 내려놓고 정면 먼 곳을 바라보세요").also(::addEvent)
    }

    fun feedback(feeling: Feeling, atMs: Long, simulated: Boolean = false): CoachEvent? {
        val last = events.lastOrNull { it.kind == EventKind.FEEDBACK }
        if (last != null && atMs - last.atMs < 3000) return null
        return CoachEvent(atMs, EventKind.FEEDBACK, feeling.label,
            if (feeling == Feeling.REST) "동승자가 휴식을 요청했어요. 안전한 장소에서 쉬어가세요." else if (simulated) "비교 시연이 자동 생성한 가상 체감 · 실제 동승자 입력 아님" else "동승자가 직접 남긴 체감 기록", feeling.severity).also(::addEvent)
    }

    fun goal(): String = FeedbackReview.goal(events) { generalGoal() }

    private fun generalGoal(): String {
        val braking = events.count { it.kind == EventKind.BRAKING }
        val acceleration = events.count { it.kind == EventKind.ACCELERATION }
        val steering = events.count { it.kind == EventKind.STEERING }
        return when {
            braking >= max(acceleration, steering) && braking > 0 -> "교통 상황이 허용할 때, 앞 상황을 미리 살피고 감속을 점진적으로 해보세요."
            acceleration > 0 && acceleration >= steering -> "출발하거나 다시 속도를 올릴 때, 가속 페달을 점진적으로 조작해보세요."
            steering > 0 -> "방향 전환을 미리 준비하고, 교통 상황에 맞춰 핸들을 점진적으로 조작해보세요."
            samples.count { it.speedMps != null } < 3 -> "주행 데이터가 부족해요. 다음 이동에서 연결 상태를 확인하고 기록해보세요."
            else -> "이번에는 설정한 기준을 넘는 주행 변화가 적었어요. 동승자의 체감도 함께 확인해보세요."
        }
    }

    fun clearRoute() { cued.clear() }
    private fun addEvent(event: CoachEvent) { events.add(event); if (events.size > 600) events.removeAt(0) }
}

/** A deterministic, fictional 60-second journey, never used by the live adapter. */
object DemoJourney {
    const val DURATION_MS = 60000L
    private val speed = listOf(0 to 0.0, 5 to 25.0, 10 to 40.0, 14 to 40.0, 17 to 13.0,
        22 to 25.0, 28 to 35.0, 34 to 35.0, 37 to 8.0, 42 to 25.0, 50 to 35.0, 55 to 18.0, 60 to 0.0)
    fun sample(ms: Long, style: DemoStyle = DemoStyle.VARIABLE): MotionSample {
        val s = (ms.coerceIn(0, DURATION_MS) / 1000.0)
        val speed = if (style == DemoStyle.SMOOTH) listOf(0 to 0.0, 10 to 30.0, 16 to 22.0, 22 to 22.0, 28 to 28.0, 35 to 16.0, 42 to 22.0, 50 to 26.0, 60 to 0.0) else speed
        val right = speed.indexOfFirst { it.first >= s }.coerceAtLeast(1)
        val a = speed[right - 1]; val b = speed[right]
        val kph = a.second + (b.second - a.second) * ((s - a.first) / (b.first - a.first))
        val angle = when {
            s in 18.0..21.0 -> 90 * kotlin.math.sin((s - 18) / 3 * Math.PI)
            s in 44.0..47.0 -> -110 * kotlin.math.sin((s - 44) / 3 * Math.PI)
            else -> 0.0
        }
        return MotionSample(ms, kph / 3.6, if (style == DemoStyle.SMOOTH) angle * 0.35 else angle)
    }
    fun hint(ms: Long): RouteHint? = when (ms) {
        in 5000..19000 -> RouteHint("demo-turn-1", Maneuver.RIGHT, (20000 - ms) / 1000.0 * 9, ms)
        in 23000..34000 -> RouteHint("demo-bump-1", Maneuver.BUMP, (35000 - ms) / 1000.0 * 8, ms)
        in 37000..44000 -> RouteHint("demo-turn-2", Maneuver.LEFT, (45000 - ms) / 1000.0 * 6.5, ms)
        else -> null
    }
}
