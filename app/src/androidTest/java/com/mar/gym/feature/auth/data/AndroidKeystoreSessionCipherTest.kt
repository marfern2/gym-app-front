package com.mar.gym.feature.auth.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSessionCipherTest {
    @Test
    fun keystoreKeyEncryptsAndDecryptsWithFreshIv() {
        val cipher = AndroidKeystoreSessionCipher(
            keyAlias = "com.mar.gym.test.session.${UUID.randomUUID()}"
        )
        val plaintext = "instrumentation-session-payload".toByteArray()
        try {
            val first = cipher.encrypt(plaintext)
            val second = cipher.encrypt(plaintext)

            assertFalse(first.contentEquals(second))
            assertArrayEquals(plaintext, cipher.decrypt(first))
            assertArrayEquals(plaintext, cipher.decrypt(second))
        } finally {
            cipher.deleteKey()
        }
    }
}
