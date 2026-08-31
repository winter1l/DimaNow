package com.example.dimanow.lms

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AndroidLmsCredentialStore(context: Context) : LmsCredentialStore {
    private val applicationContext = context.applicationContext
    private val atomicFile = AtomicFile(File(applicationContext.noBackupFilesDir, FILE_NAME))
    private val mutableState = MutableStateFlow(
        if (atomicFile.baseFile.exists()) CredentialState.SAVED else CredentialState.EMPTY,
    )
    override val state: StateFlow<CredentialState> = mutableState.asStateFlow()

    override suspend fun save(credentials: SavedLmsCredentials) = withContext(Dispatchers.IO) {
        require(credentials.username.isNotBlank())
        require(credentials.password.isNotEmpty())
        val plain = encode(credentials)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv.also { check(it.size == IV_BYTES) }
            cipher.updateAAD(aad())
            val encrypted = cipher.doFinal(plain)
            val output = ByteArrayOutputStream()
            DataOutputStream(output).use {
                it.writeInt(MAGIC)
                it.writeByte(FORMAT_VERSION)
                it.writeByte(iv.size)
                it.write(iv)
                it.writeInt(encrypted.size)
                it.write(encrypted)
            }
            val stream = atomicFile.startWrite()
            try {
                stream.write(output.toByteArray())
                atomicFile.finishWrite(stream)
            } catch (error: Throwable) {
                atomicFile.failWrite(stream)
                throw error
            }
            mutableState.value = CredentialState.SAVED
        } finally {
            plain.fill(0)
        }
    }

    override suspend fun load(): SavedLmsCredentials? = withContext(Dispatchers.IO) {
        if (!atomicFile.baseFile.exists()) return@withContext null
        try {
            val input = DataInputStream(ByteArrayInputStream(atomicFile.readFully()))
            check(input.readInt() == MAGIC)
            check(input.readUnsignedByte() == FORMAT_VERSION)
            val ivSize = input.readUnsignedByte()
            check(ivSize == IV_BYTES)
            val iv = ByteArray(ivSize).also(input::readFully)
            val encryptedSize = input.readInt()
            check(encryptedSize in 1..MAX_BLOB_BYTES)
            val encrypted = ByteArray(encryptedSize).also(input::readFully)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: throw IllegalStateException("Missing LMS key"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad())
            val plain = cipher.doFinal(encrypted)
            try {
                decode(plain).also { mutableState.value = CredentialState.SAVED }
            } finally {
                plain.fill(0)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            atomicFile.delete()
            mutableState.value = CredentialState.INVALIDATED
            null
        }
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        atomicFile.delete()
        mutableState.value = CredentialState.EMPTY
    }

    private fun getOrCreateKey(): SecretKey = existingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }
        .generateKey()

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun aad(): ByteArray = "${applicationContext.packageName}:lms-credentials:$FORMAT_VERSION".toByteArray()

    private fun encode(credentials: SavedLmsCredentials): ByteArray = ByteArrayOutputStream().let { output ->
        DataOutputStream(output).use {
            it.writeUTF(credentials.username)
            it.writeUTF(credentials.password)
            it.writeBoolean(credentials.automaticLogin)
        }
        output.toByteArray()
    }

    private fun decode(bytes: ByteArray): SavedLmsCredentials = DataInputStream(ByteArrayInputStream(bytes)).use {
        SavedLmsCredentials(it.readUTF(), it.readUTF(), it.readBoolean())
    }

    companion object {
        const val FILE_NAME = "lms-credentials-v1.bin"
        const val KEY_ALIAS = "dima_now_lms_credentials_v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val FORMAT_VERSION = 1
        private const val MAGIC = 0x444C4D53
        private const val MAX_BLOB_BYTES = 128 * 1024
    }
}
