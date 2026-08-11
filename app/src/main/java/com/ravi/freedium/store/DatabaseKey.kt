package com.ravi.freedium.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ravi.freedium.utils.log.FreediumLog
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Supplies the SQLCipher passphrase for the notifications database.
 *
 * The database holds other people's words - notification titles, article text, full
 * flattened Intents - captured from another app. On an unlocked or rooted device a plain
 * Room file is readable, so it is encrypted at rest.
 *
 * The passphrase is 32 random bytes generated once per install. It is never stored in the
 * clear: it is sealed with an AES-GCM key that lives in the Android Keystore, which is
 * hardware-backed where the device supports it and cannot be exported by anyone, including
 * this app. Only the sealed blob and its IV go into SharedPreferences, which are useless
 * without the Keystore key.
 */
object DatabaseKey {

    private const val TAG = "DatabaseKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "freedium_db_passphrase_key"
    private const val PREFS_NAME = "freedium_secure"
    private const val PREF_SEALED = "db_passphrase_sealed"
    private const val PREF_IV = "db_passphrase_iv"

    private const val PASSPHRASE_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * Returns the passphrase, creating and sealing one on first run.
     *
     * If the sealed blob cannot be opened - the Keystore key was invalidated by a factory
     * reset or a lockscreen change, say - a fresh passphrase is minted. The old database is
     * then unreadable by construction, which for captured notifications is the right
     * outcome: they are disposable, and silently failing open would be worse.
     */
    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val sealed = prefs.getString(PREF_SEALED, null)
        val iv = prefs.getString(PREF_IV, null)

        if (sealed != null && iv != null) {
            runCatching { unseal(sealed, iv) }
                .onSuccess { return it }
                .onFailure {
                    FreediumLog.e(TAG, "Stored passphrase could not be unsealed; regenerating")
                }
        }

        return createAndStore(prefs)
    }

    private fun createAndStore(prefs: android.content.SharedPreferences): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val sealed = cipher.doFinal(passphrase)

        prefs.edit()
            .putString(PREF_SEALED, Base64.encodeToString(sealed, Base64.NO_WRAP))
            .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()

        return passphrase
    }

    private fun unseal(sealedBase64: String, ivBase64: String): ByteArray {
        val sealed = Base64.decode(sealedBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(sealed)
    }

    /** The Keystore-resident AES key, created on first use. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Deliberately not setUserAuthenticationRequired: the notification listener
                // writes to this database while the screen is off, so requiring an unlocked
                // device would drop captures.
                .build()
        )
        return generator.generateKey()
    }
}
