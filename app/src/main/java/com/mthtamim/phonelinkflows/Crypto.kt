package com.mthtamim.phonelinkflows

import java.security.MessageDigest

/**
 * AutomationHub: the relay end-to-end encryption (key derived from
 * the pair code) is gone with the relay. Only pinHash survives —
 * it hashes the AppLock PIN (and the command PIN digest in Config).
 */
object Crypto {

    fun pinHash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("phonelink-pin-v1-" + pin).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
