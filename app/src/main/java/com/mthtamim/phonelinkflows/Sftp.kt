package com.mthtamim.phonelinkflows

import android.content.Context
import java.io.File

/**
 * v1.4.12: SFTP client for the sftp_upload / sftp_download
 * automation actions (mwiede JSch fork). Param, like FTP:
 * host|user|pass|remotePath|localPath (port 22). Password
 * authentication only; host keys are not verified
 * (StrictHostKeyChecking=no) — the same trade-off as the plain
 * FTP client, documented so the user knows: use it against
 * servers you trust, on networks you trust. Returns false on
 * any failure.
 */
object Sftp {

    fun transfer(ctx: Context, param: String, upload: Boolean): Boolean {
        val parts = param.split("|")
        if (parts.size < 5) return false
        return transferParts(parts[0].trim(), parts[1].trim(),
            parts[2], parts[3].trim(), parts[4].trim(), upload)
    }

    /** Same fields as transfer(), split out for the Flow engine,
     *  which resolves the local path first. Order matches
     *  Ftp.transfer's param: remote path, then local path. */
    fun transferParts(host: String, user: String, pass: String,
                      remote: String, local: String,
                      upload: Boolean): Boolean {
        if (host.isEmpty() || remote.isEmpty() || local.isEmpty()) return false
        val jsch = com.github.mwiede.jsch.JSch()
        val session = try {
            val s = jsch.getSession(user.ifEmpty { "anonymous" }, host, 22)
            s.setPassword(pass)
            // No known-hosts file ships with the app; without this
            // every server is rejected. See the header note.
            s.setConfig("StrictHostKeyChecking", "no")
            s.connect(15000)
            s
        } catch (e: Exception) {
            return false
        }
        val channel = try {
            val c = session.openChannel("sftp")
                as com.github.mwiede.jsch.ChannelSftp
            c.connect(15000)
            c
        } catch (e: Exception) {
            try { session.disconnect() } catch (_: Exception) { }
            return false
        }
        return try {
            if (upload) {
                val f = File(local)
                if (!f.exists()) return false
                channel.put(f.absolutePath, remote)
            } else {
                val f = File(local)
                f.parentFile?.mkdirs()
                channel.get(remote, local)
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            try { channel.disconnect() } catch (_: Exception) { }
            try { session.disconnect() } catch (_: Exception) { }
        }
    }
}
