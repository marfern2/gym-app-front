package com.mar.gym.feature.exercises.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.mar.gym.feature.exercises.model.HttpsUrl

fun Context.openHttpsUrl(url: HttpsUrl): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, url.value.toUri()).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    if (intent.resolveActivity(packageManager) == null) return false
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
