package xyz.blockthem.mars.manager.ui.games

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.blockthem.mars.manager.api.Game
import xyz.blockthem.mars.manager.api.GameStatus
import xyz.blockthem.mars.manager.ui.common.LoadingBox
import xyz.blockthem.mars.manager.ui.common.MessageBox
import xyz.blockthem.mars.manager.ui.common.formatApiDate
import xyz.blockthem.mars.manager.ui.common.marsViewModel
import xyz.blockthem.mars.manager.ui.common.openUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(onCreateGame: () -> Unit) {
    val vm = marsViewModel { GamesViewModel(it.api) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Like the iOS app: reload whenever the screen (or the app) comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reload() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Games") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateGame,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New game") },
            )
        },
    ) { padding ->
        val sections = state.data
        PullToRefreshBox(
            isRefreshing = state.loading && sections != null,
            onRefresh = vm::reload,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                sections == null && state.loading -> LoadingBox()
                sections == null -> MessageBox(
                    state.error ?: "Something went wrong",
                    actionLabel = "Retry",
                    onAction = vm::reload,
                )
                sections.isEmpty -> MessageBox(
                    "No games yet.\nStart one with your friends!",
                    actionLabel = "Create game",
                    onAction = onCreateGame,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.error?.let { err ->
                        item {
                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    section("Your turn", sections.yourTurn) { context.openUrl(it.playUrl) }
                    section("In progress", sections.inProgress) { context.openUrl(it.playUrl) }
                    section("Finished", sections.finished) { context.openUrl(it.playUrl) }
                }
            }
        }
    }
}

private fun LazyListScope.section(title: String, games: List<Game>, onOpen: (Game) -> Unit) {
    if (games.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    items(games, key = { it.playUrl }) { game -> GameCard(game, onClick = { onOpen(game) }) }
}

@Composable
private fun GameCard(game: Game, onClick: () -> Unit) {
    val awaits = game.status == GameStatus.AWAITS_INPUT
    val finished = game.status == GameStatus.FINISHED
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (finished) 0.6f else 1f)
            .clickable(onClick = onClick),
        colors = if (awaits) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (awaits) {
                Icon(
                    Icons.Filled.NotificationImportant,
                    contentDescription = "Awaiting your input",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${game.playersCount} players",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (awaits) FontWeight.Bold else FontWeight.Normal,
                )
                val details = listOfNotNull(
                    formatApiDate(game.createdAt)?.let { "Started $it" },
                    if (!finished) formatApiDate(game.expiresAt)?.let { "expires $it" } else null,
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Play")
        }
    }
}
