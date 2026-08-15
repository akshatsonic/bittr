package com.bitter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitter.mesh.PeerInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder

@Composable
fun TimelineScreen(viewModel: TimelineViewModel) {
    val items by viewModel.items.collectAsState()
    val meshState by viewModel.meshState.collectAsState()
    val ownNickname by viewModel.ownNickname.collectAsState()
    var draft by remember { mutableStateOf("") }
    var panelExpanded by remember { mutableStateOf(false) }
    var fingerprintTarget by remember { mutableStateOf<FingerprintTarget?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp)) {
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
            MeshStatusPanel(
                meshState = meshState,
                ownNickname = ownNickname,
                expanded = panelExpanded,
                onToggle = { panelExpanded = !panelExpanded },
                onNicknameChange = viewModel::setNickname,
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.post.id }) { item ->
                    PostCard(
                        item = item,
                        onLike = {
                            if (item.likedByMe) {
                                viewModel.unlike(item.post.id)
                            } else {
                                viewModel.like(item.post.id)
                            }
                        },
                        onFingerprint = { username ->
                            fingerprintTarget = FingerprintTarget(
                                displayName = viewModel.displayNames.value[username] ?: username,
                                username = username,
                                deviceId = viewModel.fingerprintFor(username),
                            )
                        },
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
                    onValueChange = { draft = it.take(280) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("What's happening?") },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val content = draft.trim()
                        if (content.isNotEmpty()) {
                            viewModel.post(content)
                            draft = ""
                        }
                    },
                ) {
                    Text("Post")
                }
            }
        }
    }

    fingerprintTarget?.let { target ->
        FingerprintDialog(
            target = target,
            onDismiss = { fingerprintTarget = null },
        )
    }
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
                            value = ownNickname,
                            onValueChange = { onNicknameChange(it) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Your nickname") },
                            singleLine = true,
                        )
                    }
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
                    text = if (item.likes.isNotEmpty()) "${item.likes.size}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (item.displayLikes.isNotEmpty()) {
                Text(
                    text = "Liked by ${item.displayLikes.joinToString(", ") { "@${it.author}" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
