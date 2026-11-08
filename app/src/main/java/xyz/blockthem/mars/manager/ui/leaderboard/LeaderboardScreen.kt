package xyz.blockthem.mars.manager.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.blockthem.mars.manager.auth.AuthState
import xyz.blockthem.mars.manager.ui.common.ColorDot
import xyz.blockthem.mars.manager.ui.common.LoadingBox
import xyz.blockthem.mars.manager.ui.common.currentAuth
import xyz.blockthem.mars.manager.ui.common.MessageBox
import xyz.blockthem.mars.manager.ui.common.marsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen() {
    val vm = marsViewModel { LeaderboardViewModel(it.auth.api) }
    val authState by currentAuth().state.collectAsStateWithLifecycle()
    val myId = (authState as? AuthState.SignedIn)?.user?.id
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reload() }

    Scaffold(topBar = { TopAppBar(title = { Text("Leaderboard") }) }) { padding ->
        val users = state.data
        PullToRefreshBox(
            isRefreshing = state.loading && users != null,
            onRefresh = vm::reload,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                users == null && state.loading -> LoadingBox()
                users == null -> MessageBox(state.error ?: "Something went wrong", actionLabel = "Retry", onAction = vm::reload)
                users.isEmpty() -> MessageBox("Nobody is ranked yet.\nFinish a game to get on the board!")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(users, key = { _, u -> u.id }) { index, user ->
                        val isMe = user.id == myId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                modifier = Modifier.width(32.dp),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ColorDot(user.color)
                            Text(
                                user.nickname + if (isMe) " (you)" else "",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${user.elo}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
