package com.jokin.calorias.ui.add

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jokin.calorias.R
import com.jokin.calorias.data.QUICK_SERVING_GRAMS
import com.jokin.calorias.data.forGrams
import com.jokin.calorias.domain.Macro
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.domain.Nutrients
import com.jokin.calorias.i18n.labelRes
import com.jokin.calorias.ui.Routes
import com.jokin.calorias.ui.common.AvatarStack
import com.jokin.calorias.ui.common.FoodAvatar
import com.jokin.calorias.ui.common.LocalSnackbar
import com.jokin.calorias.ui.common.MealSelector
import com.jokin.calorias.ui.common.Muted
import com.jokin.calorias.ui.common.NumberField
import com.jokin.calorias.ui.common.amountText
import com.jokin.calorias.ui.common.appContainer
import com.jokin.calorias.ui.common.dayTitle
import com.jokin.calorias.ui.common.kcal
import com.jokin.calorias.ui.common.parseDecimal
import com.jokin.calorias.ui.common.pressScale
import com.jokin.calorias.ui.common.rememberReduceMotion
import com.jokin.calorias.ui.common.today
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Something waiting in the tray to be logged when the user taps "Add". */
sealed interface TrayItem {
    val name: String
    val kcal: Double
    val macro: Macro?

    data class Food(val foodId: Long, val grams: Double, override val name: String, override val kcal: Double, override val macro: Macro?) : TrayItem
    data class Combo(val comboId: Long, override val name: String, override val kcal: Double, override val macro: Macro?) : TrayItem
}

/**
 * The add sheet lives above every screen. Today tells it which day it shows and where its ring is,
 * so the + adds to that day and the sheet can be sucked into the ring when the tray is logged.
 */
@Stable
class AddSheetState {
    var isOpen by mutableStateOf(false)
        private set
    var day by mutableLongStateOf(0L)
        private set
    var meal by mutableStateOf(MealType.SNACK)
    /** Root position the sheet grows from: the + button or the meal row tapped. */
    var origin by mutableStateOf<Offset?>(null)
        private set
    /** Changes on every opening, to restart the reveal. */
    var openings by mutableIntStateOf(0)
        private set
    val tray = mutableStateListOf<TrayItem>()

    /** Day open on Today while Today is on screen; the + adds there. */
    var todayDay by mutableStateOf<Long?>(null)
    /** Today's ring: the day it shows and its centre in root coordinates. */
    var ringTarget by mutableStateOf<Pair<Long, Offset>?>(null)

    fun open(day: Long, meal: MealType, origin: Offset?) {
        tray.clear()
        this.day = day
        this.meal = meal
        this.origin = origin
        openings++
        isOpen = true
    }

    /** Leaving without logging drops what was in the tray. */
    fun close() {
        isOpen = false
        tray.clear()
    }
}

val LocalAddSheet = staticCompositionLocalOf<AddSheetState> { error("no add sheet") }

private class GenieRun(val bitmap: Bitmap, val bounds: Rect, val target: Offset)

private class Flight(val id: Int, val item: TrayItem, val from: Offset)

@Composable
fun AddSheetHost(state: AddSheetState, nav: NavController) {
    var genie by remember { mutableStateOf<GenieRun?>(null) }
    // Drawn above the Scaffold, so no Surface sets the text colour: without this it stays black in dark theme.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        if (state.isOpen) AddSheet(state, nav, onGenie = { genie = it })
    }
    genie?.let { run -> GenieOverlay(run, onDone = { genie = null }) }
}

@Composable
private fun AddSheet(state: AddSheetState, nav: NavController, onGenie: (GenieRun) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()

    val recent by remember { c.foods.observeRecent(today() - 14) }.collectAsState(initial = emptyList())
    val combos by remember { c.combos.observeAll() }.collectAsState(initial = emptyList())
    var quickAdd by rememberSaveable { mutableStateOf(false) }

    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val slide = remember { Animatable(0f) }
    val trayScale = remember { Animatable(1f) }
    val layer = rememberGraphicsLayer()
    var sheetBounds by remember { mutableStateOf(Rect.Zero) }
    var trayEnd by remember { mutableStateOf(Offset.Zero) }
    val avatarCenters = remember { mutableStateMapOf<String, Offset>() }
    val flights = remember { mutableStateListOf<Flight>() }
    var flightIds by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(state.openings) {
        slide.snapTo(0f)
        if (!reduceMotion) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(REVEAL_MS, easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)))
        }
    }

    fun addedMessage(n: Int): String =
        res.getQuantityString(R.plurals.add_added_to_meal, n, n, res.getString(state.meal.labelRes()).lowercase())

    fun dismiss() {
        if (busy) return
        busy = true
        scope.launch {
            if (!reduceMotion) slide.animateTo(1f, tween(DISMISS_MS, easing = FastOutLinearInEasing))
            busy = false
            state.close()
        }
    }

    /** Logs everything in the tray; what was logged leaves the tray, so a retry never doubles it. */
    suspend fun logTray(): Int {
        val items = state.tray.toList()
        items.forEach { item ->
            when (item) {
                is TrayItem.Food -> c.log.logFood(state.day, state.meal, item.foodId, item.grams)
                is TrayItem.Combo -> c.log.logCombo(state.day, state.meal, item.comboId)
            }
            state.tray.remove(item)
        }
        return items.size
    }

    fun commit() {
        if (busy || state.tray.isEmpty()) return
        busy = true
        scope.launch {
            try {
                val n = logTray()
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                val target = state.ringTarget?.takeIf { it.first == state.day }?.second
                if (target != null && target.y < sheetBounds.top && !reduceMotion) {
                    // A software copy: drawBitmapMesh cannot draw the hardware bitmap the layer gives.
                    val bitmap = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
                    onGenie(GenieRun(bitmap, sheetBounds, target))
                    state.close()
                } else {
                    if (!reduceMotion) slide.animateTo(1f, tween(DISMISS_MS, easing = FastOutLinearInEasing))
                    state.close()
                    snackbar.showSnackbar(addedMessage(n))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    /** Opens another screen; what is already in the tray is logged first so nothing is lost. */
    fun leaveTo(route: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val n = if (state.tray.isEmpty()) 0 else logTray()
                state.close()
                nav.navigate(route)
                if (n > 0) snackbar.showSnackbar(addedMessage(n))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    fun fly(item: TrayItem, avatarKey: String) {
        val from = avatarCenters[avatarKey]
        if (from == null || reduceMotion) {
            state.tray.add(item)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            return
        }
        flights.add(Flight(flightIds++, item, from))
    }

    BackHandler { dismiss() }

    val scrimAlpha = (1f - slide.value) * (if (reduceMotion) 1f else reveal.value.coerceAtMost(1f))
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f * scrimAlpha))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismiss() },
        )
        val surface = MaterialTheme.colorScheme.surfaceContainerLow
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(SHEET_HEIGHT)
                .onGloballyPositioned { sheetBounds = it.boundsInRoot() }
                .graphicsLayer {
                    translationY = slide.value * size.height
                    val o = state.origin?.let { it - sheetBounds.topLeft } ?: Offset(size.width / 2, size.height)
                    val far = hypot(max(o.x, size.width - o.x), max(o.y, size.height - o.y))
                    val r = REVEAL_START.toPx() + (far - REVEAL_START.toPx()) * reveal.value
                    shape = if (reveal.value < 1f) GenericShape { _, _ -> addOval(Rect(o, r)) } else SHEET_SHAPE
                    clip = true
                }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                .background(surface, SHEET_SHAPE)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Box(Modifier.padding(top = 10.dp).size(36.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.add_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Muted(dayTitle(state.day, today()))
            }
            Spacer(Modifier.height(10.dp))
            MealSelector(state.meal, { state.meal = it })
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { leaveTo(Routes.search(state.day, state.meal)) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.add_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Tool(stringResource(R.string.add_tool_scan), Icons.Outlined.QrCodeScanner, Modifier.weight(1f)) { leaveTo(Routes.scan(state.day, state.meal)) }
                Tool(stringResource(R.string.add_tool_voice), Icons.Outlined.Mic, Modifier.weight(1f)) { leaveTo(Routes.voice(state.day, state.meal)) }
                Tool(stringResource(R.string.add_tool_plate), Icons.Outlined.Restaurant, Modifier.weight(1f)) { leaveTo(Routes.mealPhoto(state.day, state.meal)) }
                Tool(stringResource(R.string.add_tool_quick), Icons.Outlined.Bolt, Modifier.weight(1f)) { quickAdd = true }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (combos.isNotEmpty()) {
                    item { ListHeader(stringResource(R.string.add_header_combos)) }
                    items(combos, key = { "c" + it.combo.id }) { combo ->
                        val total = Nutrients.sum(combo.items.map { it.food.forGrams(it.item.grams) })
                        val avatar = "c" + combo.combo.id
                        PickRow(
                            name = combo.combo.name,
                            subtitle = pluralStringResource(R.plurals.add_combo_summary, combo.items.size, combo.items.size, kcal(total.kcal)),
                            macro = total.dominantMacro(),
                            onAvatarPlaced = { avatarCenters[avatar] = it },
                            onOpen = { leaveTo(Routes.combo(combo.combo.id)) },
                            onAdd = { fly(TrayItem.Combo(combo.combo.id, combo.combo.name, total.kcal, total.dominantMacro()), avatar) },
                        )
                    }
                }
                item { ListHeader(stringResource(R.string.add_header_recent)) }
                if (recent.isEmpty()) item { Muted(stringResource(R.string.add_recent_empty)) }
                items(recent, key = { "r" + it.food.id }) { r ->
                    val n = r.food.forGrams(r.lastGrams)
                    val avatar = "r" + r.food.id
                    PickRow(
                        name = r.food.name,
                        subtitle = "${amountText(r.food, r.lastGrams)} · ${kcal(n.kcal)} kcal",
                        macro = n.dominantMacro(),
                        onAvatarPlaced = { avatarCenters[avatar] = it },
                        onOpen = { leaveTo(Routes.log(r.food.id, state.day, state.meal)) },
                        onAdd = { fly(TrayItem.Food(r.food.id, r.lastGrams, r.food.name, n.kcal, n.dominantMacro()), avatar) },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp)
                        .graphicsLayer { scaleX = trayScale.value; scaleY = trayScale.value }
                        .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarStack(
                        state.tray.map { it.name to it.macro },
                        ring = MaterialTheme.colorScheme.surfaceContainerHigh,
                        // positionInRoot, not boundsInRoot: an empty stack has no size and its bounds collapse to zero.
                        modifier = Modifier.onGloballyPositioned { trayEnd = it.positionInRoot() + Offset(it.size.width.toFloat(), it.size.height / 2f) },
                    )
                    if (state.tray.isEmpty()) Muted(stringResource(R.string.add_tray_empty))
                    else {
                        Spacer(Modifier.width(10.dp))
                        Text("${kcal(state.tray.sumOf { it.kcal })} kcal", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Button(onClick = ::commit, enabled = state.tray.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (state.tray.isEmpty()) stringResource(R.string.add_add) else stringResource(R.string.add_add_count, state.tray.size))
                }
            }
        }

        flights.forEach { f ->
            key(f.id) {
                FlyingAvatar(f, to = trayEnd) {
                    flights.remove(f)
                    state.tray.add(f.item)
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    scope.launch {
                        trayScale.snapTo(TRAY_BUMP)
                        trayScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 700f))
                    }
                }
            }
        }
    }

    if (quickAdd) {
        QuickAddDialog(onDismiss = { quickAdd = false }) { name, total ->
            scope.launch {
                try {
                    val food = c.foods.saveQuick(name, total)
                    c.log.logFood(state.day, state.meal, food.id, QUICK_SERVING_GRAMS)
                    quickAdd = false
                    state.close()
                    snackbar.showSnackbar(addedMessage(1))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    snackbar.showSnackbar(e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }
}

/** The avatar of a picked food travels to the tray along an arc, swelling a little mid-air. */
@Composable
private fun FlyingAvatar(f: Flight, to: Offset, onArrive: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(FLIGHT_MS, easing = FastOutSlowInEasing))
        onArrive()
    }
    val size = 30.dp
    val density = LocalDensity.current
    val half = with(density) { (size / 2).toPx() }
    // Rises above both ends and drops into the tray, staying on screen whichever side they are.
    val control = Offset((f.from.x + to.x) / 2, minOf(f.from.y, to.y) - with(density) { ARC_LIFT.toPx() })
    Box(
        Modifier
            .offset {
                val p = t.value
                val q = 1 - p
                val x = q * q * f.from.x + 2 * q * p * control.x + p * p * to.x
                val y = q * q * f.from.y + 2 * q * p * control.y + p * p * to.y
                IntOffset((x - half).roundToInt(), (y - half).roundToInt())
            }
            .graphicsLayer {
                val s = 1f + sin(t.value * PI.toFloat()) * 0.35f - t.value * 0.2f
                scaleX = s
                scaleY = s
                shadowElevation = 6.dp.toPx()
                shape = CircleShape
            },
    ) { FoodAvatar(f.item.name, f.item.macro, size = size) }
}

/** Draws the captured sheet through the genie mesh while it is pulled into the ring. */
@Composable
private fun GenieOverlay(run: GenieRun, onDone: () -> Unit) {
    val progress = remember(run) { Animatable(0f) }
    LaunchedEffect(run) {
        progress.animateTo(1f, tween(GENIE_MS, easing = LinearEasing))
        onDone()
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    Canvas(Modifier.fillMaxSize()) {
        val b = run.bounds
        val mesh = GenieMesh.vertices(progress.value, b.left, b.top, b.width, b.height, run.target.x, run.target.y, MESH_COLS, MESH_ROWS)
        drawIntoCanvas { it.nativeCanvas.drawBitmapMesh(run.bitmap, MESH_COLS, MESH_ROWS, mesh, 0, null, 0, paint) }
    }
}

@Composable
private fun Tool(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(interactionSource = source, indication = null, onClick = onClick).pressScale(source).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickRow(name: String, subtitle: String, macro: Macro?, onAvatarPlaced: (Offset) -> Unit, onOpen: () -> Unit, onAdd: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        FoodAvatar(name, macro, Modifier.onGloballyPositioned { onAvatarPlaced(it.boundsInRoot().center) }, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Muted(subtitle)
        }
        Box(
            Modifier.size(40.dp).pressScale(source).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(interactionSource = source, indication = null, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, stringResource(R.string.add_add_to_tray, name), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
    }
}

@Composable
private fun QuickAddDialog(onDismiss: () -> Unit, onSave: (String, Nutrients) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcalText by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    val total = parseDecimal(kcalText)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_quick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text(stringResource(R.string.add_name_optional)) }, modifier = Modifier.fillMaxWidth())
                NumberField(kcalText, { kcalText = it }, stringResource(R.string.add_calories), Modifier.fillMaxWidth(), suffix = "kcal", decimals = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(protein, { protein = it }, stringResource(R.string.add_protein_short), Modifier.weight(1f), suffix = "g")
                    NumberField(carbs, { carbs = it }, stringResource(R.string.add_carbs_short), Modifier.weight(1f), suffix = "g")
                    NumberField(fat, { fat = it }, stringResource(R.string.add_fat_short), Modifier.weight(1f), suffix = "g")
                }
                Muted(stringResource(R.string.add_quick_hint))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name.trim(), Nutrients(total ?: 0.0, parseDecimal(protein) ?: 0.0, parseDecimal(carbs) ?: 0.0, parseDecimal(fat) ?: 0.0))
                },
                enabled = total != null,
            ) { Text(stringResource(R.string.add_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_cancel)) } },
    )
}

/** Leaves Today's ring visible above the sheet, so the sheet can be pulled into it. */
private const val SHEET_HEIGHT = 0.62f
private val SHEET_SHAPE = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val REVEAL_START = 28.dp
private const val REVEAL_MS = 380
private const val DISMISS_MS = 220
private const val FLIGHT_MS = 520
private val ARC_LIFT = 90.dp
private const val GENIE_MS = 620
private const val TRAY_BUMP = 1.07f
private const val MESH_COLS = 12
private const val MESH_ROWS = 24
