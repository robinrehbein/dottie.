package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.graphics.Color

// Die gemeinsame Retro-Farbpalette. Sie lag bisher in GameOverlays.kt und
// damit in :app — sichtbar nur fuer die Android-Oberflaeche, waehrend der
// SpriteKit-Port dieselben Werte ein zweites Mal fuehrte
// (ios/.../Palette.swift). Hier steht sie einmal, fuer jede Oberflaeche.
//
// Was NICHT hierher gehoert: Farben, die zu einem Skin oder einer Kulisse
// gehoeren. Die stehen in :core (SkinPaint, ScenePaint) und sind Daten,
// keine Gestaltung.

val SkyColor = Color(0xFF4EC0CA)
val CloudColor = Color(0xFFE9FCFD)
val BushColor = Color(0xFF71C837)
val BushShadeColor = Color(0xFF5AA82C)
val TrunkColor = Color(0xFF9C6B3C)
val TrunkShade = Color(0xFF7A4E2A)
val GroundSand = Color(0xFFDED895)
val GroundSandShade = Color(0xFFD3C87E)
val GrassLight = Color(0xFF9DE85A)
val GrassDark = Color(0xFF74BF2E)
val OutlineColor = Color(0xFF543847)
val BlockBody = Color(0xFFE0862E)
val BlockLight = Color(0xFFF2A959)
val BlockDark = Color(0xFFA65E1E)
val BlockCap = Color(0xFFFFD28A)
val DotBody = Color(0xFFFFD847)
val DotShade = Color(0xFFF5A623)
val DotShine = Color(0xFFFFF3B8)
val PanelSand = Color(0xFFDED895)
val TextDark = Color(0xFF543847)
val RecordRed = Color(0xFFE53935)

/** Fallen-Zone: klar als Gefahr lesbar, aber unter Zeitdruck verwechselbar. */
val FakeZoneColor = Color(0xFFB44FD8)
val FakeZoneCoreColor = Color(0xFF8A2FB0)
