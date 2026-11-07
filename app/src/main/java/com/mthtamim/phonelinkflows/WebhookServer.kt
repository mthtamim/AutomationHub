package com.mthtamim.phonelinkflows

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * v2.8: tiny LAN webhook server. A GET to http://<phone-ip>:37544/hook/<name>
 * from the same Wi-Fi fires the automation event "webhook" with title <name>.
 * v1.4.12: the endpoint now requires a token — ?token=<token> or the
 * X-Webhook-Token header. The token is generated on first use, stored in
 * phonelink_cfg ("webhook_token") and shown in Settings → Smart so the
 * caller can copy it. Token-less requests get 403 and fire nothing:
 * anyone on the same Wi-Fi used to be able to trigger any webhook.
 * (The internet-side equivalent already exists: peer commands over the relay.)
 */
object WebhookServer {
    private const val PORT = 37544
    private const val PREFS = "phonelink_cfg"
    private const val TOKEN_KEY = "webhook_token"
    @Volatile private var server: ServerSocket? = null
    @Volatile private var running = false

    /** The LAN webhook token, generated once and kept in prefs. */
    fun token(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(TOKEN_KEY, "") ?: ""
        if (existing.isNotEmpty()) return existing
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val fresh = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(TOKEN_KEY, fresh).apply()
        return fresh
    }

    /** Throw the old token away and make a new one (Settings → Smart). */
    fun regenerateToken(ctx: Context): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val fresh = bytes.joinToString("") { "%02x".format(it) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(TOKEN_KEY, fresh).apply()
        return fresh
    }

    fun start(ctx: Context) {
        if (running) return
        val app = ctx.applicationContext
        running = true
        thread {
            try {
                val ss = ServerSocket(PORT)
                server = ss
                while (running) {
                    val sock = try { ss.accept() } catch (e: Exception) { break }
                    thread {
                        try {
                            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                            val line = reader.readLine() ?: ""
                            // Read the headers too — the token may come
                            // as X-Webhook-Token instead of ?token=.
                            var headerToken = ""
                            try {
                                var h = reader.readLine()
                                while (h != null && h.isNotEmpty()) {
                                    if (h.startsWith("X-Webhook-Token:",
                                            ignoreCase = true)) {
                                        headerToken = h.substringAfter(":")
                                            .trim()
                                    }
                                    h = reader.readLine()
                                }
                            } catch (_: Exception) { }
                            val parts = line.split(" ")
                            var authorized = false
                            var name = ""
                            if (parts.size >= 2 && parts[0] == "GET" &&
                                parts[1].startsWith("/hook/")) {
                                var path = parts[1].removePrefix("/hook/")
                                var query = ""
                                val q = path.indexOf('?')
                                if (q >= 0) {
                                    query = path.substring(q + 1)
                                    path = path.substring(0, q)
                                }
                                name = try { URLDecoder.decode(path, "UTF-8") }
                                    catch (e: Exception) { path }
                                var queryToken = ""
                                for (pair in query.split("&")) {
                                    if (pair.startsWith("token=")) {
                                        queryToken = try {
                                            URLDecoder.decode(
                                                pair.substringAfter("="),
                                                "UTF-8")
                                        } catch (e: Exception) {
                                            pair.substringAfter("=")
                                        }
                                    }
                                }
                                val want = token(app)
                                authorized = (queryToken.isNotEmpty() &&
                                    queryToken == want) ||
                                    (headerToken.isNotEmpty() &&
                                        headerToken == want)
                            }
                            val out = sock.getOutputStream()
                            if (authorized) {
                                Automation.onEvent(app, "webhook",
                                    JSONObject().put("title", name).put("body", name))
                                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n" +
                                    "Content-Length: 2\r\n\r\nok").toByteArray())
                            } else {
                                out.write(("HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\n" +
                                    "Content-Length: 9\r\n\r\nforbidden").toByteArray())
                            }
                            out.flush()
                        } catch (_: Exception) { }
                        try { sock.close() } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) { }
            running = false
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) { }
        server = null
    }
}
