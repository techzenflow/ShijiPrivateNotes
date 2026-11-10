package com.shiji.notes.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.KeyStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator

object Security {
    private const val PREF = "shiji_security"
    private const val ITERATIONS = 210_000
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun isConfigured(c: Context) = prefs(c).contains("salt") && prefs(c).contains("verifier")

    fun setupMaster(c: Context, password: CharArray) {
        require(password.size >= 8) { "主密码至少需要 8 位" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = derive(password, salt)
        val verifier = key.copyOfRange(0, 16)
        prefs(c).edit().putString("salt", enc(salt)).putString("verifier", enc(verifier)).apply()
        key.fill(0); password.fill('\u0000')
    }

    fun unlock(c: Context, password: CharArray): ByteArray? {
        val p = prefs(c)
        val salt = p.getString("salt", null)?.let(::dec) ?: return null
        val expected = p.getString("verifier", null)?.let(::dec) ?: return null
        val key = derive(password, salt)
        password.fill('\u0000')
        val ok = java.security.MessageDigest.isEqual(expected, key.copyOfRange(0, 16))
        return if (ok) key else { key.fill(0); null }
    }

    private const val BIO_ALIAS = "shiji_biometric_master_v1"

    fun biometricConfigured(c: Context): Boolean = prefs(c).contains("bio_blob")

    fun createBiometricCipher(): Cipher {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(BIO_ALIAS)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val builder = KeyGenParameterSpec.Builder(BIO_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }
            generator.init(builder.build())
            generator.generateKey()
        }
        val key = store.getKey(BIO_ALIAS, null) as java.security.Key
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    fun enableBiometric(c: Context, masterKey: ByteArray, cipher: Cipher) {
        val blob = cipher.doFinal(masterKey)
        prefs(c).edit().putString("bio_iv", enc(cipher.iv))
            .putString("bio_blob", enc(blob)).apply()
    }

    fun biometricDecryptCipher(c: Context): Cipher? {
        val iv = prefs(c).getString("bio_iv", null)?.let(::dec) ?: return null
        if (!biometricConfigured(c)) return null
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(BIO_ALIAS, null) as? java.security.Key ?: return null
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
    }

    fun finishBiometricUnlock(c: Context, cipher: Cipher): ByteArray {
        val blob = prefs(c).getString("bio_blob", null)?.let(::dec)
            ?: throw IllegalStateException("尚未启用指纹解锁")
        return cipher.doFinal(blob)
    }

    fun disableBiometric(c: Context) {
        prefs(c).edit().remove("bio_blob").remove("bio_iv").apply()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(BIO_ALIAS)) store.deleteEntry(BIO_ALIAS)
    }

    fun encryptNoteField(key: ByteArray, value: String): String =
        if (value.startsWith("SHIJI1:")) value else "SHIJI1:" + encrypt(key, value)

    fun decryptNoteField(key: ByteArray, value: String): String =
        if (value.startsWith("SHIJI1:")) decrypt(key, value.removePrefix("SHIJI1:")) else value

    fun encrypt(key: ByteArray, value: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return enc(iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }
    fun decrypt(key: ByteArray, value: String): String {
        val all = dec(value); val iv = all.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    }
    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.also { spec.clearPassword() }
    }
    private fun enc(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun dec(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
