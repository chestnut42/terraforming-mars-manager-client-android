package xyz.blockthem.mars.manager

import android.app.Application
import okhttp3.OkHttpClient
import xyz.blockthem.mars.manager.api.MarsApi
import xyz.blockthem.mars.manager.auth.AuthRepository
import java.util.concurrent.TimeUnit

class MarsApp : Application() {
    lateinit var auth: AuthRepository
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
    }
}
