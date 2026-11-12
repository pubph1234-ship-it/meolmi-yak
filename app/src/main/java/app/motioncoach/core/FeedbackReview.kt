package app.motioncoach.core

/** A review window is an editorial aid, not a clinically validated reaction time. */
data class ReviewWindow(val startMs: Long, val endMs: Long, val changes: List<CoachEvent>, val cues: Int)

object FeedbackReview {
    const val WINDOW_MS = 20_000L
    val motionKinds = setOf(EventKind.ACCELERATION, EventKind.BRAKING, EventKind.STEERING)

    fun windows(events: List<CoachEvent>): List<ReviewWindow> = events
        .filter { it.kind == EventKind.FEEDBACK && it.severity == Feeling.UNEASY.severity }
        .sortedBy { it.atMs }.map { feedback ->
            val start = (feedback.atMs - WINDOW_MS).coerceAtLeast(0)
            val preceding = events.filter { it.atMs in start..feedback.atMs }
            ReviewWindow(start, feedback.atMs, preceding.filter { it.kind in motionKinds }, preceding.count { it.kind == EventKind.CUE })
        }

    // Union of original events prevents overlapping windows from weighting the same event twice.
    fun relatedChanges(events: List<CoachEvent>): List<CoachEvent> {
        val windows = windows(events)
        return events.filter { event -> event.kind in motionKinds && windows.any { event.atMs in it.startMs..it.endMs } }
    }

    fun goal(events: List<CoachEvent>, fallback: () -> String): String {
        if (windows(events).isEmpty()) return fallback()
        val changes = relatedChanges(events)
        if (changes.isEmpty()) return "불편 입력 전 구간에서 기준을 넘은 주행 변화는 확인되지 않았어요. 동승자와 당시 상황을 함께 돌아봐 주세요."
        val counts = changes.groupingBy { it.kind }.eachCount()
        val top = counts.filterValues { it == counts.values.maxOrNull() }.keys
        if (top.size > 1) return "불편 입력 전 여러 종류의 변화가 함께 있었어요. 교통 상황이 허용할 때, 회전과 감속을 미리 준비해보세요."
        return when (top.single()) {
            EventKind.BRAKING -> "불편 입력 전 감속 변화가 기록됐어요. 교통 상황이 허용할 때, 감속을 미리 점진적으로 준비해보세요."
            EventKind.ACCELERATION -> "불편 입력 전 가속 변화가 기록됐어요. 출발하거나 다시 속도를 올릴 때, 가속을 점진적으로 해보세요."
            else -> "불편 입력 전 조향 변화가 기록됐어요. 방향 전환을 미리 준비하고 핸들을 점진적으로 조작해보세요."
        }
    }
}
