package com.octagram.plus.data.discord

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores Discord OAuth credentials encrypted with a non-exportable Android Keystore key. */
class DiscordRpcTokenStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun saveTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        val encryptedAccess = encrypt(accessToken.trim())
        val encryptedRefresh = refreshToken?.trim()?.takeIf(String::isNotEmpty)?.let(::encrypt)
        preferences.edit {
            putString(KEY_ACCESS_TOKEN_ENCRYPTED, encryptedAccess)
            if (encryptedRefresh != null) putString(KEY_REFRESH_TOKEN_ENCRYPTED, encryptedRefresh)
            else remove(KEY_REFRESH_TOKEN_ENCRYPTED)
            if (expiresInSeconds > 0L) putLong(KEY_EXPIRES_AT, System.currentTimeMillis() / 1000L + expiresInSeconds)
            else remove(KEY_EXPIRES_AT)
            remove(KEY_ENCRYPTED_TOKEN)
        }
    }

    @Synchronized
    fun readAccessToken(): String? = decrypt(preferences.getString(KEY_ACCESS_TOKEN_ENCRYPTED, null))

    @Synchronized
    fun readRefreshToken(): String? = decrypt(preferences.getString(KEY_REFRESH_TOKEN_ENCRYPTED, null))

    fun expiresAtEpochSeconds(): Long = preferences.getLong(KEY_EXPIRES_AT, 0L)

    fun hasToken(): Boolean = !readAccessToken().isNullOrBlank()

    /** Remove a legacy manually pasted account token; it is not an OAuth credential. */
    @Synchronized
    fun clearLegacyToken() {
        preferences.edit { remove(KEY_ENCRYPTED_TOKEN) }
    }

    @Synchronized
    fun clear() {
        preferences.edit {
            remove(KEY_ACCESS_TOKEN_ENCRYPTED)
            remove(KEY_REFRESH_TOKEN_ENCRYPTED)
            remove(KEY_EXPIRES_AT)
            remove(KEY_ENCRYPTED_TOKEN)
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > GCM_IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, packed.copyOfRange(0, GCM_IV_BYTES)),
            )
            cipher.doFinal(packed.copyOfRange(GCM_IV_BYTES, packed.size))
                .toString(StandardCharsets.UTF_8)
                .takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    companion object {
        const val KEY_ENCRYPTED_TOKEN = "discord_rpc_token_enc" // legacy raw token; always removed
        const val KEY_ACCESS_TOKEN_ENCRYPTED = "discord_rpc_oauth_access_enc"
        const val KEY_REFRESH_TOKEN_ENCRYPTED = "discord_rpc_oauth_refresh_enc"
        const val KEY_EXPIRES_AT = "discord_rpc_oauth_expires_at"
        private const val PREFS = "AppSettings"
        private const val KEY_ALIAS = "octagram_discord_oauth_token_v2"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
