package xyz.blockthem.mars.manager.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import xyz.blockthem.mars.manager.MainActivity
import xyz.blockthem.mars.manager.R
import xyz.blockthem.mars.manager.auth.AuthRepository
import xyz.blockthem.mars.manager.auth.AuthState

/**
 * Registers this device's FCM token with the manager server while signed in.
 *
 * The server sends a notification when the number of games awaiting your
 * input grows, on [CHANNEL_ID] with tag [TAG], so a newer one replaces the
 * previous one.
 */
class PushManager(
    private val context: Context,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    fun start() {
        createChannel()
        scope.launch {
            // Register on every sign-in and app start; tokens can rotate.
            auth.state
                .map { it is AuthState.SignedIn }
                .distinctUntilChanged()
                .collect { signedIn -> if (signedIn) register() }
        }
    }

    /** Called by [MarsMessagingService] when Firebase issues a new token. */
    fun onNewToken(token: String) {
        if (auth.state.value !is AuthState.SignedIn) return
        scope.launch { upload(token) }
    }

    /** Stops pushes for this device, then signs out. */
    suspend fun signOut() {
        // Best effort: don't block sign-out on a slow or offline server.
        withTimeoutOrNull(5_000) { upload("") }
        auth.signOut()
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
            .onFailure { Log.w(TAG, "failed to delete FCM token", it) }
    }

    fun showNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(TAG, NOTIFICATION_ID, notification)
    }

    private suspend fun register() {
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "failed to get FCM token", e)
            return
        }
        upload(token)
    }

    private suspend fun upload(token: String) {
        try {
            auth.api.updateFcmToken(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "failed to upload FCM token", e)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_turns),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_turns_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        /** Must match the server's fcmChannelId. */
        const val CHANNEL_ID = "turns"

        /** Must match the server's fcmNotificationTag. */
        const val TAG = "awaiting-games"
        private const val NOTIFICATION_ID = 1
    }
}
