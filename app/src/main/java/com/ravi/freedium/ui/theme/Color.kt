package com.ravi.freedium.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Peacock palette.
 *
 * Drawn from the bird rather than invented: the iridescent teal of the breast carries the
 * primary role, the deeper plume blue the secondary, and the gold-bronze eye of the tail
 * the tertiary accent. Neutrals are tinted very slightly toward teal so surfaces sit with
 * the palette instead of looking like grey pasted underneath it.
 */

// --- Peacock teal: breast and neck, the signature colour ----------------------
val PeacockTeal10 = Color(0xFF00201F)
val PeacockTeal20 = Color(0xFF00373A)
val PeacockTeal30 = Color(0xFF005055)
val PeacockTeal40 = Color(0xFF0A6C74)   // primary, light
val PeacockTeal80 = Color(0xFF5CD5DF)   // primary, dark
val PeacockTeal90 = Color(0xFFB0ECF1)
val PeacockTeal95 = Color(0xFFD8F6F8)

// --- Plume blue: the deeper blue further down the feather ---------------------
val PlumeBlue10 = Color(0xFF001B33)
val PlumeBlue20 = Color(0xFF002F53)
val PlumeBlue30 = Color(0xFF0F4676)
val PlumeBlue40 = Color(0xFF2A5F92)      // secondary, light
val PlumeBlue80 = Color(0xFFA2C9F0)      // secondary, dark
val PlumeBlue90 = Color(0xFFD2E4FF)

// --- Tail-eye gold: the bronze ring in the eye of the feather -----------------
val TailGold10 = Color(0xFF261900)
val TailGold20 = Color(0xFF402D00)
val TailGold30 = Color(0xFF5C4200)
val TailGold40 = Color(0xFF7A5900)       // tertiary, light
val TailGold80 = Color(0xFFEFBF48)       // tertiary, dark - the favourite icon
val TailGold90 = Color(0xFFFFDF9B)

// --- Neutrals, warmed a touch toward the plume --------------------------------
val PeacockInk = Color(0xFF0C1414)       // darkest surface
val PeacockSurfaceDark = Color(0xFF111D1F)
val PeacockSurfaceDarkHigh = Color(0xFF18292B)
val PeacockBone = Color(0xFFF6FBFB)      // lightest surface
val PeacockSurfaceLight = Color(0xFFEAF3F3)
val PeacockOutline = Color(0xFF6F7A7B)
val PeacockOutlineDark = Color(0xFF3E4B4C)
val PeacockOnSurfaceLight = Color(0xFF171D1D)
val PeacockOnSurfaceDark = Color(0xFFDDE4E4)
val PeacockOnSurfaceVariantLight = Color(0xFF4B5556)
val PeacockOnSurfaceVariantDark = Color(0xFFBEC8C9)

// --- Attention: the deep violet iridescence beside the teal in a plume ---------
//
// Red is deliberately absent from this palette. It fights the teal and reads as an alarm
// in an app where "needs attention" only ever means a link could not be recovered. The
// violet is dark enough to carry the same weight while still belonging to the bird.
val PlumeViolet10 = Color(0xFF230A38)
val PlumeViolet20 = Color(0xFF3A2050)
val PlumeViolet30 = Color(0xFF4E3266)
val PlumeViolet40 = Color(0xFF5C3A75)
val PlumeViolet80 = Color(0xFFD6B9E8)
val PlumeViolet90 = Color(0xFFEBDDF4)
