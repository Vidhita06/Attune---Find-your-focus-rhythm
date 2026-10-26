package com.attune.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.attune.prediction.decision.SenderHasher
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Per-install HMAC-SHA256 key generated inside Android Keystore (never exported, never backed up, deleted on uninstall).
 * Used for sender ids, so stored ids cannot be matched against a list of contact names. No custom crypto: the platform Mac
 * does the work. If the keystore is unusable on a device, [mac] returns null and sender-level personalization is simply unavailable
 * (there is deliberately no weaker fallback).
 */
class KeystoreSenderHasher : SenderHasher {
    private val key: SecretKey? by lazy { runCatching { loadOrCreate() }.getOrNull() }

    override fun mac(data: ByteArray): ByteArray? {
        val k = key ?: return null
        return runCatching { Mac.getInstance("HmacSHA256").run { init(k); doFinal(data) } }.getOrNull()
    }

    private fun loadOrCreate(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVIDER)
            .apply { init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build()) }.generateKey()
    }

    private companion object { const val PROVIDER = "AndroidKeyStore"; const val ALIAS = "attune_sender_hmac_v1" }
}
