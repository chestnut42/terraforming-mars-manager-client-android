package xyz.blockthem.mars.manager.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.blockthem.mars.manager.api.ApiException
import xyz.blockthem.mars.manager.api.MarsApi
import xyz.blockthem.mars.manager.api.TokenProvider
import xyz.blockthem.mars.manager.api.User
import java.lang.ref.WeakReference

sealed interface AuthState {
    /** Restoring a previous session on app start. */
    data object Restoring : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: User) : AuthState
}

/** Thrown for sign-in problems that should be shown to the user. */
class SignInException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Owns the Google ID token used to talk to the manager API.
 *
 * Google ID tokens live about an hour, so the token is refreshed through
 * Credential Manager's auto sign-in shortly before it expires, or when the
 * server answers 401. Credential Manager needs an Activity to show its UI,
 * which [MainActivity][xyz.blockthem.mars.manager.MainActivity] provides via [attach].
 */
class AuthRepository(
    context: Context,
    private val serverClientId: String,
    apiFactory: (TokenProvider) -> MarsApi,
) : TokenProvider {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val credentialManager = CredentialManager.create(appContext)
    private val refreshMutex = Mutex()
    private var activity = WeakReference<Activity>(null)

    val api: MarsApi = apiFactory(this)

    private val _state = MutableStateFlow<AuthState>(AuthState.Restoring)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    @Volatile
    private var idToken: String? = prefs.getString(KEY_TOKEN, null)

    val isConfigured: Boolean get() = serverClientId.isNotBlank()

    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    fun detach(activity: Activity) {
        if (this.activity.get() === activity) this.activity = WeakReference(null)
    }

    /** Called once on app start. */
    suspend fun restore() {
        if (_state.value !is AuthState.Restoring) return
        if (idToken == null || !isConfigured) {
            _state.value = AuthState.SignedOut
            return
        }
        try {
            val user = api.login() // refreshes the stored token if needed
            _state.value = AuthState.SignedIn(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException.Unauthorized) {
            signOutLocally()
        } catch (e: Exception) {
            // Network trouble etc. Keep the token, but let the user retry from the sign-in screen.
            Log.w(TAG, "session restore failed", e)
            _state.value = AuthState.SignedOut
        }
    }

    /** Interactive "Sign in with Google" flow, followed by the manager login call. */
    suspend fun signIn(activity: Activity) {
        if (!isConfigured) {
            throw SignInException(
                "Google sign-in is not configured. Set mars.googleServerClientId in local.properties.",
            )
        }
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val token = requestToken(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
            ?: return // cancelled by the user
        storeToken(token)
        try {
            _state.value = AuthState.SignedIn(api.login())
        } catch (e: Exception) {
            if (e !is CancellationException) signOutLocally()
            throw e
        }
    }

    suspend fun signOut() {
        signOutLocally()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Keeps the cached user (e.g. after a profile update) in sync. */
    fun updateUser(user: User) {
        if (_state.value is AuthState.SignedIn) _state.value = AuthState.SignedIn(user)
    }

    // --- TokenProvider ---

    override suspend fun token(): String {
        val current = idToken ?: throw ApiException.Unauthorized()
        if (Jwt.isFresh(current, nowSeconds())) return current
        return refresh(current)
    }

    override suspend fun refresh(rejected: String): String = refreshMutex.withLock {
        // Another caller may have refreshed already while we waited for the lock.
        idToken?.let { if (it != rejected && Jwt.isFresh(it, nowSeconds())) return it }

        // Without a visible activity (e.g. a background FCM callback) sign-in
        // can't be refreshed. Fail this request but stay signed in; the next
        // app start refreshes it.
        if (activity.get() == null) {
            throw ApiException.Unauthorized("Sign-in can't be refreshed in the background")
        }

        val token = silentToken()
        if (token == null) {
            signOutLocally()
            throw ApiException.Unauthorized()
        }
        storeToken(token)
        token
    }

    // --- internals ---

    private suspend fun silentToken(): String? {
        val activity = activity.get()
        if (activity == null || !isConfigured) return null
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        return try {
            requestToken(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        } catch (e: SignInException) {
            Log.w(TAG, "silent sign-in failed", e)
            null
        }
    }

    /** Returns the ID token, or null when the user cancelled. */
    private suspend fun requestToken(activity: Activity, request: GetCredentialRequest): String? {
        val response = try {
            credentialManager.getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            return null
        } catch (e: NoCredentialException) {
            throw SignInException("No Google account available on this device.", e)
        } catch (e: GetCredentialException) {
            throw SignInException("Google sign-in failed: ${e.message ?: e.type}", e)
        }
        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw SignInException("Unexpected credential type: ${credential.type}")
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw SignInException("Could not read Google ID token", e)
        }
    }

    private fun storeToken(token: String) {
        idToken = token
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    private fun signOutLocally() {
        idToken = null
        prefs.edit().remove(KEY_TOKEN).apply()
        _state.value = AuthState.SignedOut
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000

    private companion object {
        const val TAG = "AuthRepository"
        const val KEY_TOKEN = "google_id_token"
    }
}
