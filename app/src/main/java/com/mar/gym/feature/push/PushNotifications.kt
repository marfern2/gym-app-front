package com.mar.gym.feature.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mar.gym.MainActivity
import com.mar.gym.R
import kotlinx.coroutines.flow.MutableSharedFlow

object PushNotifications {
    const val CHANNEL_ID = "social_notifications"
    val received = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    @Volatile var notificationsVisible: Boolean = false
    @Volatile var appForeground: Boolean = false

    fun canShow(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Notificaciones sociales", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun clearSocial(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { it.notification.channelId == CHANNEL_ID }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    fun show(context: Context, payload: PushPayload, title: String?, body: String?) {
        if (!canShow(context) || (appForeground && notificationsVisible)) return
        createChannel(context)
        val pending = contentPendingIntent(context, payload)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title?.takeIf(String::isNotBlank) ?: "Notificación social")
            .setContentText(body?.takeIf(String::isNotBlank) ?: "Tienes una notificación nueva")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            NotificationManagerCompat.from(context).notify(payload.notificationId ?: "unknown", 0, notification)
        } catch (_: SecurityException) {
            // Permission may be revoked between the check and posting.
        }
    }

    internal fun contentPendingIntent(context: Context, payload: PushPayload): PendingIntent =
        PendingIntent.getActivity(
            context, 0, tapIntent(context, payload),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal fun tapIntent(context: Context, payload: PushPayload): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("gym-push://notification/${payload.notificationId ?: "unknown"}")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(PushIntent.OPEN_EXTRA, true)
            payload.notificationId?.let { putExtra("notificationId", it) }
            payload.type?.let { putExtra("type", it.name) }
            payload.actorUserId?.let { putExtra("actorUserId", it) }
            payload.workoutId?.let { putExtra("workoutId", it) }
        }
}
