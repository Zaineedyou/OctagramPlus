package com.octagram.plus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.octagram.plus.data.discord.DiscordRpcManager
import com.octagram.plus.data.discord.DiscordRpcSettings
import com.octagram.plus.data.discord.DiscordRpcSettingsStore
import com.octagram.plus.data.discord.renderDiscordRpcTemplate
import com.octagram.plus.ui.theme.findActivity
import com.octagram.plus.ui.theme.liquidGlassBackground
import kotlinx.coroutines.launch

private enum class DiscordRpcPicker { ActivityType, AccountStatus }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordRpcSettingsScreen(
    enabled: Boolean,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectionStatus by DiscordRpcManager.connectionStatus.collectAsState()
    val isAuthenticated by DiscordRpcManager.isAuthenticated.collectAsState()
    val username by DiscordRpcManager.currentUsername.collectAsState()
    val error by DiscordRpcManager.lastError.collectAsState()

    val initialSettings = remember(context) { DiscordRpcSettingsStore.read(context) }
    var draft by remember { mutableStateOf(initialSettings) }
    var saved by remember { mutableStateOf(initialSettings) }
    var isBusy by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<DiscordRpcPicker?>(null) }
    val hasUnsavedChanges = draft != saved

    fun updateSettings(update: (DiscordRpcSettings) -> DiscordRpcSettings) {
        draft = update(draft)
    }

    LaunchedEffect(Unit) { DiscordRpcManager.initialize(context) }

    val statusText = when (connectionStatus) {
        DiscordRpcManager.ConnectionStatus.Authorizing -> "Menunggu otorisasi Discord…"
        DiscordRpcManager.ConnectionStatus.Connecting -> "Menghubungkan ke Discord…"
        DiscordRpcManager.ConnectionStatus.Connected -> "Terhubung ke Discord"
        DiscordRpcManager.ConnectionStatus.Disconnected ->
            if (isAuthenticated) "Akun tersimpan · belum terhubung" else "Belum terhubung"
    }
    val activityTypeLabel = activityTypeLabel(draft.activityType)
    val accountStatusLabel = accountStatusLabel(draft.accountStatus)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Discord Rich Presence") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassSettingsCard(title = "Akun Discord") {
                Text(
                    text = username?.let { "Terhubung sebagai $it" } ?: statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isAuthenticated) {
                    Text(
                        "Pengaturan di bawah dapat disiapkan sekarang. Hubungkan akun untuk mulai mengirim presence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            val activity = context.findActivity() ?: return@Button
                            if (isBusy) return@Button
                            isBusy = true
                            scope.launch {
                                val success = DiscordRpcManager.authorize(activity)
                                isBusy = false
                                if (success) onToggle(true)
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isBusy) "Menghubungkan…" else "Hubungkan akun Discord")
                    }
                } else {
                    SwitchRow(
                        title = "Bagikan aktivitas musik",
                        description = if (enabled) "Rich Presence sedang aktif" else "Rich Presence sedang dijeda",
                        checked = enabled,
                        onCheckedChange = onToggle,
                    )
                    OutlinedButton(
                        onClick = {
                            DiscordRpcManager.logout()
                            onToggle(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Putuskan akun Discord")
                    }
                }
            }

            if (!error.isNullOrBlank()) {
                GlassSettingsCard(title = "Koneksi Discord") {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = DiscordRpcManager::clearLastError) {
                        Text("Tutup pesan")
                    }
                }
            }

            GlassSettingsCard(title = "Tampilan presence") {
                ChoiceRow(
                    title = "Jenis aktivitas",
                    value = activityTypeLabel,
                    onClick = { picker = DiscordRpcPicker.ActivityType },
                )
                OutlinedTextField(
                    value = draft.activityName,
                    onValueChange = { value -> updateSettings { it.copy(activityName = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama aktivitas (opsional)") },
                    supportingText = { Text("Kosongkan untuk format bawaan Discord seperti di Metrolist.") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.detailsTemplate,
                    onValueChange = { value -> updateSettings { it.copy(detailsTemplate = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Baris detail") },
                    supportingText = { Text("Default: {song.name}") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.stateTemplate,
                    onValueChange = { value -> updateSettings { it.copy(stateTemplate = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Baris artis / state") },
                    supportingText = { Text("Default: {artist.name}") },
                    singleLine = true,
                )
                Text(
                    "Variabel: {song.name} · {artist.name} · {album.name} · {song.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            GlassSettingsCard(title = "Tombol pada Discord") {
                SwitchRow(
                    title = "Tombol 1",
                    description = "Tautan utama di bawah presence",
                    checked = draft.button1Enabled,
                    onCheckedChange = { value -> updateSettings { it.copy(button1Enabled = value) } },
                )
                if (draft.button1Enabled) {
                    OutlinedTextField(
                        value = draft.button1Label,
                        onValueChange = { value -> updateSettings { it.copy(button1Label = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Label tombol 1") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.button1Url,
                        onValueChange = { value -> updateSettings { it.copy(button1Url = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL tombol 1") },
                        supportingText = { Text("Gunakan https://…; mendukung {song.id}, {song.name}, {artist.name}.") },
                        singleLine = true,
                    )
                }
                SwitchRow(
                    title = "Tombol 2",
                    description = "Tautan tambahan di bawah presence",
                    checked = draft.button2Enabled,
                    onCheckedChange = { value -> updateSettings { it.copy(button2Enabled = value) } },
                )
                if (draft.button2Enabled) {
                    OutlinedTextField(
                        value = draft.button2Label,
                        onValueChange = { value -> updateSettings { it.copy(button2Label = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Label tombol 2") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.button2Url,
                        onValueChange = { value -> updateSettings { it.copy(button2Url = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL tombol 2") },
                        supportingText = { Text("Gunakan https://…; mendukung variabel lagu.") },
                        singleLine = true,
                    )
                }
            }

            GlassSettingsCard(title = "Status dan pemutaran") {
                ChoiceRow(
                    title = "Status Discord",
                    value = accountStatusLabel,
                    onClick = { picker = DiscordRpcPicker.AccountStatus },
                )
                SwitchRow(
                    title = "Tampilkan saat dijeda",
                    description = "Jika mati, presence dibersihkan ketika pemutaran dijeda.",
                    checked = draft.showWhilePaused,
                    onCheckedChange = { value -> updateSettings { it.copy(showWhilePaused = value) } },
                )
            }

            PresencePreview(settings = draft)

            GlassSettingsCard(title = "Simpan konfigurasi") {
                Text(
                    if (hasUnsavedChanges) "Perubahan belum diterapkan ke pemutar." else "Pengaturan tersimpan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        DiscordRpcSettingsStore.save(context, draft)
                        saved = draft
                    },
                    enabled = hasUnsavedChanges,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Simpan dan terapkan")
                }
                OutlinedButton(
                    onClick = { draft = DiscordRpcSettings.Defaults },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Kembalikan default Metrolist")
                }
                Text(
                    "Login memakai OAuth 2.0 PKCE di browser. Octagram+ tidak meminta token yang ditempel manual; access/refresh token tersimpan terenkripsi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    picker?.let { activePicker ->
        val title = if (activePicker == DiscordRpcPicker.ActivityType) "Jenis aktivitas" else "Status Discord"
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(title) },
            text = {
                Column {
                    if (activePicker == DiscordRpcPicker.ActivityType) {
                        listOf(0 to "Playing", 2 to "Listening", 3 to "Watching", 5 to "Competing").forEach { (type, label) ->
                            TextButton(
                                onClick = {
                                    updateSettings { it.copy(activityType = type) }
                                    picker = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(label) }
                        }
                    } else {
                        listOf("online" to "Online", "idle" to "Idle", "dnd" to "Jangan ganggu").forEach { (status, label) ->
                            TextButton(
                                onClick = {
                                    updateSettings { it.copy(accountStatus = status) }
                                    picker = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(label) }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun GlassSettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().liquidGlassBackground(RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                content()
            },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onClick) { Text("Ubah") }
    }
}

@Composable
private fun PresencePreview(settings: DiscordRpcSettings) {
    val sampleTitle = "Contoh lagu"
    val sampleArtist = "Contoh artis"
    val sampleAlbum = "Contoh album"
    val details = renderDiscordRpcTemplate(settings.detailsTemplate.ifBlank { "{song.name}" }, sampleTitle, sampleArtist, sampleAlbum, "song-id")
    val state = renderDiscordRpcTemplate(settings.stateTemplate.ifBlank { "{artist.name}" }, sampleTitle, sampleArtist, sampleAlbum, "song-id")
    val activityLabel = activityTypeLabel(settings.activityType)
    GlassSettingsCard(title = "Preview Rich Presence") {
        Text("$activityLabel $details", style = MaterialTheme.typography.titleSmall)
        if (state.isNotBlank()) Text(state, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (settings.activityName.isNotBlank()) {
            Text("Nama aktivitas: ${settings.activityName}", style = MaterialTheme.typography.bodySmall)
        }
        val buttons = buildList {
            if (settings.button1Enabled && settings.button1Label.isNotBlank()) add(settings.button1Label)
            if (settings.button2Enabled && settings.button2Label.isNotBlank()) add(settings.button2Label)
        }
        if (buttons.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text("Tombol: ${buttons.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
        }
        Text("Status akun: ${accountStatusLabel(settings.accountStatus)}", style = MaterialTheme.typography.bodySmall)
    }
}

private fun activityTypeLabel(type: Int): String = when (type) {
    0 -> "Playing"
    3 -> "Watching"
    5 -> "Competing"
    else -> "Listening"
}

private fun accountStatusLabel(status: String): String = when (status) {
    "idle" -> "Idle"
    "dnd" -> "Jangan ganggu"
    else -> "Online"
}
