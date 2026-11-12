package app.motioncoach.data

import app.motioncoach.core.Feeling
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Optional local-network companion. No accounts, cloud uploads, trip history, or vehicle control endpoints. */
class PassengerServer(private val feedback: (Feeling) -> Unit) {
    private val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
    private val basePath = "/p/$token"
    private val server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(8765)) }
    private val clients = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4))
    @Volatile private var running = true
    @Volatile var active = false
    @Volatile var cue = "아직 주행을 시작하지 않았어요"
    val urls: List<String> = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>().filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
        .map { "http://${it.hostAddress}:8765$basePath" }.distinct()
    val localPath get() = basePath
    init {
        Thread({
            while (running) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                try { clients.execute { socket.use { handle(it) } } } catch (_: Exception) { runCatching { socket.close() } }
            }
        }, "passenger-listener").apply { isDaemon = true; start() }
    }
    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 2500
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val first = readLine(reader, 512) ?: return
            val pieces = first.split(' ')
            if (pieces.size != 3) return
            val method = pieces[0]; val target = pieces[1]
            var length = 0; var contentType = ""; var headerCount = 0; var origin = ""; var host = ""
            while (true) {
                val line = readLine(reader, 1024) ?: return
                if (line.isEmpty()) break
                if (++headerCount > 30) return
                val key = line.substringBefore(':').lowercase(); val value = line.substringAfter(':', "").trim()
                when (key) { "content-length" -> length = value.toIntOrNull() ?: -1; "content-type" -> contentType = value; "origin" -> origin = value; "host" -> host = value }
            }
            if (target != basePath && target != "$basePath/state" && target != "$basePath/feedback") { reply(socket, 404, "text/plain", "Not found"); return }
            if (method == "GET" && target == basePath) { reply(socket, 200, "text/html", page()); return }
            if (method == "GET" && target == "$basePath/state") { reply(socket, 200, "application/json", JSONObject().put("active", active).put("cue", cue).toString()); return }
            if (method != "POST" || target != "$basePath/feedback" || length !in 1..128 || !contentType.startsWith("application/json") || (origin.isNotEmpty() && origin != "http://$host")) { reply(socket, 400, "text/plain", "Bad request"); return }
            val chars = CharArray(length)
            var count = 0
            while (count < length) { val n = reader.read(chars, count, length - count); if (n <= 0) return; count += n }
            val feeling = runCatching { Feeling.valueOf(JSONObject(String(chars)).getString("feeling")) }.getOrNull()
            if (!active || feeling == null) { reply(socket, 409, "application/json", "{\"ok\":false}"); return }
            feedback(feeling)
            reply(socket, 200, "application/json", "{\"ok\":true}")
        } catch (_: Exception) { /* Malformed requests never reach the app or crash the listener. */ }
    }
    private fun readLine(reader: java.io.Reader, limit: Int): String? {
        val b = StringBuilder()
        while (b.length <= limit) { val c = reader.read(); if (c < 0) return null; if (c == 10) return b.toString().removeSuffix("\r"); b.append(c.toChar()) }
        return null
    }
    private fun reply(socket: Socket, code: Int, type: String, body: String) {
        val data = body.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $code ${if(code==200) "OK" else "Error"}\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${data.size}\r\nConnection: close\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-$token'; connect-src 'self'; frame-ancestors 'none'\r\n\r\n"
        socket.getOutputStream().apply { write(headers.toByteArray()); write(data); flush() }
    }
    private fun page() = """<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>멀미약 동승자</title>
        <style>body{margin:0;background:#F5F7F5;color:#183B37;font:18px system-ui,sans-serif}main{max-width:520px;margin:40px auto;padding:24px}h1{font-size:32px;margin:12px 0}small{color:#607772}#cue{font-size:23px;line-height:1.5;margin:32px 0;min-height:100px}button{display:block;width:100%;padding:22px;border:1px solid #d5e3df;border-radius:18px;margin:14px 0;background:white;color:#183B37;font:600 21px system-ui}button:nth-of-type(3){background:#f2e5d3}button:disabled{opacity:.45}#result{min-height:30px;color:#127A70}footer{font-size:14px;color:#607772;line-height:1.6;margin-top:32px}</style>
        <main><small>동승자 전용</small><h1>지금 어떠세요?</h1><p id="cue">연결 확인 중…</p><button data-feeling="OK">괜찮아요</button><button data-feeling="UNEASY">조금 불편해요</button><button data-feeling="REST">쉬고 싶어요</button><p id="result" role="status"></p><footer>운전자는 사용하지 마세요. 입력 후 화면을 내려놓으세요.<br>같은 네트워크의 차량 앱으로만 전송합니다.<br>공유 링크를 아는 사람은 입력할 수 있어요.</footer></main>
        <script nonce="$token">let busy=false;let enabled=false;let cooldown=0;const buttons=[...document.querySelectorAll('button')];function lock(){buttons.forEach(b=>b.disabled=busy||!enabled||Date.now()<cooldown)}async function state(){try{const r=await fetch(location.pathname+'/state');const s=await r.json();enabled=s.active;document.querySelector('#cue').textContent=s.cue;lock()}catch(e){enabled=false;document.querySelector('#cue').textContent='차량 앱과 연결이 끊겼어요';lock()}}buttons.forEach(b=>b.onclick=async()=>{busy=true;lock();try{let r=await fetch(location.pathname+'/feedback',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({feeling:b.dataset.feeling})});document.querySelector('#result').textContent=r.ok?'보냈어요. 잠시 화면을 내려놓으세요.':'주행 시작 후 다시 시도해주세요.';cooldown=Date.now()+3500}catch(e){document.querySelector('#result').textContent='전송하지 못했어요. 연결을 확인해주세요.'}busy=false;lock()});state();setInterval(state,1500);</script></html>""".trimIndent()
    fun close() { running = false; active = false; runCatching { server.close() }; clients.shutdownNow() }
}
