package com.mar.gym.feature.auth.data

import com.mar.gym.feature.auth.model.AuthSession
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentSessionStoreTest {
    @Test
    fun missingSessionRestoresAsMissing() = runTest {
        val store = store()

        assertSame(SessionRestoreResult.Missing, store.restore())
        assertNull(store.currentSession())
    }

    @Test
    fun encryptsPersistsAndDecryptsSession() = runTest {
        val storage = FakeStorage()
        val cipher = JvmAesGcmCipher()
        val first = store(storage, cipher)

        assertSame(SessionStoreResult.Success, first.save(session()))
        val restored = store(storage, cipher).restore() as SessionRestoreResult.Restored

        assertEquals(session(), restored.session)
        assertFalse(requireNotNull(storage.bytes).toString(Charsets.UTF_8).contains(ACCESS_TOKEN))
        assertFalse(requireNotNull(storage.bytes).toString(Charsets.UTF_8).contains(REFRESH_TOKEN))
    }

    @Test
    fun equalWritesProduceDifferentCiphertextBecauseIvChanges() = runTest {
        val storage = FakeStorage()
        val store = store(storage)

        store.save(session())
        val first = requireNotNull(storage.bytes).copyOf()
        store.save(session())
        val second = requireNotNull(storage.bytes).copyOf()

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun corruptCiphertextIsDeletedAndInvalidated() = runTest {
        val storage = FakeStorage(byteArrayOf(1, 2, 3, 4))
        val cipher = JvmAesGcmCipher()
        val store = store(storage, cipher)

        assertSame(SessionRestoreResult.Invalidated, store.restore())
        assertNull(storage.bytes)
        assertTrue(cipher.keyDeleted)
        assertNull(store.currentSession())
    }

    @Test
    fun unavailableKeyDeletesRestoredCiphertext() = runTest {
        val storage = FakeStorage()
        val writerCipher = JvmAesGcmCipher()
        store(storage, writerCipher).save(session())
        val missingKeyCipher = JvmAesGcmCipher(keyAvailable = false)

        val result = store(storage, missingKeyCipher).restore()

        assertSame(SessionRestoreResult.Invalidated, result)
        assertNull(storage.bytes)
        assertTrue(missingKeyCipher.keyDeleted)
    }

    @Test
    fun clearRemovesMemoryCiphertextAndKey() = runTest {
        val storage = FakeStorage()
        val cipher = JvmAesGcmCipher()
        val store = store(storage, cipher)
        store.save(session())

        assertSame(SessionStoreResult.Success, store.clear())

        assertNull(store.currentSession())
        assertNull(storage.bytes)
        assertTrue(cipher.keyDeleted)
    }

    @Test
    fun failedAtomicWriteDoesNotPublishNewMemoryState() = runTest {
        val storage = FakeStorage()
        val store = store(storage)
        store.save(session(accessToken = "old-access"))
        storage.failWrites = true

        val result = store.save(session(accessToken = "new-access"))

        assertSame(SessionStoreResult.Failure, result)
        assertEquals("old-access", store.currentAccessToken())
    }

    @Test
    fun cacheAvoidsDiskReadsAfterRestore() = runTest {
        val storage = FakeStorage()
        val cipher = JvmAesGcmCipher()
        store(storage, cipher).save(session())
        val restored = store(storage, cipher)
        restored.restore()

        repeat(10) { assertEquals(ACCESS_TOKEN, restored.currentAccessToken()) }

        assertEquals(1, storage.readCalls)
    }

    @Test
    fun sensitiveRepresentationsAreRedacted() = runTest {
        val value = session()
        val restored = SessionRestoreResult.Restored(value)
        val store = store().apply { save(value) }

        assertFalse(value.toString().contains(ACCESS_TOKEN))
        assertFalse(value.toString().contains(REFRESH_TOKEN))
        assertFalse(restored.toString().contains(ACCESS_TOKEN))
        assertFalse(store.toString().contains(ACCESS_TOKEN))
        assertFalse(RefreshTokenRequestDto(REFRESH_TOKEN).toString().contains(REFRESH_TOKEN))
        assertFalse(
            AuthenticationResponseDto(
                "Bearer",
                ACCESS_TOKEN,
                600,
                REFRESH_TOKEN,
                86_400,
            ).toString().contains(ACCESS_TOKEN)
        )
    }

    @Test
    fun codecRoundTripIsExact() {
        val codec = BinarySessionCodec()

        assertArrayEquals(codec.encode(session()), codec.encode(codec.decode(codec.encode(session()))))
    }

    private fun store(
        storage: FakeStorage = FakeStorage(),
        cipher: JvmAesGcmCipher = JvmAesGcmCipher(),
    ) = PersistentSessionStore(storage, cipher, ioDispatcher = Dispatchers.Unconfined)

    private fun session(accessToken: String = ACCESS_TOKEN) = AuthSession(
        tokenType = "Bearer",
        accessToken = accessToken,
        refreshToken = REFRESH_TOKEN,
        accessTokenExpiresAt = Instant.parse("2026-08-01T10:10:00Z"),
        refreshTokenExpiresAt = Instant.parse("2026-08-31T10:00:00Z"),
    )

    private companion object {
        const val ACCESS_TOKEN = "sensitive-access-token"
        const val REFRESH_TOKEN = "sensitive-refresh-token"
    }
}

private class FakeStorage(initial: ByteArray? = null) : SessionStorage {
    var bytes = initial
    var failWrites = false
    var readCalls = 0

    override fun read(): ByteArray? {
        readCalls += 1
        return bytes?.copyOf()
    }

    override fun writeAtomically(bytes: ByteArray) {
        if (failWrites) error("atomic write failed")
        this.bytes = bytes.copyOf()
    }

    override fun delete() {
        bytes = null
    }
}

private class JvmAesGcmCipher(
    keyAvailable: Boolean = true,
) : SessionCipher {
    private var key: SecretKey? = if (keyAvailable) {
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    } else {
        null
    }
    var keyDeleted = false

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        cipher.init(Cipher.ENCRYPT_MODE, requireNotNull(key), GCMParameterSpec(128, iv))
        return ByteBuffer.allocate(4 + iv.size + plaintext.size + 16)
            .putInt(iv.size)
            .put(iv)
            .put(cipher.doFinal(plaintext))
            .array()
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(encrypted)
        val iv = ByteArray(buffer.int)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, requireNotNull(key), GCMParameterSpec(128, iv))
            doFinal(ciphertext)
        }
    }

    override fun deleteKey() {
        key = null
        keyDeleted = true
    }
}
