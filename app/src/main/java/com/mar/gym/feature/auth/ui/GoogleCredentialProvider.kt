package com.mar.gym.feature.auth.ui

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.mar.gym.BuildConfig

fun interface GoogleCredentialProvider {
    suspend fun getCredential(activityContext: Context, nonce: String): GoogleCredentialResult
}

class CredentialManagerGoogleCredentialProvider : GoogleCredentialProvider {
    override suspend fun getCredential(
        activityContext: Context,
        nonce: String,
    ): GoogleCredentialResult {
        if (activityContext !is Activity) return GoogleCredentialResult.InternalError

        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val credential = try {
            CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
                .credential
        } catch (_: GetCredentialCancellationException) {
            return GoogleCredentialResult.Cancelled
        } catch (_: NoCredentialException) {
            return GoogleCredentialResult.NoCredential
        } catch (_: GetCredentialException) {
            return GoogleCredentialResult.InternalError
        }

        if (
            credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleCredentialResult.UnexpectedCredentialType
        }

        return try {
            GoogleCredentialResult.Success(
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            )
        } catch (_: GoogleIdTokenParsingException) {
            GoogleCredentialResult.InvalidGoogleCredential
        }
    }
}

sealed interface GoogleCredentialResult {
    class Success(val idToken: String) : GoogleCredentialResult {
        override fun toString(): String = "GoogleCredentialResult.Success[idToken=REDACTED]"
    }

    data object Cancelled : GoogleCredentialResult
    data object NoCredential : GoogleCredentialResult
    data object UnexpectedCredentialType : GoogleCredentialResult
    data object InvalidGoogleCredential : GoogleCredentialResult
    data object InternalError : GoogleCredentialResult
}
