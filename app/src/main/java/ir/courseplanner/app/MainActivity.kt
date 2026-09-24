package ir.courseplanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.courseplanner.app.data.preferences.ThemeMode
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.screens.CoursesScreen
import ir.courseplanner.app.ui.screens.DocumentsScreen
import ir.courseplanner.app.ui.screens.HomeScreen
import ir.courseplanner.app.ui.screens.ScheduleScreen
import ir.courseplanner.app.ui.screens.SettingsScreen
import ir.courseplanner.app.ui.theme.MyApplicationTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: CoursePlannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MyApplicationTheme(
                darkTheme = isDark,
                colorTheme = preferences.theme
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CoursePlannerApp(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun CoursePlannerApp(viewModel: CoursePlannerViewModel) {
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Surface(
                modifier = Modifier
                    .shadow(8.dp)
                    .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                color = MaterialTheme.colorScheme.surface
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("main_bottom_nav")
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    NavigationBarItem(
                        selected = currentDestination == AppDestination.HOME,
                        onClick = { viewModel.navigateTo(AppDestination.HOME) },
                        icon = {
                            Icon(
                                if (currentDestination == AppDestination.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "خانه"
                            )
                        },
                        label = {
                            Text(
                                "خانه",
                                fontSize = 11.5.sp,
                                fontWeight = if (currentDestination == AppDestination.HOME) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_item_home")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.COURSES,
                        onClick = { viewModel.navigateTo(AppDestination.COURSES) },
                        icon = {
                            Icon(
                                 if (currentDestination == AppDestination.COURSES) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = "دروس"
                            )
                        },
                        label = {
                            Text(
                                "دروس",
                                fontSize = 11.5.sp,
                                fontWeight = if (currentDestination == AppDestination.COURSES) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_item_courses")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.SCHEDULE,
                        onClick = { viewModel.navigateTo(AppDestination.SCHEDULE) },
                        icon = {
                            Icon(
                                if (currentDestination == AppDestination.SCHEDULE) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                contentDescription = "برنامه‌ساز"
                            )
                        },
                        label = {
                            Text(
                                "برنامه‌ساز",
                                fontSize = 11.sp,
                                fontWeight = if (currentDestination == AppDestination.SCHEDULE) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_item_schedule")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.DOCUMENTS,
                        onClick = { viewModel.navigateTo(AppDestination.DOCUMENTS) },
                        icon = {
                            Icon(
                                if (currentDestination == AppDestination.DOCUMENTS) Icons.Filled.Description else Icons.Outlined.Description,
                                contentDescription = "جزوات"
                            )
                        },
                        label = {
                            Text(
                                "جزوات",
                                fontSize = 11.sp,
                                fontWeight = if (currentDestination == AppDestination.DOCUMENTS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_item_documents")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.SETTINGS,
                        onClick = { viewModel.navigateTo(AppDestination.SETTINGS) },
                        icon = {
                            Icon(
                                if (currentDestination == AppDestination.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "تنظیمات"
                            )
                        },
                        label = {
                            Text(
                                "تنظیمات",
                                fontSize = 11.sp,
                                fontWeight = if (currentDestination == AppDestination.SETTINGS) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = navColors,
                        modifier = Modifier.testTag("nav_item_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val slideOffset = if (forward) 200 else -200
                (slideInHorizontally(animationSpec = tween(280)) { slideOffset } + fadeIn(animationSpec = tween(240)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(280)) { -slideOffset } + fadeOut(animationSpec = tween(200)))
            },
            label = "ScreenTransition"
        ) { destination ->
            when (destination) {
                AppDestination.HOME -> HomeScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.COURSES -> CoursesScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.SCHEDULE -> ScheduleScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.DOCUMENTS -> DocumentsScreen(viewModel = viewModel, modifier = screenModifier)
                AppDestination.SETTINGS -> SettingsScreen(viewModel = viewModel, modifier = screenModifier)
            }
        }
    }
}
