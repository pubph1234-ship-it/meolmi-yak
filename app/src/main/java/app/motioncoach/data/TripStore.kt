package app.motioncoach.data

import android.content.Context
import app.motioncoach.core.*
import org.json.JSONArray
import org.json.JSONObject

class TripStore(context: Context) {
    private val preferences = context.getSharedPreferences("journeys", Context.MODE_PRIVATE)
    fun load(): List<TripReport> = runCatching {
        val arr = JSONArray(preferences.getString("reports", "[]"))
        (0 until arr.length()).map { decode(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())
    fun save(reports: List<TripReport>) {
        preferences.edit().putString("reports", JSONArray().apply { reports.take(10).forEach { put(encode(it)) } }.toString()).apply()
    }
    fun export(reports: List<TripReport>): String = JSONObject().put("schema", "motioncoach.trip.v1")
        .put("notice", "주행 변화 추정과 주관적 체감 기록입니다. 의료 또는 안전 점수가 아닙니다.")
        .put("trips", JSONArray().apply { reports.forEach { put(encode(it)) } }).toString(2)
    private fun encode(t: TripReport) = JSONObject().put("id", t.id).put("createdAt", t.createdAt)
        .put("scenario", t.scenario ?: JSONObject.NULL).put("mode", t.mode.name).put("durationMs", t.durationMs).put("goal", t.goal).put("interrupted", t.interrupted)
        .put("samples", JSONArray().apply { t.samples.forEach { s -> put(JSONObject().put("atMs", s.atMs)
            .put("speedMps", s.speedMps ?: JSONObject.NULL).put("steeringDeg", s.steeringDeg ?: JSONObject.NULL)
            .put("speedAtMs", s.speedAtMs).put("steeringAtMs", s.steeringAtMs)) } })
        .put("events", JSONArray().apply { t.events.forEach { e -> put(JSONObject().put("atMs", e.atMs).put("kind", e.kind.name)
            .put("title", e.title).put("detail", e.detail).put("severity", e.severity)) } })
    private fun decode(j: JSONObject): TripReport {
        val samples = j.getJSONArray("samples")
        val events = j.getJSONArray("events")
        return TripReport(j.getString("id"), j.getLong("createdAt"), InputMode.valueOf(j.getString("mode")), j.getLong("durationMs"),
            (0 until samples.length()).map { samples.getJSONObject(it).let { s -> MotionSample(s.getLong("atMs"),
                if (s.isNull("speedMps")) null else s.getDouble("speedMps"), if (s.isNull("steeringDeg")) null else s.getDouble("steeringDeg"),
                s.optLong("speedAtMs", s.getLong("atMs")), s.optLong("steeringAtMs", s.getLong("atMs"))) } },
            (0 until events.length()).map { events.getJSONObject(it).let { e -> CoachEvent(e.getLong("atMs"), EventKind.valueOf(e.getString("kind")), e.getString("title"), e.getString("detail"), e.optInt("severity")) } },
            j.getString("goal"), j.optBoolean("interrupted"), if (j.isNull("scenario")) null else j.optString("scenario"))
    }
}
