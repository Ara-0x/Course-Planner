package ir.courseplanner.app.ui.theme

import androidx.compose.ui.graphics.Color







// Status & Indicator Colors
val ConflictRedLight = Color(0xFFDC2626)
val ConflictRedBgLight = Color(0xFFFEF2F2)
val ConflictRedDark = Color(0xFFF87171)
val ConflictRedBgDark = Color(0xFF450A0A)

val SuccessGreenLight = Color(0xFF059669)
val SuccessGreenBgLight = Color(0xFFECFDF5)
val SuccessGreenDark = Color(0xFF34D399)
val SuccessGreenBgDark = Color(0xFF064E3B)

val WarningAmberLight = Color(0xFFD97706)
val WarningAmberBgLight = Color(0xFFFFFBEB)
val WarningAmberDark = Color(0xFFFBBF24)
val WarningAmberBgDark = Color(0xFF451A03)

// Bookmark / "best option" star. The light variant is a deep amber so it keeps
// contrast on a white surface; the dark variant is the bright one.
val BookmarkAmberLight = Color(0xFFB45309)
val BookmarkAmberDark = Color(0xFFFBBF24)

// Document-category accents (see DocumentsScreen). Each pair is used for the
// icon/label and, at low alpha, for the same category's background: the light
// variant is deep enough for white, the dark variant bright enough for navy.
val CategoryGreenLight = Color(0xFF047857)
val CategoryGreenDark = Color(0xFF34D399)
val CategoryPurpleLight = Color(0xFF6D28D9)
val CategoryPurpleDark = Color(0xFFA78BFA)
val CategoryOrangeLight = Color(0xFFC2410C)
val CategoryOrangeDark = Color(0xFFFB923C)
val CategoryTealLight = Color(0xFF0E7490)
val CategoryTealDark = Color(0xFF22D3EE)
val CategoryPinkLight = Color(0xFFBE185D)
val CategoryPinkDark = Color(0xFFFB7185)

// Course accent palettes. The light variants are deliberately deeper so the
// course title stays readable on a white timetable; the dark variants retain
// the brighter, high-contrast colours needed on the navy surface.
val CourseColorListLight = listOf(
    Color(0xFF1D4ED8), Color(0xFF047857), Color(0xFF6D28D9), Color(0xFFB45309),
    Color(0xFFBE185D), Color(0xFF0E7490), Color(0xFF4338CA), Color(0xFF0F766E),
    Color(0xFFC2410C), Color(0xFF4D7C0F)
)

val CourseColorListDark = listOf(
    Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFFA78BFA), Color(0xFFFBBF24),
    Color(0xFFFB7185), Color(0xFF22D3EE), Color(0xFF818CF8), Color(0xFF2DD4BF),
    Color(0xFFFB923C), Color(0xFFA3E635)
)

