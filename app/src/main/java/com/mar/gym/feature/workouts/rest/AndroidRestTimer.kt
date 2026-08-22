package com.mar.gym.feature.workouts.rest

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.RemoteViews
import coil3.Image
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.size.Scale
import coil3.toBitmap
import com.mar.gym.AppContainer
import com.mar.gym.MainActivity
import com.mar.gym.R

class HandlerRestTimerScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : RestTimerScheduler {
    override fun schedule(delayMillis: Long, action: () -> Unit): ScheduledRestTimerTask {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMillis.coerceAtLeast(0L))
        return ScheduledRestTimerTask { handler.removeCallbacks(runnable) }
    }
}

class AndroidRestTimerNotifier(
    private val context: Context,
    private val imageLoader: ImageLoader = ImageLoader.Builder(context).build(),
) : RestTimerNotifier {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var activeTimer: RestTimer? = null
    private var finishedTimer: RestTimer? = null
    private var progressUpdate: Runnable? = null
    private var requestedThumbnailUrl: String? = null
    private val thumbnails = mutableMapOf<String, Bitmap>()

    init { createChannels() }

    override fun showActive(timer: RestTimer, remainingMillis: Long) {
        if (!canPostNotifications()) return
        activeTimer = timer
        finishedTimer = null
        progressUpdate?.let(handler::removeCallbacks)
        publishActive(timer, remainingMillis)
        loadThumbnail(timer)
        scheduleProgressUpdate(timer)
    }

    private fun publishActive(timer: RestTimer, remainingMillis: Long) {
        if (remainingMillis <= 0L || !canPostNotifications()) return
        val collapsed = activeRemoteViews(R.layout.notification_rest_timer_collapsed, timer, remainingMillis)
        val expanded = activeRemoteViews(R.layout.notification_rest_timer_expanded, timer, remainingMillis).apply {
            val displaySet = timer.upcomingSet ?: timer.completedSet
            setTextViewText(R.id.rest_timer_exercise_name, displaySet?.exerciseName ?: timer.exerciseName)
            setTextViewText(R.id.rest_timer_next_set, timer.activeSetText())
            setOnClickPendingIntent(
                R.id.rest_timer_action_skip,
                actionPendingIntent(RestTimerAction.Skip, REQUEST_SKIP),
            )
            setOnClickPendingIntent(
                R.id.rest_timer_action_minus,
                actionPendingIntent(RestTimerAction.MinusFifteen, REQUEST_MINUS),
            )
            setOnClickPendingIntent(
                R.id.rest_timer_action_plus,
                actionPendingIntent(RestTimerAction.PlusFifteen, REQUEST_PLUS),
            )
        }
        val displaySet = timer.upcomingSet ?: timer.completedSet
        val notification = Notification.Builder(context, ACTIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rest_timer_notification)
            .setContentTitle(displaySet?.exerciseName ?: timer.exerciseName)
            .setContentText(timer.activeSetText())
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setColor(context.getColor(R.color.brand_primary))
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openAppIntent())
            .setTimeoutAfter(remainingMillis)
            .build()
        notifications.notify(ACTIVE_NOTIFICATION_ID, notification)
    }

    override fun hideActive() {
        activeTimer = null
        requestedThumbnailUrl = null
        progressUpdate?.let(handler::removeCallbacks)
        progressUpdate = null
        notifications.cancel(ACTIVE_NOTIFICATION_ID)
    }

    override fun showFinished(timer: RestTimer) {
        if (!canPostNotifications()) return
        finishedTimer = timer
        val displaySet = timer.upcomingSet ?: timer.completedSet
        val views = RemoteViews(context.packageName, R.layout.notification_rest_timer_finished).apply {
            setTextViewText(R.id.rest_timer_exercise_name, displaySet?.exerciseName ?: timer.exerciseName)
            setTextViewText(R.id.rest_timer_finished_set, timer.finishedSetText())
            applyThumbnail(timer)
        }
        val notification = Notification.Builder(context, FINISHED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rest_timer_notification)
            .setContentTitle(displaySet?.exerciseName ?: timer.exerciseName)
            .setContentText(timer.finishedSetText())
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setColor(context.getColor(R.color.brand_primary))
            .setShowWhen(false)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openAppIntent())
            .build()
        notifications.notify(FINISHED_NOTIFICATION_ID, notification)
        loadThumbnail(timer)
    }

    fun cancelStaleActiveNotification() = notifications.cancel(ACTIVE_NOTIFICATION_ID)

    private fun activeRemoteViews(
        layoutId: Int,
        timer: RestTimer,
        remainingMillis: Long,
    ): RemoteViews = RemoteViews(context.packageName, layoutId).apply {
        val endElapsedRealtime = SystemClock.elapsedRealtime() + remainingMillis
        setChronometer(
            R.id.rest_timer_chronometer,
            endElapsedRealtime,
            context.getString(R.string.rest_timer_notification_chronometer_format),
            true,
        )
        setChronometerCountDown(R.id.rest_timer_chronometer, true)
        val maximum = (timer.configuredDurationSeconds * 1_000).coerceAtLeast(1)
        setProgressBar(
            R.id.rest_timer_progress,
            maximum,
            remainingMillis.coerceAtMost(maximum.toLong()).toInt(),
            false,
        )
        applyThumbnail(timer)
    }

    private fun RemoteViews.applyThumbnail(timer: RestTimer) {
        val url = (timer.upcomingSet ?: timer.completedSet)?.thumbnailUrl ?: return
        val bitmap = thumbnails[url] ?: return
        setViewPadding(R.id.rest_timer_thumbnail, 0, 0, 0, 0)
        setImageViewBitmap(R.id.rest_timer_thumbnail, bitmap)
    }

    private fun actionPendingIntent(action: RestTimerAction, requestCode: Int): PendingIntent {
        val intent = Intent(context, RestTimerActionReceiver::class.java)
            .setAction(action.intentAction)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleProgressUpdate(timer: RestTimer) {
        val update = object : Runnable {
            override fun run() {
                if (activeTimer != timer) return
                val remaining = timer.deadline.toEpochMilli() - System.currentTimeMillis()
                if (remaining <= 0L) return
                publishActive(timer, remaining)
                handler.postDelayed(this, PROGRESS_UPDATE_MILLIS)
            }
        }
        progressUpdate = update
        handler.postDelayed(update, PROGRESS_UPDATE_MILLIS)
    }

    private fun loadThumbnail(timer: RestTimer) {
        val url = (timer.upcomingSet ?: timer.completedSet)?.thumbnailUrl ?: return
        if (url == requestedThumbnailUrl || thumbnails.containsKey(url)) return
        requestedThumbnailUrl = url
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(THUMBNAIL_PIXELS)
            .scale(Scale.FILL)
            .target(
                onError = { if (requestedThumbnailUrl == url) requestedThumbnailUrl = null },
                onSuccess = { image -> onThumbnailLoaded(url, image) },
            )
            .build()
        imageLoader.enqueue(request)
    }

    private fun onThumbnailLoaded(url: String, image: Image) {
        thumbnails[url] = image.circularBitmap(THUMBNAIL_PIXELS)
        activeTimer?.takeIf { (it.upcomingSet ?: it.completedSet)?.thumbnailUrl == url }?.let { timer ->
            val remaining = timer.deadline.toEpochMilli() - System.currentTimeMillis()
            publishActive(timer, remaining)
        }
        finishedTimer?.takeIf { (it.upcomingSet ?: it.completedSet)?.thumbnailUrl == url }?.let(::showFinished)
    }

    private fun RestTimer.activeSetText(): String {
        upcomingSet?.let { set ->
            return if (set.metricSummary == null) {
                context.getString(
                    R.string.rest_timer_notification_next_set_no_metrics,
                    set.setNumber,
                    set.totalSets,
                )
            } else {
                context.getString(
                    R.string.rest_timer_notification_next_set,
                    set.setNumber,
                    set.totalSets,
                    set.metricSummary,
                )
            }
        }
        completedSet?.let { set ->
            return if (set.metricSummary == null) {
                context.getString(
                    R.string.rest_timer_notification_completed_set_no_metrics,
                    set.setNumber,
                    set.totalSets,
                )
            } else {
                context.getString(
                    R.string.rest_timer_notification_completed_set,
                    set.setNumber,
                    set.totalSets,
                    set.metricSummary,
                )
            }
        }
        return exerciseName
    }

    private fun RestTimer.finishedSetText(): String {
        val set = upcomingSet ?: completedSet ?: return context.getString(R.string.rest_timer_finished_title)
        return context.getString(
            R.string.rest_timer_notification_finished_set_no_metrics,
            set.setNumber,
            set.totalSets,
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannels() {
        val active = NotificationChannel(
            ACTIVE_CHANNEL_ID,
            context.getString(R.string.rest_timer_active_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.rest_timer_active_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        val finished = NotificationChannel(
            FINISHED_CHANNEL_ID,
            context.getString(R.string.rest_timer_finished_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.rest_timer_finished_channel_description)
            enableVibration(true)
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
            )
        }
        notifications.createNotificationChannels(listOf(active, finished))
    }

    private val RestTimerAction.intentAction: String
        get() = "$ACTION_PREFIX$name"

    companion object {
        private const val ACTIVE_CHANNEL_ID = "rest_timer_active_v1"
        private const val FINISHED_CHANNEL_ID = "rest_timer_finished_v1"
        private const val ACTIVE_NOTIFICATION_ID = 4101
        private const val FINISHED_NOTIFICATION_ID = 4102
        private const val REQUEST_OPEN = 4200
        private const val REQUEST_MINUS = 4201
        private const val REQUEST_PLUS = 4202
        private const val REQUEST_SKIP = 4203
        private const val ACTION_PREFIX = "com.mar.gym.rest_timer."
        private const val PROGRESS_UPDATE_MILLIS = 1_000L
        private const val THUMBNAIL_PIXELS = 144

        fun actionFrom(intent: Intent): RestTimerAction? = when (intent.action) {
            "$ACTION_PREFIX${RestTimerAction.MinusFifteen.name}" -> RestTimerAction.MinusFifteen
            "$ACTION_PREFIX${RestTimerAction.PlusFifteen.name}" -> RestTimerAction.PlusFifteen
            "$ACTION_PREFIX${RestTimerAction.Skip.name}" -> RestTimerAction.Skip
            else -> null
        }
    }
}

private fun Image.circularBitmap(size: Int): Bitmap {
    val source = toBitmap(size, size)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    Canvas(output).drawCircle(
        size / 2f,
        size / 2f,
        size / 2f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader },
    )
    return output
}

class RestTimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppContainer.initialize(context)
        val action = AndroidRestTimerNotifier.actionFrom(intent) ?: return
        if (!AppContainer.handleRestTimerAction(action)) {
            AndroidRestTimerNotifier(context.applicationContext).cancelStaleActiveNotification()
        }
    }
}
