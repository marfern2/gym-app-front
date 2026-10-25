package com.mar.gym.feature.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mar.gym.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GymFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        AppContainer.initialize(applicationContext)
        scope.launch { AppContainer.pushTokenCoordinator.newToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = PushPayload.parse(message.data) ?: return
        PushNotifications.received.tryEmit(Unit)
        PushNotifications.show(this, payload, message.notification?.title, message.notification?.body)
    }
}
