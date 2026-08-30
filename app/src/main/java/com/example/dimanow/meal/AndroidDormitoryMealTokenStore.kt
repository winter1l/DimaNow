package com.example.dimanow.meal

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidDormitoryMealTokenStore(context: Context) : DormitoryMealTokenStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = runCatching {
        val encoded = preferences.getString(TOKEN_KEY, null) ?: return null
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size > IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)))
        cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)).decodeToString()
    }.getOrElse {
        preferences.edit().remove(TOKEN_KEY).apply()
        null
    }

    override fun write(token: String) {
        require(token.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(token.encodeToByteArray())
        preferences.edit().putString(TOKEN_KEY, Base64.getEncoder().encodeToString(encrypted)).apply()
    }

    override fun clear() {
        preferences.edit().remove(TOKEN_KEY).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "dormitory_meal_upload"
        const val TOKEN_KEY = "github_token"
        const val KEY_ALIAS = "dima_now_dormitory_meal_upload"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
