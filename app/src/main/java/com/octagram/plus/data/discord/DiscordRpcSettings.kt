package com.octagram.plus.data.discord

import android.content.Context
import android.content.SharedPreferences

/** User-configurable Discord presence options. Credentials remain in the encrypted token store. */
data class DiscordRpcSettings(
    val activityType: Int = 2,
    val activityName: String = "",
    val detailsTemplate: String = "{song.name}",
    val stateTemplate: String = "{artist.name}",
    val button1Enabled: Boolean = true,
    val button1Label: String = "Listen on YouTube Music",
    val button1Url: String = "https://music.youtube.com/watch?v={song.id}",
    val button2Enabled: Boolean = true,
    val button2Label: String = "Visit Octagram+",
    val button2Url: String = "https://zaineedyou.my.id",
    val accountStatus: String = "online",
    val showWhilePaused: Boolean = true,
) {
    companion object {
        val Defaults = DiscordRpcSettings()
        val ActivityTypes = setOf(0, 2, 3, 5)
        val AccountStatuses = setOf("online", "idle", "dnd")
    }
}

object DiscordRpcSettingsStore {
    const val PREFS_NAME = "AppSettings"
    const val KEY_SETTINGS_REVISION = "discord_rpc_settings_revision"

    private const val KEY_ACTIVITY_TYPE = "discord_rpc_activity_type"
    private const val KEY_ACTIVITY_NAME = "discord_rpc_activity_name"
    private const val KEY_DETAILS_TEMPLATE = "discord_rpc_details_template"
    private const val KEY_STATE_TEMPLATE = "discord_rpc_state_template"
    private const val KEY_BUTTON1_ENABLED = "discord_rpc_button1_enabled"
    private const val KEY_BUTTON1_LABEL = "discord_rpc_button1_label"
    private const val KEY_BUTTON1_URL = "discord_rpc_button1_url"
    private const val KEY_BUTTON2_ENABLED = "discord_rpc_button2_enabled"
    private const val KEY_BUTTON2_LABEL = "discord_rpc_button2_label"
    private const val KEY_BUTTON2_URL = "discord_rpc_button2_url"
    private const val KEY_ACCOUNT_STATUS = "discord_rpc_account_status"
    private const val KEY_SHOW_WHILE_PAUSED = "discord_rpc_show_while_paused"

    fun read(context: Context): DiscordRpcSettings = read(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun read(preferences: SharedPreferences): DiscordRpcSettings {
        val defaults = DiscordRpcSettings.Defaults
        val activityType = preferences.getInt(KEY_ACTIVITY_TYPE, defaults.activityType)
            .takeIf { it in DiscordRpcSettings.ActivityTypes } ?: defaults.activityType
        val accountStatus = preferences.getString(KEY_ACCOUNT_STATUS, defaults.accountStatus)
            ?.takeIf { it in DiscordRpcSettings.AccountStatuses } ?: defaults.accountStatus
        return DiscordRpcSettings(
            activityType = activityType,
            activityName = preferences.getString(KEY_ACTIVITY_NAME, defaults.activityName).orEmpty(),
            detailsTemplate = preferences.getString(KEY_DETAILS_TEMPLATE, defaults.detailsTemplate).orEmpty(),
            stateTemplate = preferences.getString(KEY_STATE_TEMPLATE, defaults.stateTemplate).orEmpty(),
            button1Enabled = preferences.getBoolean(KEY_BUTTON1_ENABLED, defaults.button1Enabled),
            button1Label = preferences.getString(KEY_BUTTON1_LABEL, defaults.button1Label).orEmpty(),
            button1Url = preferences.getString(KEY_BUTTON1_URL, defaults.button1Url).orEmpty(),
            button2Enabled = preferences.getBoolean(KEY_BUTTON2_ENABLED, defaults.button2Enabled),
            button2Label = preferences.getString(KEY_BUTTON2_LABEL, defaults.button2Label).orEmpty(),
            button2Url = preferences.getString(KEY_BUTTON2_URL, defaults.button2Url).orEmpty(),
            accountStatus = accountStatus,
            showWhilePaused = preferences.getBoolean(KEY_SHOW_WHILE_PAUSED, defaults.showWhilePaused),
        )
    }

    fun save(context: Context, settings: DiscordRpcSettings) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nextRevision = preferences.getInt(KEY_SETTINGS_REVISION, 0) + 1
        preferences.edit()
            .putInt(KEY_ACTIVITY_TYPE, settings.activityType.takeIf { it in DiscordRpcSettings.ActivityTypes } ?: 2)
            .putString(KEY_ACTIVITY_NAME, settings.activityName)
            .putString(KEY_DETAILS_TEMPLATE, settings.detailsTemplate)
            .putString(KEY_STATE_TEMPLATE, settings.stateTemplate)
            .putBoolean(KEY_BUTTON1_ENABLED, settings.button1Enabled)
            .putString(KEY_BUTTON1_LABEL, settings.button1Label)
            .putString(KEY_BUTTON1_URL, settings.button1Url)
            .putBoolean(KEY_BUTTON2_ENABLED, settings.button2Enabled)
            .putString(KEY_BUTTON2_LABEL, settings.button2Label)
            .putString(KEY_BUTTON2_URL, settings.button2Url)
            .putString(KEY_ACCOUNT_STATUS, settings.accountStatus.takeIf { it in DiscordRpcSettings.AccountStatuses } ?: "online")
            .putBoolean(KEY_SHOW_WHILE_PAUSED, settings.showWhilePaused)
            .putInt(KEY_SETTINGS_REVISION, nextRevision)
            .apply()
    }
}

fun renderDiscordRpcTemplate(
    template: String,
    title: String,
    artist: String,
    album: String,
    songId: String,
): String = template
    .replace("{song.name}", title)
    .replace("{artist.name}", artist)
    .replace("{album.name}", album)
    .replace("{song.id}", songId)
    .trim()
