package com.octagram.plus.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat.getInsetsController

@Composable
fun <T> SharedPreferences.observeKey(key: String, defaultValue: T): State<T> {
    val preferences = this
    val state = remember(preferences, key) { mutableStateOf(readPreference(preferences, key, defaultValue)) }
    DisposableEffect(preferences, key) {
        state.value = readPreference(preferences, key, defaultValue)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPreferences, changedKey ->
            if (changedKey == key) state.value = readPreference(changedPreferences, key, defaultValue)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

@Suppress("UNCHECKED_CAST")
private fun <T> readPreference(preferences: SharedPreferences, key: String, defaultValue: T): T =
    when (defaultValue) {
        is Boolean -> preferences.getBoolean(key, defaultValue) as T
        is String -> (preferences.getString(key, defaultValue) ?: defaultValue) as T
        is Float -> preferences.getFloat(key, defaultValue) as T
        is Int -> preferences.getInt(key, defaultValue) as T
        is Long -> preferences.getLong(key, defaultValue) as T
        else -> defaultValue
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OctagramPlusTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicAlbumColor: Int? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = lightColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnPrimaryContainer,
        secondary = Secondary,
        onSecondary = OnSecondary,
        secondaryContainer = SecondaryContainer,
        onSecondaryContainer = OnSecondaryContainer,
        tertiary = Tertiary,
        onTertiary = OnTertiary,
        tertiaryContainer = TertiaryContainer,
        onTertiaryContainer = OnTertiaryContainer,
        error = Error,
        onError = OnError,
        errorContainer = ErrorContainer,
        onErrorContainer = OnErrorContainer,
        background = AppPinkBackground,
        onBackground = OnSurface,
        surface = AppPinkBackground,
        onSurface = OnSurface,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = OnSurfaceVariant,
        surfaceContainerLowest = Color(0x55FFF7FA),
        surfaceContainerLow = SurfaceContainerLow,
        surfaceContainer = SurfaceContainer,
        surfaceContainerHigh = SurfaceContainerHigh,
        surfaceContainerHighest = SurfaceContainerHighest,
        outline = Outline,
        outlineVariant = OutlineVariant,
        scrim = Color(0x66000000)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.decorView.setBackgroundColor(AppPinkBackground.toArgb())
                getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

internal tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
