package app.motioncoach

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.motioncoach.core.Feeling
import app.motioncoach.data.PassengerServer
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PassengerServerTest {
    @Test fun companionRequiresTokenActiveTripAndSameOrigin() {
        val received = AtomicReference<Feeling>()
        val latch = CountDownLatch(1)
        val server = PassengerServer { received.set(it); latch.countDown() }
        try {
            assertTrue(request("GET", "/p/wrong/state").startsWith("HTTP/1.1 404"))
            val path = server.localPath
            assertTrue(request("POST", "$path/feedback", "{\"feeling\":\"UNEASY\"}").startsWith("HTTP/1.1 409"))
            server.active = true; server.cue = "곧 오른쪽으로 회전합니다"
            assertTrue(request("POST", "$path/feedback", "{\"feeling\":\"UNEASY\"}", "https://example.com").startsWith("HTTP/1.1 400"))
            assertNull(received.get())
            assertTrue(request("POST", "$path/feedback", "{\"feeling\":\"REST\"}").startsWith("HTTP/1.1 200"))
            assertTrue(latch.await(2, TimeUnit.SECONDS)); assertEquals(Feeling.REST, received.get())
            val state = request("GET", "$path/state")
            assertTrue(state.contains("곧 오른쪽으로 회전합니다"))
            assertFalse(state.contains("samples")); assertFalse(state.contains("history"))
        } finally { server.close() }
    }
    private fun request(method: String, path: String, body: String = "", origin: String = "http://127.0.0.1:8765"): String = Socket("127.0.0.1", 8765).use { socket ->
        socket.soTimeout = 4000
        val headers = "$method $path HTTP/1.1\r\nHost: 127.0.0.1:8765\r\nOrigin: $origin\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body"
        socket.getOutputStream().apply { write(headers.toByteArray()); flush() }
        socket.getInputStream().bufferedReader().readText()
    }
}
