package com.octagram.plus.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// Clearly pink pastel canvas: bright and soft, not the near-white #FFF0F5 used previously.
val AppPinkBackground = Color(0xFFFFC4DC)
val GlassHighlight = Color(0xFFFFFFFF)
val GlassTint = Color(0x66FFF7FA)

val Primary = Color(0xFFE94F88)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFFFE0EB)
val OnPrimaryContainer = Color(0xFF4C1730)
val Secondary = Color(0xFFB94475)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFFFDDE9)
val OnSecondaryContainer = Color(0xFF421B2C)
val Tertiary = Color(0xFF98504B)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFDDD8)
val OnTertiaryContainer = Color(0xFF421D1A)
val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)
val Outline = Color(0xFF986477)
val OutlineVariant = Color(0xFFEBA5BF)
val Surface = AppPinkBackground
val OnSurface = Color(0xFF321B24)
val SurfaceVariant = Color(0xFFFFD9E7)
val OnSurfaceVariant = Color(0xFF62424F)
val SurfaceContainerLow = Color(0x66FFF7FA)
val SurfaceContainer = Color(0x77FFF7FA)
val SurfaceContainerHigh = Color(0x88FFF9FB)
val SurfaceContainerHighest = Color(0x99FFFAFC)

val DarkPrimary = Primary
val DarkOnPrimary = OnPrimary
val DarkPrimaryContainer = PrimaryContainer
val DarkOnPrimaryContainer = OnPrimaryContainer
val DarkSecondary = Secondary
val DarkOnSecondary = OnSecondary
val DarkSecondaryContainer = SecondaryContainer
val DarkOnSecondaryContainer = OnSecondaryContainer
val DarkTertiary = Tertiary
val DarkOnTertiary = OnTertiary
val DarkTertiaryContainer = TertiaryContainer
val DarkOnTertiaryContainer = OnTertiaryContainer
val DarkSurface = AppPinkBackground
val DarkOnSurface = OnSurface
val DarkSurfaceVariant = SurfaceVariant
val DarkOnSurfaceVariant = OnSurfaceVariant
val DarkSurfaceContainer = SurfaceContainer
val DarkSurfaceContainerHigh = SurfaceContainerHigh
val DarkSurfaceContainerHighest = SurfaceContainerHighest

val PrimaryArgb: Int get() = Primary.toArgb()
val OnPrimaryArgb: Int get() = OnPrimary.toArgb()
val PrimaryContainerArgb: Int get() = PrimaryContainer.toArgb()
val OnPrimaryContainerArgb: Int get() = OnPrimaryContainer.toArgb()
val SecondaryArgb: Int get() = Secondary.toArgb()
val OnSecondaryArgb: Int get() = OnSecondary.toArgb()
val SecondaryContainerArgb: Int get() = SecondaryContainer.toArgb()
val OnSecondaryContainerArgb: Int get() = OnSecondaryContainer.toArgb()
val TertiaryArgb: Int get() = Tertiary.toArgb()
val SurfaceArgb: Int get() = Surface.toArgb()
val OnSurfaceArgb: Int get() = OnSurface.toArgb()
val SurfaceVariantArgb: Int get() = SurfaceVariant.toArgb()
val OnSurfaceVariantArgb: Int get() = OnSurfaceVariant.toArgb()
val SurfaceContainerLowArgb: Int get() = SurfaceContainerLow.toArgb()
val SurfaceContainerArgb: Int get() = SurfaceContainer.toArgb()
val SurfaceContainerHighArgb: Int get() = SurfaceContainerHigh.toArgb()
val SurfaceContainerHighestArgb: Int get() = SurfaceContainerHighest.toArgb()
