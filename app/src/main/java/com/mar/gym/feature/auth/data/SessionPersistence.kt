package com.mar.gym.feature.auth.data

import com.mar.gym.feature.auth.model.AuthSession
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant

interface SessionStorage {
    fun read(): ByteArray?

    fun writeAtomically(bytes: ByteArray)

    fun delete()
}

interface SessionCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(encrypted: ByteArray): ByteArray

    fun deleteKey()
}

interface SessionCodec {
    fun encode(session: AuthSession): ByteArray

    fun decode(bytes: ByteArray): AuthSession
}

class BinarySessionCodec : SessionCodec {
    override fun encode(session: AuthSession): ByteArray {
        require(session.tokenType == "Bearer")
        require(session.accessToken.isNotBlank())
        require(session.refreshToken.isNotBlank())
        require(session.accessTokenExpiresAt.epochSecond > 0)
        require(session.refreshTokenExpiresAt.epochSecond > 0)

        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(PLAINTEXT_MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeSizedString(session.tokenType)
                output.writeSizedString(session.accessToken)
                output.writeSizedString(session.refreshToken)
                output.writeLong(session.accessTokenExpiresAt.epochSecond)
                output.writeLong(session.refreshTokenExpiresAt.epochSecond)
            }
            bytes.toByteArray()
        }
    }

    override fun decode(bytes: ByteArray): AuthSession = DataInputStream(
        ByteArrayInputStream(bytes)
    ).use { input ->
        require(input.readInt() == PLAINTEXT_MAGIC)
        require(input.readInt() == FORMAT_VERSION)
        val session = AuthSession(
            tokenType = input.readSizedString(),
            accessToken = input.readSizedString(),
            refreshToken = input.readSizedString(),
            accessTokenExpiresAt = Instant.ofEpochSecond(input.readLong()),
            refreshTokenExpiresAt = Instant.ofEpochSecond(input.readLong()),
        )
        require(input.available() == 0)
        require(session.tokenType == "Bearer")
        require(session.accessToken.isNotBlank())
        require(session.refreshToken.isNotBlank())
        require(session.accessTokenExpiresAt.epochSecond > 0)
        require(session.refreshTokenExpiresAt.epochSecond > 0)
        session
    }

    private fun DataOutputStream.writeSizedString(value: String) {
        val encoded = value.toByteArray(StandardCharsets.UTF_8)
        require(encoded.size in 1..MAX_STRING_BYTES)
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readSizedString(): String {
        val size = readInt()
        require(size in 1..MAX_STRING_BYTES)
        val value = ByteArray(size)
        readFully(value)
        return String(value, StandardCharsets.UTF_8)
    }

    private companion object {
        const val PLAINTEXT_MAGIC = 0x47594D53
        const val FORMAT_VERSION = 1
        const val MAX_STRING_BYTES = 16 * 1024
    }
}
