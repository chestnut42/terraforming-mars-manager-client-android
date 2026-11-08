package xyz.blockthem.mars.manager

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import xyz.blockthem.mars.manager.api.MarsApi
import xyz.blockthem.mars.manager.auth.AuthRepository
import xyz.blockthem.mars.manager.push.PushManager
import java.util.concurrent.TimeUnit

class MarsApp : Application() {
    /** Lives as long as the process; for app-wide background work. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var auth: AuthRepository
        private set

    lateinit var push: PushManager
        private set

    override fun onCreate() {
        super.onCreate()
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        auth = AuthRepository(
            context = this,
            serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID,
            apiFactory = { tokens -> MarsApi(BuildConfig.API_BASE_URL, tokens, http) },
        )
        push = PushManager(this, auth, appScope).also { it.start() }
    }
}
