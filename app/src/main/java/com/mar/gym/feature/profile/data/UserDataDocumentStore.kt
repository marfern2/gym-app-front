package com.mar.gym.feature.profile.data

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface UserDataDocumentStore {
    suspend fun read(documentUri: String): String
    suspend fun write(documentUri: String, json: String)
}

class SafUserDataDocumentStore(private val resolver: ContentResolver) : UserDataDocumentStore {
    override suspend fun read(documentUri: String): String = withContext(Dispatchers.IO) {
        val uri = contentUri(documentUri)
        val stream = resolver.openInputStream(uri) ?: throw IOException("Document unavailable")
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > MAX_IMPORT_BYTES) throw UserDataFileTooLarge()
                output.write(buffer, 0, read)
            }
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray())).toString()
        }
    }

    override suspend fun write(documentUri: String, json: String) = withContext(Dispatchers.IO) {
        val uri = contentUri(documentUri)
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Document unavailable")
        stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    private fun contentUri(value: String): Uri = Uri.parse(value).takeIf { it.scheme == "content" }
        ?: throw IOException("Invalid document URI")
}

class UserDataFileTooLarge : IOException()
const val MAX_IMPORT_BYTES = 2_000_000
