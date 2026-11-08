package xyz.blockthem.mars.manager.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import xyz.blockthem.mars.manager.MarsApp

/**
 * Receives FCM callbacks. While the app is in the background, Android shows
 * notification messages itself; this only handles foreground delivery.
 */
class MarsMessagingService : FirebaseMessagingService() {
    private val push get() = (application as MarsApp).push

    override fun onNewToken(token: String) {
        push.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        push.showNotification(n.title.orEmpty(), n.body.orEmpty())
    }
}
