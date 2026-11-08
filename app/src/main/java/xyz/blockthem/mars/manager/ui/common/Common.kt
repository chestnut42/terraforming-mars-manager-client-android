package xyz.blockthem.mars.manager.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import xyz.blockthem.mars.manager.MarsApp
import xyz.blockthem.mars.manager.api.PlayerColor
import xyz.blockthem.mars.manager.auth.AuthRepository

/** Creates a ViewModel that gets the app's [AuthRepository]. */
@Composable
inline fun <reified VM : ViewModel> marsViewModel(crossinline create: (AuthRepository) -> VM): VM =
    viewModel(
        factory = viewModelFactory {
            initializer { create((this[APPLICATION_KEY] as MarsApp).auth) }
        },
    )

/** The app-wide [AuthRepository]. */
@Composable
fun currentAuth(): AuthRepository =
    (LocalContext.current.applicationContext as MarsApp).auth

/** Swatch colours roughly matching the game's player colours. */
fun PlayerColor.toColor(): Color = when (this) {
    PlayerColor.BLUE -> Color(0xFF2F6FDB)
    PlayerColor.RED -> Color(0xFFD7263D)
    PlayerColor.YELLOW -> Color(0xFFF2C14E)
    PlayerColor.GREEN -> Color(0xFF3BA55C)
    PlayerColor.BLACK -> Color(0xFF2B2B2B)
    PlayerColor.PURPLE -> Color(0xFF8E44AD)
    PlayerColor.ORANGE -> Color(0xFFF28C28)
    PlayerColor.PINK -> Color(0xFFF06BA8)
    PlayerColor.BRONZE -> Color(0xFFA9713A)
}

fun PlayerColor.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun ColorDot(color: PlayerColor, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Box(
        modifier
            .size(size)
            .background(color.toColor(), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun MessageBox(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null) Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Opens a game in a Custom Tab, falling back to any browser. */
fun Context.openUrl(url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // nothing can open it
        }
    }
}
