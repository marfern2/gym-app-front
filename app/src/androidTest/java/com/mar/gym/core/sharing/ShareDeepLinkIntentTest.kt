package com.mar.gym.core.sharing

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mar.gym.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareDeepLinkIntentTest {
    @Test fun localhostRoutineViewIntentResolvesToMainActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("http://localhost:8080/r/62b820ec-960f-4e25-8ae4-70dab3e84486"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
        assertEquals(ComponentName(context, MainActivity::class.java),
            intent.resolveActivity(context.packageManager))
    }
}
