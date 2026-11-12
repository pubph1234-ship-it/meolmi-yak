package app.motioncoach.data

import android.content.Context
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.VehicleGear
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.CautionPoint
import app.motioncoach.core.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

data class VehicleSnapshot(
    val speedMps: Double? = null, val steeringDeg: Double? = null,
    val parked: Boolean = false, val restricted: Boolean = true,
    val hint: RouteHint? = null, val status: Map<String, String> = emptyMap(),
    val speedAtMs: Long = 0, val steeringAtMs: Long = 0
)

/** Read-only adapters. No set/control APIs. Missing signals stay null. */
class PleosSource(private val context: Context, private val deliver: (VehicleSnapshot) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val polling = AtomicBoolean(false)
    private var running = false
    private var generation = 0
    private var vehicle: Vehicle? = null
    private var navigation: NaviHelper? = null
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var ux: CarUxRestrictionsManager? = null
    private var speed: Double? = null
    private var steering: Double? = null
    private var speedAt = 0L
    private var steeringAt = 0L
    private var park = false
    private var restricted = true
    private var navConnected = false
    private var turn: RouteHint? = null
    private var caution: RouteHint? = null
    private var turnCounter = 0
    private val status = linkedMapOf("차량 속도" to "연결 대기", "조향각" to "연결 대기", "내비게이션" to "연결 대기", "주행 제한" to "확인 대기")

    private fun update(block: () -> Unit) { main.post { if (running) { block(); publish() } } }
    private fun failure(key: String, error: Throwable) = update { status[key] = "연결 안 됨 (${error.javaClass.simpleName})" }
    private val propertyListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>?) {
            if (value == null) return
            update {
                when (value.propertyId) {
                    VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                        speed = if (value.status == CarPropertyValue.STATUS_AVAILABLE) (value.value as? Number)?.toDouble()?.let(::abs)?.takeIf { it.isFinite() && it <= 100 } else null
                        speedAt = (value.timestamp / 1_000_000).takeIf { it > 0 } ?: SystemClock.elapsedRealtime()
                        status["차량 속도"] = if (speed != null) "AAOS 수신 중" else "신호 사용 불가"
                    }
                    VehiclePropertyIds.GEAR_SELECTION -> { park = value.status == CarPropertyValue.STATUS_AVAILABLE && value.value == VehicleGear.GEAR_PARK }
                }
            }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) = update {
            if (propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) { speed = null; status["차량 속도"] = "신호 오류" }
            if (propertyId == VehiclePropertyIds.GEAR_SELECTION) park = false
        }
    }
    private val navListener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtInfo: List<TBTInfo>) = update {
            val first = tbtInfo.firstOrNull { maneuver(it.type.name) != null && it.distance.isFinite() && it.distance >= 0 }
            if (first == null) turn = null else {
                val kind = maneuver(first.type.name)!!
                val old = turn
                if (old == null || old.kind != kind || first.distance > old.distanceM + 60) turnCounter++
                turn = RouteHint("turn-$turnCounter-${kind.name}", kind, first.distance, SystemClock.elapsedRealtime())
            }
            status["내비게이션"] = if (first == null) "연결됨 · 안내할 회전 없음" else "회전 안내 수신 중"
        }
        override fun onCautionPointInfo(cautionPoints: List<CautionPoint>) = update {
            val point = cautionPoints.firstOrNull { it.type.name in setOf("SPEED_BUMP", "DOWNHILL_CAUTION_AREA") && it.remainDistance >= 0 }
            caution = point?.let { RouteHint("caution-${it.id}", if (it.type.name == "SPEED_BUMP") Maneuver.BUMP else Maneuver.DOWNHILL, it.remainDistance.toDouble(), SystemClock.elapsedRealtime()) }
        }
        override fun onRouteCancelled() = update { turn = null; caution = null; turnCounter++; status["내비게이션"] = "경로 안내 종료" }
        override fun onReRouteEnded() = update { turn = null; caution = null; turnCounter++ }
        override fun onRouteEnded() = update { turn = null; caution = null }
    }

    fun start() {
        if (running) return
        running = true
        val token = ++generation
        try {
            vehicle = Vehicle(context.applicationContext).also { it.initialize() }
        } catch (e: Exception) { failure("조향각", e) }
        try {
            navigation = NaviHelper(context.applicationContext).also { n ->
                n.initialize(onConnected = {
                    main.post {
                        if (running && generation == token) {
                            navConnected = true; n.addListener(navListener); n.getTBTInfo()
                            status["내비게이션"] = "연결됨 · 안내 대기"; publish()
                        }
                    }
                }, onFailed = { failure("내비게이션", it) })
            }
        } catch (e: Exception) { failure("내비게이션", e) }
        try {
            car = Car.createCar(context.applicationContext, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!running || generation != token) return@createCar
                if (!ready) { update { speed = null; park = false; restricted = true; status["차량 속도"] = "차량 서비스 연결 끊김" }; return@createCar }
                try {
                    properties = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                    val speedRegistered = properties!!.registerCallback(propertyListener, VehiclePropertyIds.PERF_VEHICLE_SPEED, 5f)
                    properties!!.registerCallback(propertyListener, VehiclePropertyIds.GEAR_SELECTION, 0f)
                    if (!speedRegistered) status["차량 속도"] = "속도 속성 미지원"
                } catch (e: Exception) { failure("차량 속도", e) }
                try {
                    ux = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    fun applyRestrictions(r: CarUxRestrictions) = update {
                        restricted = r.isRequiresDistractionOptimization || r.activeRestrictions != CarUxRestrictions.UX_RESTRICTIONS_BASELINE
                        status["주행 제한"] = if (restricted) "주행 중 간단 안내만 표시" else "정차용 화면 사용 가능"
                    }
                    ux!!.registerListener { applyRestrictions(it) }
                    applyRestrictions(ux!!.currentCarUxRestrictions)
                } catch (e: Exception) { failure("주행 제한", e) }
            }
        } catch (e: Exception) { failure("차량 속도", e) }
        main.post(poll)
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            val token = generation
            if (polling.compareAndSet(false, true)) worker.execute {
                try {
                runCatching {
                    properties?.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)?.let { propertyListener.onChangeEvent(it) }
                    properties?.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)?.let { propertyListener.onChangeEvent(it) }
                }.onFailure { failure("차량 속도", it) }
                runCatching {
                    vehicle?.getSteeringWheel()?.getCurrentSteeringWheelAngle(onComplete = { angle ->
                        update {
                            if (token == generation) {
                                steering = angle?.toDouble()?.takeIf { it.isFinite() && abs(it) <= 1080 }
                                steeringAt = SystemClock.elapsedRealtime()
                                status["조향각"] = if (steering != null) "Vehicle SDK 수신 중" else "신호 사용 불가"
                            }
                        }
                    }, onFailed = { failure("조향각", it) })
                }.onFailure { failure("조향각", it) }
                if (navConnected) runCatching { navigation?.getTBTInfo() }.onFailure { failure("내비게이션", it) }
                } finally { polling.set(false) }
            }
            publish()
            main.postDelayed(this, 1000)
        }
    }
    private fun publish() {
        val now = SystemClock.elapsedRealtime()
        val validSpeed = speed.takeIf { now - speedAt in 0..2500 }
        val validSteering = steering.takeIf { now - steeringAt in 0..2500 }
        val hint = listOfNotNull(turn, caution).filter { now - it.atMs in 0..2500 }.minByOrNull { it.distanceM }
        deliver(VehicleSnapshot(validSpeed, validSteering, park && validSpeed != null && validSpeed < 0.5, restricted, hint, status.toMap(), speedAt, steeringAt))
    }
    fun close() {
        running = false; generation++; main.removeCallbacks(poll)
        runCatching { properties?.unregisterCallback(propertyListener) }
        runCatching { ux?.unregisterListener() }
        runCatching { car?.disconnect() }
        runCatching { navigation?.removeListener(navListener); navigation?.release() }
        runCatching { vehicle?.release() }
        worker.shutdownNow()
    }
    private fun maneuver(type: String): Maneuver? = when (type) {
        "TURN_LEFT", "TURN_SHARP_L" -> Maneuver.LEFT
        "TURN_RIGHT", "TURN_SHARP_R" -> Maneuver.RIGHT
        else -> null
    }
}
