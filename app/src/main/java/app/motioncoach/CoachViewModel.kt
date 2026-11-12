package app.motioncoach

import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.motioncoach.core.*
import app.motioncoach.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class Screen { HOME, JOURNEY, REPORT, HISTORY, CONNECTION }
data class CoachUiState(
    val demoStyle: DemoStyle = DemoStyle.VARIABLE, val comparison: Boolean = false,
    val screen: Screen = Screen.HOME, val mode: InputMode = InputMode.DEMO,
    val active: Boolean = false, val paused: Boolean = false, val elapsedMs: Long = 0, val automotive: Boolean = false,
    val sample: MotionSample? = null, val hint: RouteHint? = null, val acceleration: Double? = null,
    val cue: String = "동승자와 함께 출발을 준비해요", val cueDetail: String = "필요한 순간에 짧게 안내할게요",
    val events: List<CoachEvent> = emptyList(), val samples: List<MotionSample> = emptyList(),
    val vehicle: VehicleSnapshot = VehicleSnapshot(), val report: TripReport? = null,
    val history: List<TripReport> = emptyList(), val voice: Boolean = true, val voiceStatus: String = "앱에 저장된 한국어 음성으로 안내해요.",
    val sparse: Boolean = false, val pairingUrls: List<String> = emptyList(), val pairingPath: String? = null,
    val message: String? = null
) {
    val controlsLocked get() = (automotive || mode == InputMode.PLEOS) && (!vehicle.parked || vehicle.restricted)
}

class CoachViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TripStore(app)
    private val preferences = app.getSharedPreferences("settings", 0)
    private val mutable = MutableStateFlow(CoachUiState(automotive = app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE), history = store.load(), voice = preferences.getBoolean("voice", true), sparse = preferences.getBoolean("sparse", false)))
    val state = mutable.asStateFlow()
    private var engine = CoachEngine()
    private var tick: Job? = null
    private var source: PleosSource? = null
    private var passenger: PassengerServer? = null
    private var startedAt = 0L
    private var createdAt = 0L
    private var lastCueAt: Long? = null
    private val speaker = CoachSpeaker(app) { status -> mutable.update { it.copy(voiceStatus = status) } }
    fun previewVoice() {
        val s = mutable.value
        if (s.voice && !s.active && !s.controlsLocked) speaker.maneuver(Maneuver.RIGHT, withTip = true)
    }

    fun selectDemoStyle(style: DemoStyle) { if (!mutable.value.active && !mutable.value.controlsLocked) mutable.update { it.copy(demoStyle = style) } }
    fun toggleComparison() { if (!mutable.value.active && !mutable.value.controlsLocked) mutable.update { it.copy(comparison = !it.comparison) } }
    fun selectMode(mode: InputMode) {
        if (mutable.value.active || (mutable.value.automotive && mutable.value.controlsLocked)) return
        source?.close(); source = null
        mutable.update { it.copy(mode = mode, vehicle = VehicleSnapshot(), message = null) }
        if (mode == InputMode.PLEOS || mutable.value.automotive) connect()
    }
    fun connect() {
        source?.close(); source = null
        val available = getApplication<Application>().packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) && runCatching { Class.forName("android.car.Car") }.isSuccess
        if (!available) {
            mutable.update { it.copy(vehicle = VehicleSnapshot(status = mapOf("실행 환경" to "Pleos / Android Automotive 환경이 아니에요", "시연 모드" to "이 기기에서도 60초 시연을 실행할 수 있어요"))) }
            return
        }
        try {
            source = PleosSource(getApplication()) { snapshot -> mutable.update { it.copy(vehicle = snapshot) } }.also { it.start() }
        } catch (e: Exception) { mutable.update { it.copy(message = "차량 연결을 시작하지 못했어요: ${e.javaClass.simpleName}") } }
          catch (_: LinkageError) { mutable.update { it.copy(message = "이 환경에서 차량 API를 사용할 수 없어요. 시연 모드를 이용해주세요.") } }
    }
    fun navigate(screen: Screen) {
        if (mutable.value.controlsLocked && screen !in setOf(Screen.CONNECTION, Screen.JOURNEY)) return
        mutable.update { it.copy(screen = screen, message = null) }
    }
    fun start() {
        val s = mutable.value
        if (s.active) return
        if (s.controlsLocked) { mutable.update { it.copy(message = "차량의 P단과 정차 상태 확인 후 시작할 수 있어요.") }; return }
        speaker.stop()
        engine = CoachEngine(); lastCueAt = null; startedAt = SystemClock.elapsedRealtime(); createdAt = System.currentTimeMillis()
        mutable.update { it.copy(active = true, paused = false, elapsedMs = 0, events = emptyList(), samples = emptyList(), report = null,
            screen = Screen.JOURNEY, cue = "함께 편안한 이동을 준비해요", cueDetail = "안내가 필요한 순간에 알려드릴게요", message = null) }
        passenger?.active = true
        tick?.cancel()
        tick = viewModelScope.launch {
            while (mutable.value.active) {
                delay(if (mutable.value.mode == InputMode.DEMO) 500 else 1000)
                if (!mutable.value.paused) advance()
            }
        }
    }
    private fun advance() {
        val s = mutable.value
        if (!s.active) return
        if (s.mode == InputMode.DEMO && s.controlsLocked) { finish(interrupted = true); return }
        val elapsed = if (s.mode == InputMode.DEMO) (s.elapsedMs + 500).coerceAtMost(DemoJourney.DURATION_MS) else SystemClock.elapsedRealtime() - startedAt
        val sample = if (s.mode == InputMode.DEMO) DemoJourney.sample(elapsed, s.demoStyle) else MotionSample(elapsed, s.vehicle.speedMps, s.vehicle.steeringDeg, s.vehicle.speedAtMs - startedAt, s.vehicle.steeringAtMs - startedAt)
        val hint = if (s.mode == InputMode.DEMO) DemoJourney.hint(elapsed) else s.vehicle.hint?.let { it.copy(atMs = it.atMs - startedAt) }
        engine.accept(sample)
        if (s.mode == InputMode.DEMO && s.comparison && s.elapsedMs < 42000 && elapsed >= 42000) engine.feedback(Feeling.UNEASY, elapsed, simulated = true)
        val cue = engine.cue(hint, elapsed, if (s.sparse) 25000 else 15000)
        if (cue != null) lastCueAt = elapsed
        val expired = lastCueAt?.let { elapsed - it > 12000 } == true
        if (cue != null && hint != null && s.voice) {
            speaker.maneuver(hint.kind, withTip = engine.events.count { it.kind == EventKind.CUE } == 1)
        }
        mutable.update { it.copy(elapsedMs = elapsed, sample = sample, hint = hint, acceleration = engine.acceleration,
            samples = engine.samples.toList(), events = engine.events.toList(),
            cue = cue?.title ?: if (expired) "다음 안내를 기다리고 있어요" else it.cue,
            cueDetail = cue?.detail ?: if (expired) "불편하다면 동승자 휴대폰으로 체감을 남겨주세요" else it.cueDetail) }
        passenger?.cue = if (s.mode == InputMode.DEMO) "시연 중 · ${mutable.value.cue}" else mutable.value.cue
        if (s.mode == InputMode.DEMO && elapsed >= DemoJourney.DURATION_MS) finish()
        if (s.mode == InputMode.PLEOS && elapsed > 3_600_000) finish(interrupted = true)
    }
    fun pause() { if (mutable.value.mode == InputMode.DEMO) mutable.update { it.copy(paused = !it.paused) } }
    fun skip() { if (mutable.value.mode == InputMode.DEMO) repeat(10) { advance() } }
    fun feeling(feeling: Feeling, companion: Boolean = false) {
        val s = mutable.value
        if (!s.active || (s.controlsLocked && !companion) || (s.mode == InputMode.DEMO && s.comparison)) return
        engine.feedback(feeling, s.elapsedMs) ?: return
        mutable.update { it.copy(events = engine.events.toList(), message = "${feeling.label} · 기록했어요") }
        if (feeling == Feeling.REST && s.voice) speaker.rest()
    }
    fun finish(interrupted: Boolean = false) {
        val s = mutable.value
        if (!s.active || (s.controlsLocked && !interrupted)) return
        tick?.cancel(); tick = null; speaker.stop()
        passenger?.active = false; passenger?.cue = "이동 기록을 마쳤어요"
        val report = TripReport(UUID.randomUUID().toString(), createdAt, s.mode, s.elapsedMs, engine.samples.toList(), engine.events.toList(), engine.goal(), interrupted, if (s.mode == InputMode.DEMO) "${s.demoStyle.label}${if (s.comparison) " · 가상 체감 자동 입력" else ""}" else null)
        val history = (listOf(report) + s.history).take(10)
        store.save(history)
        mutable.update { it.copy(active = false, paused = false, report = report, history = history, screen = Screen.REPORT, message = if (interrupted) "기록이 중단되어 지금까지의 이동을 저장했어요" else null) }
    }
    fun openReport(report: TripReport) { if (!mutable.value.controlsLocked) mutable.update { it.copy(report = report, screen = Screen.REPORT) } }
    fun clearHistory() { if (mutable.value.controlsLocked || mutable.value.active) return; store.save(emptyList()); mutable.update { it.copy(history = emptyList(), report = null) } }
    fun toggleVoice() { if (mutable.value.controlsLocked) return; mutable.update { it.copy(voice = !it.voice) }; preferences.edit().putBoolean("voice", mutable.value.voice).apply(); if (!mutable.value.voice) speaker.stop() }
    fun toggleSparse() { if (mutable.value.controlsLocked) return; mutable.update { it.copy(sparse = !it.sparse) }; preferences.edit().putBoolean("sparse", mutable.value.sparse).apply() }
    fun exportJson(): String = store.export(if (mutable.value.screen == Screen.REPORT) mutable.value.report?.let(::listOf) ?: emptyList() else mutable.value.history)
    fun startPairing() {
        if (mutable.value.controlsLocked) return
        if (passenger != null) { stopPairing(); return }
        try {
            passenger = PassengerServer { f -> viewModelScope.launch { feeling(f, companion = true) } }.also { it.active = mutable.value.active; it.cue = mutable.value.cue }
            mutable.update { it.copy(pairingUrls = passenger!!.urls, pairingPath = passenger!!.localPath, message = null) }
        } catch (_: Exception) { mutable.update { it.copy(message = "동승자 연결을 시작하지 못했어요. 포트 또는 네트워크를 확인해주세요.") } }
    }
    private fun stopPairing() { passenger?.close(); passenger = null; mutable.update { it.copy(pairingUrls = emptyList(), pairingPath = null) } }
    fun foreground() { if ((mutable.value.mode == InputMode.PLEOS || mutable.value.automotive) && source == null) connect() }
    fun background() {
        finish(interrupted = true)
        source?.close(); source = null; stopPairing(); speaker.stop()
        mutable.update { it.copy(vehicle = VehicleSnapshot()) }
    }
    fun clearMessage() { mutable.update { it.copy(message = null) } }
    override fun onCleared() { tick?.cancel(); source?.close(); passenger?.close(); speaker.stop(); super.onCleared() }
}
