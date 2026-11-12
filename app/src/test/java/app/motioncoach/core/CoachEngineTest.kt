package app.motioncoach.core

import org.junit.Assert.*
import org.junit.Test

class CoachEngineTest {
    @Test fun `repeated cached values do not shorten actual measurement interval`() {
        val e = CoachEngine()
        e.accept(MotionSample(0, 10.0, 0.0, speedAtMs = 0))
        e.accept(MotionSample(500, 10.0, 0.0, speedAtMs = 0))
        e.accept(MotionSample(1000, 12.0, 0.0, speedAtMs = 1000))
        assertEquals(2.0, e.acceleration!!, 0.001)
        assertTrue(e.events.isEmpty())
    }
    @Test fun `missing and invalid signals cannot create driving events`() {
        val e = CoachEngine()
        e.accept(MotionSample(0, 10.0, 0.0))
        e.accept(MotionSample(1000, Double.NaN, Double.POSITIVE_INFINITY))
        e.accept(MotionSample(2000, -3.0, 5000.0))
        e.accept(MotionSample(3000, null, null))
        assertTrue(e.events.isEmpty()); assertNull(e.acceleration)
        assertTrue(e.samples.drop(1).all { it.speedMps == null && it.steeringDeg == null })
    }
    @Test fun `out of order samples are ignored`() {
        val e = CoachEngine()
        e.accept(MotionSample(1000, 10.0, 0.0))
        e.accept(MotionSample(1000, 30.0, 100.0))
        e.accept(MotionSample(500, 30.0, 100.0))
        assertEquals(1, e.samples.size); assertTrue(e.events.isEmpty())
    }
    @Test fun `reconnecting after a data gap does not invent acceleration`() {
        val e = CoachEngine()
        e.accept(MotionSample(0, 0.0, 0.0)); e.accept(MotionSample(10000, 25.0, 90.0))
        assertNull(e.acceleration); assertTrue(e.events.isEmpty())
    }
    @Test fun `large braking is recorded once within cooldown`() {
        val e = CoachEngine()
        for (i in 0..4) e.accept(MotionSample(i * 1000L, 25.0 - i * 3.0, 0.0))
        assertEquals(1, e.events.count { it.kind == EventKind.BRAKING })
        assertTrue(e.goal().contains("감속"))
    }
    @Test fun `steering when parked does not count as a driving change`() {
        val e = CoachEngine(); e.accept(MotionSample(0, 0.0, 0.0)); e.accept(MotionSample(1000, 0.0, 300.0))
        assertTrue(e.events.isEmpty())
    }
    @Test fun `cue requires fresh route fresh speed and plausible arrival time`() {
        val e = CoachEngine(); e.accept(MotionSample(10000, 10.0, 0.0))
        assertNull(e.cue(RouteHint("stale", Maneuver.LEFT, 50.0, 7000), 10000))
        assertNull(e.cue(RouteHint("future", Maneuver.LEFT, 50.0, 11000), 10000))
        assertNull(e.cue(RouteHint("late", Maneuver.LEFT, 10.0, 10000), 10000))
        assertNull(e.cue(RouteHint("far", Maneuver.LEFT, 200.0, 10000), 10000))
        assertNull(e.cue(RouteHint("bad", Maneuver.LEFT, Double.NaN, 10000), 10000))
        assertNotNull(e.cue(RouteHint("ok", Maneuver.LEFT, 50.0, 10000), 10000))
    }
    @Test fun `stale speed cannot trigger a current route cue`() {
        val e = CoachEngine(); e.accept(MotionSample(0, 10.0, 0.0))
        assertNull(e.cue(RouteHint("turn", Maneuver.LEFT, 60.0, 3000), 3000))
    }
    @Test fun `route is announced once and other routes respect minimum interval`() {
        val e = CoachEngine(); e.accept(MotionSample(0, 10.0, 0.0))
        assertNotNull(e.cue(RouteHint("a", Maneuver.LEFT, 60.0, 0), 0))
        e.accept(MotionSample(20000, 10.0, 0.0))
        assertNull(e.cue(RouteHint("a", Maneuver.LEFT, 60.0, 20000), 20000))
        assertNotNull(e.cue(RouteHint("b", Maneuver.RIGHT, 60.0, 20000), 20000))
        e.accept(MotionSample(21000, 10.0, 0.0))
        assertNull(e.cue(RouteHint("c", Maneuver.BUMP, 60.0, 21000), 21000))
    }
    @Test fun `no speed means no prediction`() {
        val e = CoachEngine(); e.accept(MotionSample(0, null, 20.0))
        assertNull(e.cue(RouteHint("a", Maneuver.RIGHT, 60.0, 0), 0))
    }
    @Test fun `feedback duplicate taps are throttled and rest remains a request`() {
        val e = CoachEngine()
        assertNotNull(e.feedback(Feeling.UNEASY, 1000))
        assertNull(e.feedback(Feeling.UNEASY, 1500))
        val rest = e.feedback(Feeling.REST, 4000)
        assertNotNull(rest); assertTrue(rest!!.detail.contains("안전한 장소"))
        assertEquals(2, e.events.size)
    }
    @Test fun `demo completes with route cues motion changes and user feedback in same timeline`() {
        val e = CoachEngine()
        for (ms in 0L..DemoJourney.DURATION_MS step 500) {
            e.accept(DemoJourney.sample(ms)); e.cue(DemoJourney.hint(ms), ms)
            if (ms == 18000L) e.feedback(Feeling.UNEASY, ms)
        }
        assertEquals(0.0, e.samples.last().speedMps!!, 0.001)
        assertTrue(e.events.count { it.kind == EventKind.CUE } >= 2)
        assertTrue(e.events.any { it.kind == EventKind.BRAKING })
        assertTrue(e.events.any { it.kind == EventKind.STEERING })
        assertEquals(18000L, e.events.first { it.kind == EventKind.FEEDBACK }.atMs)
        assertTrue(e.events.zipWithNext().all { (a, b) -> a.atMs <= b.atMs })
    }
    @Test fun `insufficient data yields an explicit limitation instead of a score`() {
        val e = CoachEngine(); e.accept(MotionSample(0, null, null))
        assertTrue(e.goal().contains("부족"))
    }
}
