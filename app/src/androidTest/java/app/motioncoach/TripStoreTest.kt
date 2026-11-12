package app.motioncoach

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.motioncoach.core.*
import app.motioncoach.data.TripStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripStoreTest {
    @Test fun persistsModeMissingSignalsAndFeedbackWithoutInventingValues() {
        // Test APK storage: never clears the user's app history.
        val store = TripStore(InstrumentationRegistry.getInstrumentation().context)
        val report = TripReport("test", 1000, InputMode.PLEOS, 2000,
            listOf(MotionSample(500, null, 12.0, 400, 450)),
            listOf(CoachEvent(1000, EventKind.FEEDBACK, "조금 불편해요", "직접 입력", 1)), "목표", true, "미리 준비한 운전 · 가상 체감 자동 입력")
        try {
            store.save(listOf(report))
            assertEquals(report, store.load().single())
            assertTrue(store.export(store.load()).contains("\"speedMps\": null"))
            store.save((0..11).map { report.copy(id = "$it") })
            assertEquals(10, store.load().size)
        } finally { store.save(emptyList()) }
    }
}
