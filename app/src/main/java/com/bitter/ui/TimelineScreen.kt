package com.bitter.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitter.R
import com.bitter.log.LogEntry
import com.bitter.log.LogStore
import com.bitter.mesh.MeshStatus
import com.bitter.mesh.PeerInfo
import com.bitter.model.Event
import com.bitter.model.Mention
import com.bitter.ble.NicknamePacket
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder

@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    focusEventId: String?,
    onFocusConsumed: () -> Unit,
    shouldPromptUsername: Boolean,
    onUsernamePromptDone: () -> Unit,
) {
    val items by viewModel.items.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val meshState by viewModel.meshState.collectAsState()
    val ownNickname by viewModel.ownNickname.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val displayNames by viewModel.displayNames.collectAsState()
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var fingerprintTarget by remember { mutableStateOf<FingerprintTarget?>(null) }
    var likesTarget by remember { mutableStateOf<LikesTarget?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var showUsernamePrompt by remember { mutableStateOf(shouldPromptUsername) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val ownUsername = viewModel.username
    val ownDisplayName = displayNames[ownUsername] ?: ownUsername

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerShape = RoundedCornerShape(0.dp),
                drawerContainerColor = if (isDarkTheme) BittrColors.DarkDrawer else MaterialTheme.colorScheme.surface,
            ) {
                DrawerContent(
                    meshState = meshState,
                    ownNickname = ownNickname,
                    isDarkTheme = isDarkTheme,
                    onNicknameChange = { viewModel.setNickname(it) },
                    onToggleTheme = onToggleTheme,
                    onClose = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
            ) {
                AppHeader(
                    ownDisplayName = ownDisplayName,
                    ownUsername = ownUsername,
                    meshState = meshState,
                    onMeshClick = { scope.launch { drawerState.open() } },
                )
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
                if (selectedTab == 0) {
                    TimelineTab(
                        items = items,
                        hasMore = hasMore,
                        onLoadMore = viewModel::loadMore,
                        listState = listState,
                        ownDisplayName = ownDisplayName,
                        ownUsername = ownUsername,
                        draft = draft,
                        candidates = candidates,
                        displayNames = displayNames,
                        focusEventId = focusEventId,
                        onFocusConsumed = onFocusConsumed,
                        onDraftChange = { newValue ->
                            if (newValue.text.length <= Event.MAX_CONTENT_LENGTH) {
                                draft = newValue
                            }
                        },
                        onPost = {
                            val content = draft.text.trim()
                            if (content.isNotEmpty()) {
                                viewModel.post(content)
                                draft = TextFieldValue("")
                                if (items.isNotEmpty()) {
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
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
                                displayName = displayNames[username] ?: username,
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
                    displayName = displayNames[username] ?: username,
                    username = username,
                    deviceId = viewModel.fingerprintFor(username),
                )
            },
            onDismiss = { likesTarget = null },
        )
    }

    if (showUsernamePrompt) {
        UsernamePromptDialog(
            currentUsername = ownNickname,
            onSave = { newName ->
                viewModel.setNickname(newName)
                showUsernamePrompt = false
                onUsernamePromptDone()
            },
            onDismiss = {
                showUsernamePrompt = false
                onUsernamePromptDone()
            },
        )
    }
}

@Composable
private fun AppHeader(
    ownDisplayName: String,
    ownUsername: String,
    meshState: MeshStatus,
    onMeshClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                name = ownDisplayName,
                seed = ownUsername,
                size = 36.dp,
                modifier = Modifier.clickable(onClick = onMeshClick),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_bittr_logo),
                    contentDescription = "Bittr",
                    modifier = Modifier.height(44.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                )
            }
            MeshStatusIndicator(meshState = meshState, onClick = onMeshClick)
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun MeshStatusIndicator(meshState: MeshStatus, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dot = if (meshState.advertising) "\u25CF" else "\u25CB"
        Text(
            text = dot,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${meshState.peers.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimelineTab(
    items: List<TimelineItem>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    ownDisplayName: String,
    ownUsername: String,
    draft: TextFieldValue,
    candidates: List<Mention.Candidate>,
    displayNames: Map<String, String>,
    focusEventId: String?,
    onFocusConsumed: () -> Unit,
    onDraftChange: (TextFieldValue) -> Unit,
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
    LaunchedEffect(focusEventId, items) {
        val focus = focusEventId ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.post.id == focus }
        when {
            index >= 0 -> {
                listState.animateScrollToItem(index)
                onFocusConsumed()
            }
            hasMore -> onLoadMore()
            else -> onFocusConsumed()
        }
    }
    Column {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            items(items, key = { it.post.id }) { item ->
                PostRow(
                    item = item,
                    displayNames = displayNames,
                    onLike = { onLike(item) },
                    onShowLikers = { onShowLikers(item) },
                    onFingerprint = onFingerprint,
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(name = ownDisplayName, seed = ownUsername, size = 36.dp)
            Spacer(modifier = Modifier.width(8.dp))
            MentionComposer(
                value = draft,
                candidates = candidates,
                displayNames = displayNames,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onPost,
                enabled = draft.text.isNotBlank(),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text("Post")
            }
        }
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

private class MentionTransformation(
    private val resolve: (String) -> String,
    private val chipStyle: SpanStyle,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val v = Mention.visualize(text.text, resolve)
        val styled = buildAnnotatedString {
            append(v.text)
            for (range in v.mentionRanges) {
                addStyle(chipStyle, range.first, range.last + 1)
            }
        }
        return TransformedText(
            styled,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    v.originalToTransformed[offset.coerceIn(0, v.originalToTransformed.lastIndex)]

                override fun transformedToOriginal(offset: Int): Int =
                    v.transformedToOriginal[offset.coerceIn(0, v.transformedToOriginal.lastIndex)]
            },
        )
    }
}

@Composable
private fun mentionStyle(): SpanStyle = SpanStyle(
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.SemiBold,
)

@Composable
private fun MentionComposer(
    value: TextFieldValue,
    candidates: List<Mention.Candidate>,
    displayNames: Map<String, String>,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolve: (String) -> String = { username -> displayNames[username] ?: username }
    val chipStyle = mentionStyle()
    val remaining = Event.MAX_CONTENT_LENGTH - value.text.length

    val active = remember(value.text, value.selection) {
        Mention.activeMention(value.text, value.selection.start)
    }
    val filtered = remember(active?.query, candidates) {
        Mention.filter(candidates, active?.query ?: "")
    }

    Column(modifier = modifier) {
        if (active != null) {
            MentionDropdown(
                candidates = filtered,
                onSelect = { candidate ->
                    val updated = Mention.insert(value.text, active, candidate.username)
                    val cursor = active.range.first + Mention.token(candidate.username).length
                    onValueChange(TextFieldValue(updated, TextRange(cursor)))
                },
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace) {
                        val sel = value.selection
                        if (sel.collapsed && sel.start > 0) {
                            val updated = Mention.deleteBefore(value.text, sel.start)
                            if (updated != value.text) {
                                val removed = value.text.length - updated.length
                                onValueChange(TextFieldValue(updated, TextRange(sel.start - removed)))
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    false
                },
            visualTransformation = MentionTransformation(resolve, chipStyle),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "What's happening?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp),
                    ) {
                        inner()
                    }
                    Text(
                        text = "$remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = charCountColor(remaining),
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            },
        )
    }
}

@Composable
private fun MentionDropdown(
    candidates: List<Mention.Candidate>,
    onSelect: (Mention.Candidate) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .heightIn(max = 200.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 3.dp,
    ) {
        if (candidates.isEmpty()) {
            Text(
                text = "No matches",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn {
                items(candidates, key = { it.username }) { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(candidate) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(
                            name = candidate.displayName,
                            seed = candidate.username,
                            size = 28.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "@${candidate.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (candidate.displayName != candidate.username) {
                                Text(
                                    text = candidate.username,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
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
            style = MaterialTheme.typography.bodySmall,
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
private fun UsernamePromptDialog(
    currentUsername: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set your username") },
        text = {
            Column {
                Text(
                    text = "You're currently using the default username \"$currentUsername\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { newValue ->
                        if (NicknamePacket.byteLength(newValue) <= NicknamePacket.MAX_NICKNAME_BYTES) {
                            draft = newValue
                        }
                    },
                    label = { Text("Your username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(draft.trim().ifEmpty { currentUsername }) },
                enabled = draft.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        },
    )
}

@Composable
private fun DrawerContent(
    meshState: MeshStatus,
    ownNickname: String,
    isDarkTheme: Boolean,
    onNicknameChange: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onClose: () -> Unit,
) {
    var nicknameDraft by remember(ownNickname) { mutableStateOf(ownNickname) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Bittr",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
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
            modifier = Modifier.padding(top = 12.dp),
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
            Button(
                onClick = {
                    onNicknameChange(nicknameDraft.trim())
                    onClose()
                },
            ) {
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
            text = "Appearance",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        ThemeSelector(isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme)
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        PermissionList()
        Text(
            text = "Nearby peers:",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 16.dp),
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

@Composable
private fun ThemeSelector(isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        ThemeOption(
            label = "Light",
            iconRes = R.drawable.ic_sun,
            selected = !isDarkTheme,
            onClick = { if (isDarkTheme) onToggleTheme() },
        )
        ThemeOption(
            label = "Dark",
            iconRes = R.drawable.ic_moon,
            selected = isDarkTheme,
            onClick = { if (!isDarkTheme) onToggleTheme() },
        )
    }
}

@Composable
private fun RowScope.ThemeOption(label: String, iconRes: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class PermissionRow(val label: String, val granted: Boolean)

@Composable
private fun PermissionList() {
    val context = LocalContext.current
    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(PermissionRow("Notifications", hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val nearby = hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT) &&
                hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            add(PermissionRow("Nearby devices", nearby))
        } else {
            add(PermissionRow("Location", hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)))
        }
    }
    Column {
        permissions.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !row.granted) { openAppSettings(context) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (row.granted) "Granted" else "Not granted",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    context.startActivity(intent)
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
private fun PostRow(
    item: TimelineItem,
    displayNames: Map<String, String>,
    onLike: () -> Unit,
    onShowLikers: () -> Unit,
    onFingerprint: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Avatar(
                name = item.displayAuthor,
                seed = item.post.author,
                size = 40.dp,
                modifier = Modifier.clickable { onFingerprint(item.post.author) },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayAuthor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = postSubtitle(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onFingerprint(item.post.author) },
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                PostContent(
                    content = item.post.content,
                    displayNames = displayNames,
                    onFingerprint = onFingerprint,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLike, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (item.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (item.likedByMe) "Unlike" else "Like",
                            tint = if (item.likedByMe) {
                                MaterialTheme.colorScheme.primary
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
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        )
    }
}

private fun postSubtitle(item: TimelineItem): String = buildString {
    if (item.displayAuthor != item.post.author) {
        append("@${item.post.author} · ")
    }
    append(TimeFormatter.format(item.post.createdAt))
}

@Composable
private fun PostContent(
    content: String,
    displayNames: Map<String, String>,
    onFingerprint: (String) -> Unit,
) {
    val chipStyle = mentionStyle()
    val listener = LinkInteractionListener { link ->
        if (link is LinkAnnotation.Clickable) {
            onFingerprint(link.tag)
        }
    }
    val annotated = buildAnnotatedString {
        for (segment in Mention.parse(content)) {
            when (segment) {
                is Mention.Segment.Text -> append(segment.text)
                is Mention.Segment.Mention -> {
                    val start = length
                    append("@${displayNames[segment.username] ?: segment.username}")
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = segment.username,
                            styles = TextLinkStyles(style = chipStyle),
                            linkInteractionListener = listener,
                        ),
                        start,
                        length,
                    )
                }
            }
        }
    }
    Text(text = annotated, style = MaterialTheme.typography.bodyLarge)
}

private const val LOAD_MORE_THRESHOLD = 5
