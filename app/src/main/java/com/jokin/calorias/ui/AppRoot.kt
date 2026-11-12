package com.jokin.calorias.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jokin.calorias.R
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.ui.add.AddSheetHost
import com.jokin.calorias.ui.add.AddSheetState
import com.jokin.calorias.ui.add.LocalAddSheet
import com.jokin.calorias.ui.add.MealPhotoScreen
import com.jokin.calorias.ui.add.ScanScreen
import com.jokin.calorias.ui.add.SearchScreen
import com.jokin.calorias.ui.add.VoiceScreen
import com.jokin.calorias.ui.body.BodyScreen
import com.jokin.calorias.ui.calendar.CalendarScreen
import com.jokin.calorias.ui.common.ContainerTransitions
import com.jokin.calorias.ui.common.LocalAppScope
import com.jokin.calorias.ui.common.LocalNavAnimatedScope
import com.jokin.calorias.ui.common.LocalSharedTransitionScope
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.MotionSpec
import com.jokin.calorias.ui.common.ScreenTransitions
import com.jokin.calorias.ui.common.pressScale
import com.jokin.calorias.ui.common.today
import com.jokin.calorias.ui.exercise.ExerciseScreen
import com.jokin.calorias.ui.food.FoodEditorScreen
import com.jokin.calorias.ui.food.LogFoodScreen
import com.jokin.calorias.ui.mine.ComboEditorScreen
import com.jokin.calorias.ui.mine.CombosScreen
import com.jokin.calorias.ui.mine.ExportScreen
import com.jokin.calorias.ui.mine.GoalScreen
import com.jokin.calorias.ui.mine.ImportScreen
import com.jokin.calorias.ui.mine.MineScreen
import com.jokin.calorias.ui.mine.OwnProductsScreen
import com.jokin.calorias.ui.mine.RecipeEditorScreen
import com.jokin.calorias.ui.mine.RecipesScreen
import com.jokin.calorias.ui.mine.SettingsScreen
import com.jokin.calorias.ui.progress.ProgressScreen
import com.jokin.calorias.ui.theme.LocalMarkColor
import com.jokin.calorias.ui.theme.contentOn
import com.jokin.calorias.ui.today.TodayScreen
import java.time.LocalTime

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppRoot(startScan: Boolean, onScanHandled: () -> Unit, needsGoal: Boolean) {
    val nav = rememberNavController()
    val addSheet = remember { AddSheetState() }
    val snackbar = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(startScan) {
        if (startScan) {
            nav.navigate(Routes.scan(today(), MealType.guessFor(LocalTime.now())))
            onScanHandled()
        }
    }
    LaunchedEffect(needsGoal) {
        if (needsGoal) nav.navigate(Routes.GOAL)
    }

    val tabs = listOf(
        Tab(Routes.TODAY, R.string.tab_today, Icons.Outlined.Today, Icons.Filled.Today),
        Tab(Routes.CALENDAR, R.string.tab_diary, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
        Tab(Routes.PROGRESS, R.string.tab_progress, Icons.AutoMirrored.Outlined.ShowChart, Icons.AutoMirrored.Filled.ShowChart),
        Tab(Routes.MINE, R.string.tab_me, Icons.Outlined.Person, Icons.Filled.Person),
    )

    CompositionLocalProvider(LocalSnackbar provides snackbar, LocalAppScope provides appScope, LocalAddSheet provides addSheet) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                AnimatedVisibility(
                    visible = route in Routes.topLevel,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    MainBar(
                        tabs = tabs,
                        route = route,
                        addOpen = addSheet.isOpen,
                        onTab = { nav.goToTab(it) },
                        onAdd = { origin -> addSheet.open(addSheet.todayDay ?: today(), MealType.guessFor(LocalTime.now()), origin) },
                    )
                }
            },
        ) { padding ->
            SharedTransitionLayout(Modifier.padding(padding)) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                nav,
                startDestination = Routes.TODAY,
                enterTransition = { if (isContainerTransform()) ContainerTransitions.enter else ScreenTransitions.enter(this) },
                exitTransition = { if (isContainerTransform()) ContainerTransitions.exit else ScreenTransitions.exit(this) },
                popEnterTransition = { if (isContainerTransform()) ContainerTransitions.enter else ScreenTransitions.popEnter(this) },
                popExitTransition = { if (isContainerTransform()) ContainerTransitions.exit else ScreenTransitions.popExit(this) },
            ) {
                screen(Routes.TODAY) { TodayScreen(nav) }
                screen(Routes.CALENDAR) { CalendarScreen(nav) }
                screen(Routes.PROGRESS) { ProgressScreen() }
                screen(Routes.MINE) { MineScreen(nav) }
                screen(Routes.SCAN, dayMealArgs()) { e -> ScanScreen(nav, e.day(), e.meal()) }
                screen(
                    Routes.SEARCH,
                    dayMealArgs() + navArgument("pick") { type = NavType.BoolType; defaultValue = false },
                ) { e -> SearchScreen(nav, e.day(), e.meal(), pickMode = e.arguments?.getBoolean("pick") == true) }
                screen(Routes.VOICE, dayMealArgs()) { e -> VoiceScreen(nav, e.day(), e.meal()) }
                screen(Routes.MEAL_PHOTO, dayMealArgs()) { e -> MealPhotoScreen(nav, e.day(), e.meal()) }
                screen(
                    Routes.EXERCISE,
                    listOf(
                        navArgument("day") { type = NavType.LongType },
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    ),
                ) { e -> ExerciseScreen(nav, e.day(), e.arguments!!.getLong("id")) }
                screen(Routes.BODY, listOf(navArgument("day") { type = NavType.LongType })) { e -> BodyScreen(nav, e.day()) }
                screen(
                    Routes.LOG,
                    dayMealArgs() + listOf(
                        navArgument("foodId") { type = NavType.LongType },
                        navArgument("entryId") { type = NavType.LongType; defaultValue = 0L },
                    ),
                ) { e ->
                    LogFoodScreen(nav, e.arguments!!.getLong("foodId"), e.day(), e.meal(), e.arguments!!.getLong("entryId"))
                }
                screen(
                    Routes.FOOD,
                    listOf(
                        navArgument("foodId") { type = NavType.LongType },
                        navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                        navArgument("brand") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { e ->
                    val a = e.arguments!!
                    FoodEditorScreen(nav, a.getLong("foodId"), a.getString("barcode").orEmpty(), a.getString("name").orEmpty(), a.getString("brand").orEmpty())
                }
                screen(Routes.COMBOS) { CombosScreen(nav) }
                screen(Routes.COMBO, listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    ComboEditorScreen(nav, e.arguments!!.getLong("id"), e.savedStateHandle)
                }
                screen(Routes.RECIPES) { RecipesScreen(nav) }
                screen(Routes.RECIPE, listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    RecipeEditorScreen(nav, e.arguments!!.getLong("id"), e.savedStateHandle)
                }
                screen(Routes.OWN) { OwnProductsScreen(nav) }
                screen(Routes.GOAL) { GoalScreen(nav) }
                screen(Routes.SETTINGS) { SettingsScreen() }
                screen(Routes.EXPORT) { ExportScreen(nav) }
                screen(Routes.IMPORT) { ImportScreen() }
            }
            }
            }
        }
        AddSheetHost(addSheet, nav)
        }
    }
}

/** Today's rows grow into the entry screen and shrink back into the row (see SharedContainer). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isContainerTransform(): Boolean {
    val routes = setOf(initialState.destination.route, targetState.destination.route)
    return routes == setOf(Routes.TODAY, Routes.LOG)
}

/** A destination whose content knows its own enter/exit animation, for shared containers. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
}

private fun dayMealArgs() = listOf(
    navArgument("day") { type = NavType.LongType },
    navArgument("meal") { type = NavType.StringType },
)

private fun androidx.navigation.NavBackStackEntry.day(): Long = arguments!!.getLong("day")

private fun androidx.navigation.NavBackStackEntry.meal(): MealType =
    MealType.entries.firstOrNull { it.name == arguments?.getString("meal") } ?: MealType.SNACK

/** Standard bottom-bar navigation: one copy of each tab, state kept when switching. */
private fun NavHostController.goToTab(route: String) {
    // Today is also the start destination: the other tabs' stacks are saved under its id, so
    // restoring it would reopen the tab the user just left. Popping back to it avoids that.
    if (route == Routes.TODAY) {
        popBackStack(Routes.TODAY, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Four tabs with the + in the middle; the + reports where it is so the add sheet grows out of it. */
@Composable
private fun MainBar(tabs: List<Tab>, route: String?, addOpen: Boolean, onTab: (String) -> Unit, onAdd: (Offset) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.take(2).forEach { BarTab(it, route == it.route, Modifier.weight(1f)) { onTab(it.route) } }
            AddButton(addOpen, Modifier.weight(1f), onAdd)
            tabs.drop(2).forEach { BarTab(it, route == it.route, Modifier.weight(1f)) { onTab(it.route) } }
        }
    }
}

@Composable
private fun BarTab(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pill by animateFloatAsState(if (selected) 1f else 0f, MotionSpec.fast, label = "tab")
    val indicator = MaterialTheme.colorScheme.secondaryContainer
    val marks = LocalMarkColor.current
    Column(
        modifier.fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(56.dp, 30.dp).drawBehind {
                if (pill > 0f) {
                    val w = size.width * (0.5f + 0.5f * pill)
                    val color = if (marks.turning) marks.value.copy(alpha = 0.3f) else indicator
                    drawRoundRect(color.copy(alpha = color.alpha * pill), Offset((size.width - w) / 2, 0f), Size(w, size.height), CornerRadius(size.height / 2))
                }
            },
            contentAlignment = Alignment.Center,
        ) { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) }
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(tab.label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddButton(open: Boolean, modifier: Modifier, onAdd: (Offset) -> Unit) {
    val source = remember { MutableInteractionSource() }
    var center by remember { mutableStateOf(Offset.Zero) }
    val turn by animateFloatAsState(if (open) 45f else 0f, MotionSpec.fast, label = "plus")
    val marks = LocalMarkColor.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(56.dp)
                .onGloballyPositioned { center = it.boundsInRoot().center }
                .pressScale(source)
                .clip(RoundedCornerShape(18.dp))
                .drawBehind { drawRect(marks.value) }
                .clickable(interactionSource = source, indication = null) { onAdd(center) },
            contentAlignment = Alignment.Center,
        ) {
            // Only this icon recomposes as an RGB mark turns, to stay readable on light hues.
            Icon(Icons.Filled.Add, stringResource(R.string.add_food), tint = contentOn(marks.value), modifier = Modifier.rotate(turn))
        }
    }
}
