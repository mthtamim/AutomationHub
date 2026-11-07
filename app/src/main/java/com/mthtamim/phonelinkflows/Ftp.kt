package com.mthtamim.phonelinkflows

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * v2.9: a tiny plain-FTP client for the ftp_upload / ftp_download
 * automation actions. Param: host|user|pass|remotePath|localPath
 * (port 21, passive mode). Returns false on any failure.
 * v1.4.12: explicit FTPS (AUTH TLS) via transferSecure — same
 * param format, control + data connections wrapped in TLS
 * (PBSZ 0 / PROT P). Plain FTP stays for servers without TLS.
 */
object Ftp {

    fun transfer(ctx: Context, param: String, upload: Boolean): Boolean =
        transferInternal(param, upload, secure = false)

    fun transferSecure(ctx: Context, param: String, upload: Boolean): Boolean =
        transferInternal(param, upload, secure = true)

    private fun transferInternal(param: String, upload: Boolean,
                                 secure: Boolean): Boolean {
        val parts = param.split("|")
        if (parts.size < 5) return false
        val host = parts[0].trim()
        val user = parts[1].trim().ifEmpty { "anonymous" }
        val pass = parts[2]
        val remote = parts[3].trim()
        val local = parts[4].trim()
        if (host.isEmpty() || remote.isEmpty() || local.isEmpty()) return false
        var ctrl: Socket? = null
        return try {
            ctrl = Socket(host, 21)
            ctrl.soTimeout = 15000
            var reader = BufferedReader(InputStreamReader(ctrl.getInputStream()))
            var out = ctrl.getOutputStream()
            fun reply(): String {
                var line = reader.readLine() ?: ""
                while (line.length >= 4 && line[3] == '-')
                    line = reader.readLine() ?: ""
                return line
            }
            fun cmd(s: String): String {
                out.write((s + "\r\n").toByteArray())
                out.flush()
                return reply()
            }
            reply() // greeting
            if (secure) {
                // Explicit FTPS: upgrade the plain control
                // connection to TLS before any credential is sent.
                val auth = cmd("AUTH TLS")
                if (!auth.startsWith("234")) return false
                val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(ctrl, host, 21, true) as javax.net.ssl.SSLSocket
                ssl.startHandshake()
                ctrl = ssl
                reader = BufferedReader(InputStreamReader(ctrl.getInputStream()))
                out = ctrl.getOutputStream()
            }
            cmd("USER $user")
            cmd("PASS $pass")
            if (secure) {
                cmd("PBSZ 0")
                cmd("PROT P")
            }
            cmd("TYPE I")
            val pasv = cmd("PASV")
            val nums = Regex("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)")
                .find(pasv)?.groupValues ?: return false
            val dHost = "${nums[1]}.${nums[2]}.${nums[3]}.${nums[4]}"
            val dPort = nums[5].toInt() * 256 + nums[6].toInt()
            var data: Socket = Socket(dHost, dPort)
            data.soTimeout = 20000
            if (secure) {
                val sslData = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(data, dHost, dPort, true) as javax.net.ssl.SSLSocket
                sslData.startHandshake()
                data = sslData
            }
            if (upload) {
                val f = File(local)
                if (!f.exists()) { data.close(); cmd("QUIT"); return false }
                cmd("STOR $remote")
                data.getOutputStream().use { os ->
                    f.inputStream().use { it.copyTo(os) }
                }
            } else {
                cmd("RETR $remote")
                val f = File(local)
                f.parentFile?.mkdirs()
                data.getInputStream().use { ins ->
                    f.outputStream().use { ins.copyTo(it) }
                }
            }
            data.close()
            cmd("QUIT")
            true
        } catch (e: Exception) {
            false
        } finally {
            try { ctrl?.close() } catch (_: Exception) { }
        }
    }
}
