package ir.courseplanner.app.ui.theme

import androidx.compose.ui.graphics.Color

// Modern Light Palette (Clean, Academic Indigo/Teal with Warm Slate surfaces)
val PrimaryLight = Color(0xFF2563EB)        // Vibrant Royal Blue / Indigo
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFEFF6FF)
val OnPrimaryContainerLight = Color(0xFF1E40AF)

val SecondaryLight = Color(0xFF0D9488)      // Modern Teal
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFF0FDFA)
val OnSecondaryContainerLight = Color(0xFF115E59)

val SurfaceLight = Color(0xFFFFFFFF)        // Crisp White Card Surface
val OnSurfaceLight = Color(0xFF0F172A)
val SurfaceVariantLight = Color(0xFFF1F5F9) // Clean Slate tint
val OnSurfaceVariantLight = Color(0xFF475569)
val BackgroundLight = Color(0xFFF8FAFC)     // Ultra-clean light gray/slate canvas
val OutlineLight = Color(0xFFE2E8F0)
val OutlineVariantLight = Color(0xFFCBD5E1)

// Modern Dark Palette (Deep Space Navy with glowing accents)
val PrimaryDark = Color(0xFF60A5FA)
val OnPrimaryDark = Color(0xFF0B1220)
val PrimaryContainerDark = Color(0xFF1E3A8A)
val OnPrimaryContainerDark = Color(0xFFDBEAFE)

val SecondaryDark = Color(0xFF2DD4BF)
val OnSecondaryDark = Color(0xFF042F2C)
val SecondaryContainerDark = Color(0xFF134E4A)
val OnSecondaryContainerDark = Color(0xFFCCFBF1)

val SurfaceDark = Color(0xFF111827)         // Deep slate surface
val OnSurfaceDark = Color(0xFFF9FAFB)
val SurfaceVariantDark = Color(0xFF1F2937)
val OnSurfaceVariantDark = Color(0xFF9CA3AF)
val BackgroundDark = Color(0xFF0B0F17)      // Midnight canvas
val OutlineDark = Color(0xFF374151)
val OutlineVariantDark = Color(0xFF1F2937)

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

