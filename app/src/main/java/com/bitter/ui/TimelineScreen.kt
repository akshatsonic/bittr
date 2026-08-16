package com.bitter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitter.log.LogEntry
import com.bitter.log.LogStore
import com.bitter.mesh.PeerInfo
import com.bitter.model.Event
import com.bitter.ble.NicknamePacket
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder

@Composable
fun TimelineScreen(viewModel: TimelineViewModel) {
    val items by viewModel.items.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val meshState by viewModel.meshState.collectAsState()
    val ownNickname by viewModel.ownNickname.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()
    var draft by remember { mutableStateOf("") }
    var panelExpanded by remember { mutableStateOf(false) }
    var fingerprintTarget by remember { mutableStateOf<FingerprintTarget?>(null) }
    var likesTarget by remember { mutableStateOf<LikesTarget?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                ) {
                    focusManager.clearFocus()
                    keyboard?.hide()
                },
        ) {
            Text(
                text = "Bittr",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "you are @${viewModel.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Timeline") },
                )
                if (com.bitter.BuildConfig.DEBUG) {
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Logs") },
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (selectedTab == 0) {
                TimelineTab(
                    items = items,
                    hasMore = hasMore,
                    onLoadMore = viewModel::loadMore,
                    listState = listState,
                    meshState = meshState,
                    ownNickname = ownNickname,
                    panelExpanded = panelExpanded,
                    onTogglePanel = { panelExpanded = !panelExpanded },
                    onNicknameChange = {
                        viewModel.setNickname(it)
                        panelExpanded = false
                    },
                    draft = draft,
                    onDraftChange = { newValue ->
                        if (newValue.length <= Event.MAX_CONTENT_LENGTH) {
                            draft = newValue
                        }
                    },
                    onPost = {
                        val content = draft.trim()
                        if (content.isNotEmpty()) {
                            viewModel.post(content)
                            draft = ""
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    },
                    onLike = { item ->
                        if (item.likedByMe) {
                            viewModel.unlike(item.post.id)
                        } else {
                            viewModel.like(item.post.id)
                        }
                    },
                    onShowLikers = { item ->
                        likesTarget = LikesTarget(
                            postId = item.post.id,
                            likers = item.displayLikes.map { it.author },
                            usernames = item.likes.map { it.author },
                        )
                    },
                    onFingerprint = { username ->
                        fingerprintTarget = FingerprintTarget(
                            displayName = viewModel.displayNames.value[username] ?: username,
                            username = username,
                            deviceId = viewModel.fingerprintFor(username),
                        )
                    },
                )
            } else {
                LogsTab(
                    entries = logEntries,
                    onClear = viewModel::clearLogs,
                )
            }
        }
    }

    fingerprintTarget?.let { target ->
        FingerprintDialog(
            target = target,
            onDismiss = { fingerprintTarget = null },
        )
    }

    likesTarget?.let { target ->
        LikersDialog(
            target = target,
            onFingerprint = { username ->
                fingerprintTarget = FingerprintTarget(
                    displayName = viewModel.displayNames.value[username] ?: username,
                    username = username,
                    deviceId = viewModel.fingerprintFor(username),
                )
            },
            onDismiss = { likesTarget = null },
        )
    }
}

@Composable
private fun TimelineTab(
    items: List<TimelineItem>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    meshState: com.bitter.mesh.MeshStatus,
    ownNickname: String,
    panelExpanded: Boolean,
    onTogglePanel: () -> Unit,
    onNicknameChange: (String) -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
    onLike: (TimelineItem) -> Unit,
    onShowLikers: (TimelineItem) -> Unit,
    onFingerprint: (String) -> Unit,
) {
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && lastVisible >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
    Column {
        MeshStatusPanel(
            meshState = meshState,
            ownNickname = ownNickname,
            expanded = panelExpanded,
            onToggle = onTogglePanel,
            onNicknameChange = onNicknameChange,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.post.id }) { item ->
                PostCard(
                    item = item,
                    onLike = { onLike(item) },
                    onShowLikers = { onShowLikers(item) },
                    onFingerprint = onFingerprint,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("What's happening?") },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onPost,
                enabled = draft.isNotBlank(),
            ) {
                Text("Post")
            }
        }
        val remaining = Event.MAX_CONTENT_LENGTH - draft.length
        Text(
            text = "$remaining",
            style = MaterialTheme.typography.labelSmall,
            color = charCountColor(remaining),
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 2.dp),
        )
    }
}

private fun charCountColor(remaining: Int, max: Int = Event.MAX_CONTENT_LENGTH): Color {    val ratio = remaining.toFloat() / max
    return when {
        remaining <= 0 -> Color(0xFFD32F2F)
        remaining < 10 -> Color(0xFFD32F2F)
        ratio > 0.5f -> lerp(Color(0xFFF9A825), Color(0xFF2E7D32), (ratio - 0.5f) * 2f)
        else -> lerp(Color(0xFFD32F2F), Color(0xFFF9A825), ratio * 2f)
    }
}

@Composable
private fun LogsTab(entries: List<LogEntry>, onClear: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${entries.size} log line${if (entries.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(entries.asReversed(), key = { entry ->
                "${entry.timestamp}-${entry.priority}-${entry.tag}-${entry.message.hashCode()}"
            }) { entry ->
                LogRow(entry)
            }
        }
    }
}

private fun priorityChar(priority: Int): Char = when (priority) {
    LogStore.VERBOSE -> 'V'
    LogStore.DEBUG -> 'D'
    LogStore.INFO -> 'I'
    LogStore.WARN -> 'W'
    LogStore.ERROR -> 'E'
    else -> '?'
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.priority) {
        LogStore.ERROR -> MaterialTheme.colorScheme.error
        LogStore.WARN -> Color(0xFFB26A00)
        LogStore.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "[${priorityChar(entry.priority)}] ${entry.tag} @ ${entry.timestamp}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontFamily = FontFamily.Monospace,
        )
    }
}

data class LikesTarget(
    val postId: String,
    val likers: List<String>,
    val usernames: List<String>,
)

@Composable
private fun LikersDialog(
    target: LikesTarget,
    onFingerprint: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Liked by ${target.likers.size}") },
        text = {
            if (target.likers.isEmpty()) {
                Text("No likes yet")
            } else {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(target.likers.size) { index ->
                        Text(
                            text = "@${target.likers[index]}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFingerprint(target.usernames[index]) }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

data class FingerprintTarget(
    val displayName: String,
    val username: String,
    val deviceId: Int?,
)

@Composable
private fun FingerprintDialog(target: FingerprintTarget, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.displayName) },
        text = {
            Text(
                text = buildString {
                    append("username: ${target.username}\n")
                    append("device fingerprint: ")
                    append(target.deviceId?.let { "%08x".format(it) } ?: "unknown")
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun MeshStatusPanel(
    meshState: com.bitter.mesh.MeshStatus,
    ownNickname: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNicknameChange: (String) -> Unit,
) {
    var nicknameDraft by remember(ownNickname) { mutableStateOf(ownNickname) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dot = if (meshState.advertising) "\u25CF" else "\u25CB"
                Text(
                    text = "$dot ${summaryLine(meshState)}  (${meshState.peers.size} peer${if (meshState.peers.size == 1) "" else "s"})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (expanded) "hide \u25B2" else "show \u25BC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = if (meshState.advertising) "Status: advertising" else "Status: not advertising",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    meshState.activeSync?.let { sync ->
                        Text(
                            text = "Status: ${
                                when (sync.direction) {
                                    "initiating" -> "initiating request to device %08x".format(sync.deviceId)
                                    "serving" -> "serving request from device %08x".format(sync.deviceId)
                                    else -> "syncing with device %08x".format(sync.deviceId)
                                }
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = nicknameDraft,
                            onValueChange = { newValue ->
                                if (NicknamePacket.byteLength(newValue) <= NicknamePacket.MAX_NICKNAME_BYTES) {
                                    nicknameDraft = newValue
                                }
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Your nickname") },
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { onNicknameChange(nicknameDraft.trim()) }) {
                            Text("Save")
                        }
                    }
                    val nickRemaining = NicknamePacket.MAX_NICKNAME_BYTES - NicknamePacket.byteLength(nicknameDraft)
                    Text(
                        text = "$nickRemaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = charCountColor(nickRemaining, NicknamePacket.MAX_NICKNAME_BYTES),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                    )
                    Text(
                        text = "Nearby peers:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (meshState.peers.isEmpty()) {
                        Text(
                            text = "none in range",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        meshState.peers.forEach { peer ->
                            PeerRow(peer)
                        }
                    }
                }
            }
        }
    }
}

private fun summaryLine(meshState: com.bitter.mesh.MeshStatus): String = when {
    meshState.activeSync != null && meshState.activeSync.direction == "initiating" ->
        "initiating sync"
    meshState.activeSync != null -> "syncing"
    meshState.advertising -> "advertising"
    else -> "idle"
}

@Composable
private fun PeerRow(peer: PeerInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = peer.nickname ?: "%08x".format(peer.deviceId),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "%08x".format(peer.deviceId),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (peer.rootMatches) "in-sync" else "differs",
            style = MaterialTheme.typography.labelSmall,
            color = if (peer.rootMatches) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${peer.rssi} dBm",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PostCard(
    item: TimelineItem,
    onLike: () -> Unit,
    onShowLikers: () -> Unit,
    onFingerprint: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "@${item.displayAuthor}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onFingerprint(item.post.author) },
            )
            Text(
                text = item.post.content,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = TimeFormatter.format(item.post.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike) {
                    Icon(
                        imageVector = if (item.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (item.likedByMe) "Unlike" else "Like",
                        tint = if (item.likedByMe) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = "${item.likes.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onShowLikers)
                        .padding(4.dp),
                )
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 5
