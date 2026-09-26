package app.luma.chat

import android.content.Context
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

data class LumaAccount(val id: String, val name: String, val email: String, val photo: String? = null) {
    /** What the avatar falls back to before a photo loads. */
    val initial: String get() = (name.trim().ifEmpty { email }).take(1).uppercase()
}

/**
 * Who is signed in. Kept behind an interface so the gate can be driven — and
 * tested — without Google Play services anywhere near it.
 */
interface AccountStore {
    fun current(): LumaAccount?
    fun save(account: LumaAccount)
    fun clear()
}

class LocalAccountStore(context: Context) : AccountStore {
    private val prefs = context.getSharedPreferences("luma_account", Context.MODE_PRIVATE)
    override fun current(): LumaAccount? {
        val id = prefs.getString("id", null) ?: return null
        return LumaAccount(
            id = id,
            name = prefs.getString("name", null).orEmpty(),
            email = prefs.getString("email", null).orEmpty(),
            photo = prefs.getString("photo", null)
        )
    }
    override fun save(account: LumaAccount) {
        prefs.edit().putString("id", account.id).putString("name", account.name)
            .putString("email", account.email).putString("photo", account.photo).apply()
    }
    override fun clear() { prefs.edit().clear().apply() }
}

/**
 * Basic Google sign-in: it asks Play services for the account's id, name and
 * e-mail and nothing else, which is all the gate needs and which works without
 * an OAuth client registered in Google Cloud. It is a client-side gate, not a
 * server-verified identity — swap in Credential Manager with an ID token if the
 * app ever needs to prove to a backend who the user is.
 */
@Composable
fun rememberGoogleSignIn(onResult: (Result<LumaAccount>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val client: GoogleSignInClient? = remember(context) {
        runCatching {
            GoogleSignIn.getClient(
                context,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestProfile().build()
            )
        }.getOrNull()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onResult(runCatching {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            LumaAccount(
                id = account.id ?: account.email.orEmpty(),
                name = account.displayName.orEmpty(),
                email = account.email.orEmpty(),
                photo = account.photoUrl?.toString()
            ).also { if (it.id.isEmpty()) error("empty account") }
        }.recoverCatching { throw SignInFailure(describe(it)) })
    }
    return {
        val intent = client?.let { runCatching { it.signInIntent }.getOrNull() }
        if (intent == null) onResult(Result.failure(SignInFailure(R.string.signin_no_play)))
        else runCatching { launcher.launch(intent) }
            .onFailure { onResult(Result.failure(SignInFailure(R.string.signin_no_window))) }
    }
}

class SignInFailure(@StringRes val messageRes: Int) : Exception("sign_in_failure:$messageRes")

@StringRes private fun describe(error: Throwable): Int = when {
    error is ApiException && error.statusCode == 12501 -> R.string.signin_cancelled
    error is ApiException && error.statusCode == 7 -> R.string.signin_offline
    error is ApiException && error.statusCode == 10 -> R.string.signin_not_configured
    else -> R.string.signin_failed
}

/** Signs out of Play services too, so the next tap offers the account chooser again. */
fun signOutOfGoogle(context: Context) {
    runCatching {
        GoogleSignIn.getClient(context, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build()).signOut()
    }
}
