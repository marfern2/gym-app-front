package com.mar.gym.feature.auth.data

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
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AtomicSessionFileStorage(context: Context) : SessionStorage {
    private val directory = File(context.filesDir, SESSION_DIRECTORY)
    private val atomicFile = AtomicFile(File(directory, SESSION_FILE_NAME))

    override fun read(): ByteArray? = if (atomicFile.baseFile.isFile) {
        atomicFile.readFully()
    } else {
        null
    }

    override fun writeAtomically(bytes: ByteArray) {
        check(directory.exists() || directory.mkdirs())
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw exception
        }
    }

    override fun delete() {
        atomicFile.delete()
        if (directory.isDirectory && directory.list().isNullOrEmpty()) {
            directory.delete()
        }
    }

    companion object {
        const val SESSION_DIRECTORY = "secure_session"
        const val SESSION_FILE_NAME = "local_session.enc"
    }
}

class AndroidKeystoreSessionCipher(
    private val keyAlias: String = KEY_ALIAS,
) : SessionCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        require(iv.size == GCM_IV_BYTES)
        cipher.updateAAD(AUTHENTICATED_HEADER)
        val ciphertext = cipher.doFinal(plaintext)

        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(AUTHENTICATED_HEADER)
                output.writeInt(iv.size)
                output.write(iv)
                output.writeInt(ciphertext.size)
                output.write(ciphertext)
            }
            bytes.toByteArray()
        }
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        val envelope = DataInputStream(ByteArrayInputStream(encrypted)).use { input ->
            val header = ByteArray(AUTHENTICATED_HEADER.size)
            input.readFully(header)
            require(header.contentEquals(AUTHENTICATED_HEADER))
            val ivSize = input.readInt()
            require(ivSize == GCM_IV_BYTES)
            val iv = ByteArray(ivSize).also(input::readFully)
            val ciphertextSize = input.readInt()
            require(ciphertextSize in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES)
            val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
            require(input.available() == 0)
            Envelope(iv, ciphertext)
        }

        val key = loadExistingKey() ?: throw SessionKeyUnavailableException()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        return try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_BITS, envelope.iv),
            )
            cipher.updateAAD(AUTHENTICATED_HEADER)
            cipher.doFinal(envelope.ciphertext)
        } catch (exception: AEADBadTagException) {
            throw SessionDecryptionException(exception)
        }
    }

    override fun deleteKey() {
        keyStore().deleteEntry(keyAlias)
    }

    private fun getOrCreateKey(): SecretKey = loadExistingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }
        .generateKey()

    private fun loadExistingKey(): SecretKey? = keyStore().getKey(keyAlias, null) as? SecretKey

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private data class Envelope(val iv: ByteArray, val ciphertext: ByteArray)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.mar.gym.local_session.aes_gcm.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_BITS = 256
        const val GCM_TAG_BITS = 128
        const val GCM_IV_BYTES = 12
        const val MIN_CIPHERTEXT_BYTES = 16
        const val MAX_CIPHERTEXT_BYTES = 64 * 1024
        val AUTHENTICATED_HEADER = byteArrayOf(0x47, 0x59, 0x4D, 0x45, 0x01)
    }
}

class SessionKeyUnavailableException : Exception()

class SessionDecryptionException(cause: Throwable) : Exception(cause)
