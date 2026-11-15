package com.example.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Real End-to-End Encryption (E2EE) manager using:
 * - ECDH on NIST P-256 (secp256r1) for ephemeral/device key agreement
 * - HKDF-SHA256 for symmetric session key derivation
 * - AES-256-GCM (12-byte IV, 128-bit auth tag) for authenticated message encryption
 * - Android KeyStore AES key wrapping for local private key protection at rest
 */
class E2eeManager {

    companion object {
        private const val KEYSTORE_ALIAS = "my_chatting_pro_ec_wrap_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENVELOPE_VERSION = 1
    }

    data class IdentityKeyPair(
        val publicKeyBase64: String,
        val wrappedPrivateKeyBase64: String,
        val fingerprint: String
    )

    fun generateOrLoadIdentity(
        existingPublicB64: String?,
        existingWrappedPrivB64: String?,
        onSave: (IdentityKeyPair) -> Unit
    ): IdentityKeyPair {
        if (!existingPublicB64.isNullOrBlank() && !existingWrappedPrivB64.isNullOrBlank()) {
            val pubBytes = Base64.decode(existingPublicB64, Base64.NO_WRAP)
            return IdentityKeyPair(
                publicKeyBase64 = existingPublicB64,
                wrappedPrivateKeyBase64 = existingWrappedPrivB64,
                fingerprint = computeFingerprint(pubBytes)
            )
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val pair = kpg.generateKeyPair()
        val pubBytes = pair.public.encoded
        val privBytes = pair.private.encoded
        val wrapped = wrapWithKeyStore(privBytes)

        val identity = IdentityKeyPair(
            publicKeyBase64 = Base64.encodeToString(pubBytes, Base64.NO_WRAP),
            wrappedPrivateKeyBase64 = wrapped,
            fingerprint = computeFingerprint(pubBytes)
        )
        onSave(identity)
        return identity
    }

    fun encryptText(
        plaintext: String,
        myWrappedPrivateKeyB64: String,
        peerPublicKeyB64: String,
        conversationId: String
    ): String {
        val myPrivateKey = unwrapPrivateKey(myWrappedPrivateKeyB64)
        val peerPublicKey = decodePublicKey(peerPublicKeyB64)
        val aesKey = deriveSharedAesKey(myPrivateKey, peerPublicKey, conversationId)

        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return JSONObject().apply {
            put("v", ENVELOPE_VERSION)
            put("alg", "ECDH-P256-AES256GCM")
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ct", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }.toString()
    }

    fun decryptText(
        envelopeJson: String,
        myWrappedPrivateKeyB64: String,
        peerPublicKeyB64: String,
        conversationId: String
    ): String? {
        return try {
            val json = JSONObject(envelopeJson)
            if (json.optInt("v", 0) != ENVELOPE_VERSION) return null
            val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
            val ct = Base64.decode(json.getString("ct"), Base64.NO_WRAP)

            val myPrivateKey = unwrapPrivateKey(myWrappedPrivateKeyB64)
            val peerPublicKey = decodePublicKey(peerPublicKeyB64)
            val aesKey = deriveSharedAesKey(myPrivateKey, peerPublicKey, conversationId)

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun computeFingerprint(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
        return digest.take(12).joinToString(" ") { "%02X".format(it) }
    }

    private fun deriveSharedAesKey(
        privateKey: PrivateKey,
        peerPublicKey: PublicKey,
        conversationId: String
    ): SecretKeySpec {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privateKey)
        ka.doPhase(peerPublicKey, true)
        val sharedSecret = ka.generateSecret()

        // HKDF-SHA256 Extract & Expand
        val salt = "MyChattingPro-E2EE-Salt-v1".toByteArray(Charsets.UTF_8)
        val prk = hmacSha256(salt, sharedSecret)
        val info = "conv:$conversationId".toByteArray(Charsets.UTF_8)
        val okm = hmacSha256(prk, info + byteArrayOf(0x01))
        return SecretKeySpec(okm, 0, 32, "AES")
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun decodePublicKey(b64: String): PublicKey {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val kg = javax.crypto.KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        kg.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    private fun wrapWithKeyStore(rawPrivateKeyBytes: ByteArray): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        val encrypted = cipher.doFinal(rawPrivateKeyBytes)
        val iv = cipher.iv
        return JSONObject().apply {
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("k", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }.toString()
    }

    private fun unwrapPrivateKey(wrappedJson: String): PrivateKey {
        val obj = JSONObject(wrappedJson)
        val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
        val encrypted = Base64.decode(obj.getString("k"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(128, iv))
        val raw = cipher.doFinal(encrypted)
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(raw))
    }
}
