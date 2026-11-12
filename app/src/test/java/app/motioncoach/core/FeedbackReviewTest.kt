package app.motioncoach.core

import org.junit.Assert.*
import org.junit.Test

class FeedbackReviewTest {
    private fun event(at: Long, kind: EventKind, severity: Int = 0) = CoachEvent(at, kind, "", "", severity)
    @Test fun `only discomfort creates review windows`() {
        val events = listOf(event(1000, EventKind.FEEDBACK, 0), event(5000, EventKind.FEEDBACK, 2))
        assertTrue(FeedbackReview.windows(events).isEmpty())
        assertEquals("fallback", FeedbackReview.goal(events) { "fallback" })
    }
    @Test fun `window includes bounds but excludes later and earlier events`() {
        val events = listOf(event(9999, EventKind.BRAKING), event(10000, EventKind.ACCELERATION),
            event(30000, EventKind.STEERING), event(30001, EventKind.BRAKING), event(30000, EventKind.FEEDBACK, 1))
        assertEquals(listOf(EventKind.ACCELERATION, EventKind.STEERING), FeedbackReview.relatedChanges(events).map { it.kind })
    }
    @Test fun `overlapping windows do not double count and cues are context only`() {
        val events = listOf(event(15000, EventKind.BRAKING), event(16000, EventKind.CUE),
            event(20000, EventKind.FEEDBACK, 1), event(25000, EventKind.FEEDBACK, 1))
        assertEquals(1, FeedbackReview.relatedChanges(events).size)
        assertEquals(1, FeedbackReview.windows(events).first().cues)
        assertTrue(FeedbackReview.goal(events) { "fallback" }.contains("감속"))
    }
    @Test fun `discomfort without observed changes does not invent cause`() {
        val events = listOf(event(1000, EventKind.FEEDBACK, 1))
        assertEquals(0L, FeedbackReview.windows(events).single().startMs)
        assertTrue(FeedbackReview.goal(events) { "fallback" }.contains("확인되지"))
    }
    @Test fun `related events take priority over overall totals`() {
        val events = (1..5).map { event(it * 1000L, EventKind.BRAKING) } +
            listOf(event(40000, EventKind.STEERING), event(42000, EventKind.FEEDBACK, 1))
        assertTrue(FeedbackReview.goal(events) { "전체 감속" }.contains("조향"))
    }
    @Test fun `tied kinds produce a combined goal`() {
        val events = listOf(event(15000, EventKind.BRAKING), event(16000, EventKind.STEERING), event(20000, EventKind.FEEDBACK, 1))
        assertTrue(FeedbackReview.goal(events) { "fallback" }.contains("여러 종류"))
    }
    @Test fun `two demos use same feedback time but yield different evidence and goals`() {
        fun run(style: DemoStyle): CoachEngine = CoachEngine().also { e ->
            for (ms in 0L..DemoJourney.DURATION_MS step 500) {
                e.accept(DemoJourney.sample(ms, style)); e.cue(DemoJourney.hint(ms), ms)
                if (ms == 42000L) e.feedback(Feeling.UNEASY, ms, simulated = true)
            }
        }
        val variable = run(DemoStyle.VARIABLE); val smooth = run(DemoStyle.SMOOTH)
        assertTrue(FeedbackReview.relatedChanges(variable.events).isNotEmpty())
        assertTrue(FeedbackReview.relatedChanges(smooth.events).isEmpty())
        assertNotEquals(variable.goal(), smooth.goal())
        assertTrue(smooth.events.single { it.kind == EventKind.FEEDBACK }.detail.contains("가상"))
    }
}
