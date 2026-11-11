package com.maisonchahyn.app.launcher

import android.app.ActivityOptions
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.maisonchahyn.app.Prefs
import com.maisonchahyn.app.system.SystemTools
import com.maisonchahyn.app.toast
import com.maisonchahyn.app.ui.Backdrop
import com.maisonchahyn.app.ui.BackdropHost
import com.maisonchahyn.app.ui.LiquidGlass
import com.maisonchahyn.app.ui.MaisonTheme
import com.maisonchahyn.app.ui.WallpaperStore
import com.maisonchahyn.app.ui.drawImageCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

class LauncherActivity : ComponentActivity() {

    private val homePressed = mutableIntStateOf(0)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lifecycleScope.launch { AppRepository.load(applicationContext) }
        }
    }

    private val laCallback = object : LauncherApps.Callback() {
        private fun reload() {
            lifecycleScope.launch { AppRepository.load(applicationContext) }
        }

        override fun onPackageRemoved(packageName: String?, user: android.os.UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: android.os.UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: android.os.UserHandle?) = reload()
        override fun onPackagesAvailable(p: Array<out String>?, user: android.os.UserHandle?, r: Boolean) = reload()
        override fun onPackagesUnavailable(p: Array<out String>?, user: android.os.UserHandle?, r: Boolean) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            getSystemService(LauncherApps::class.java).registerCallback(laCallback)
        } catch (_: Throwable) {
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, f, ContextCompat.RECEIVER_EXPORTED)
        setContent { MaisonTheme { LauncherScreen(homePressed.intValue) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.ACTION_MAIN == intent.action) homePressed.intValue++
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (_: Throwable) {
        }
        try {
            getSystemService(LauncherApps::class.java).unregisterCallback(laCallback)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}

fun launchApp(ctx: Context, view: View, app: AppEntry, bounds: Rect) {
    try {
        val opts = ActivityOptions.makeScaleUpAnimation(
            view, bounds.left.toInt(), bounds.top.toInt(),
            max(1, bounds.width.toInt()), max(1, bounds.height.toInt())
        ).toBundle()
        val la = ctx.getSystemService(LauncherApps::class.java)
        la.startMainActivity(
            ComponentName(app.pkg, app.cls), app.user,
            android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()),
            opts
        )
    } catch (e: Throwable) {
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(app.pkg, app.cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ctx.startActivity(i)
        } catch (e2: Throwable) {
            ctx.toast("Impossible d'ouvrir ${app.label}")
        }
    }
}

@Composable
fun rememberLauncherBackdrop(): Backdrop {
    val ctx = LocalContext.current
    LaunchedEffect(Prefs.wallpaperPath.value, Prefs.wallpaperVersion.value) { WallpaperStore.ensure(ctx) }
    val useSystem = Prefs.lUseSystemWallpaper.value
    val own = WallpaperStore.image.value
    // Si aucun fond Maison : on tente de lire le fond système (peut être refusé par Android)
    var sys by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(useSystem, own == null) {
        if (useSystem || own == null) {
            sys = withContext(Dispatchers.IO) {
                try {
                    val d = WallpaperManager.getInstance(ctx).drawable
                    if (d == null || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) null
                    else {
                        val sc = 1080f / d.intrinsicWidth
                        d.toBitmap((d.intrinsicWidth * sc).toInt(), (d.intrinsicHeight * sc).toInt()).asImageBitmap()
                    }
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }
    val img = if (useSystem) sys else (own ?: sys)
    val dim = Prefs.lDimWallpaper.value
    return remember(img, dim) {
        if (img == null) Backdrop(isEmpty = true) {}
        else Backdrop { s ->
            drawImageCover(img, s)
            if (dim > 0f) drawRect(Color.Black.copy(alpha = dim), size = s)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(homePressed: Int) {
    val ctx = LocalContext.current
    val apps = AppRepository.apps.value
    val hidden = Prefs.lHidden.value
    val dockOn = Prefs.lDock.value
    val dockKeys = Dock.keys(apps)
    val dockApps = if (dockOn) dockKeys.mapNotNull { k -> apps.firstOrNull { it.key == k } } else emptyList()
    val gridApps = apps.filter { it.key !in hidden && (!dockOn || it.key !in dockKeys) }
    val backdrop = rememberLauncherBackdrop()
    LaunchedEffect(Prefs.lIconPack.value) {
        IconPacks.invalidate()
        AppRepository.load(ctx.applicationContext)
    }

    var searchOpen by remember { mutableStateOf(false) }
    var homeMenu by remember { mutableStateOf(false) }

    BackdropHost(backdrop, Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val cols = Prefs.lColumns.value.coerceIn(3, 6)
            val iconSize = Prefs.lIconSize.value.dp
            val labels = Prefs.lShowLabels.value
            val labelH = if (labels) (Prefs.lLabelSize.value * 1.5f + 6).dp else 0.dp
            val cellH = iconSize + labelH + 18.dp
            val reserved = 24.dp + 30.dp + (if (Prefs.lSearchBar.value) 46.dp else 0.dp) +
                (if (dockOn) iconSize + 40.dp else 0.dp) + 60.dp
            val usable = maxHeight - reserved
            val rows = max(1, (usable / cellH).toInt())
            val clockOn = Prefs.lClock.value
            val clockRows = if (clockOn) ceil((170f + 12f) / cellH.value).toInt() else 0
            val firstCap = max(cols, (rows - clockRows) * cols)
            val perPage = rows * cols
            val pages = mutableListOf<List<AppEntry>>()
            if (gridApps.isEmpty()) pages.add(emptyList()) else {
                pages.add(gridApps.take(firstCap))
                var rest = gridApps.drop(firstCap)
                while (rest.isNotEmpty()) {
                    pages.add(rest.take(perPage)); rest = rest.drop(perPage)
                }
            }
            val library = Prefs.lLibrary.value
            val pageCount = pages.size + if (library) 1 else 0
            val pager = rememberPagerState { pageCount }

            LaunchedEffect(homePressed) {
                if (homePressed > 0) {
                    if (searchOpen) searchOpen = false
                    else if (pager.currentPage != 0) pager.animateScrollToPage(0)
                }
            }
            BackHandler(enabled = searchOpen || homeMenu) {
                searchOpen = false; homeMenu = false
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f },
                                onVerticalDrag = { _, amount -> total += amount },
                                onDragEnd = {
                                    if (total > 140f) {
                                        if (Prefs.lSwipeDown.value == "recherche") searchOpen = true
                                        else SystemTools.expandNotifications(ctx)
                                    } else if (total < -140f) {
                                        searchOpen = true
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { homeMenu = true })
                        }
                ) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                        if (page < pages.size) {
                            AppPage(
                                apps = pages[page], cols = cols, showClock = page == 0 && clockOn,
                                cellH = cellH, iconSize = iconSize, allApps = apps
                            )
                        } else {
                            AppLibrary(apps.filter { it.key !in hidden }, allApps = apps)
                        }
                    }
                    DropdownMenu(expanded = homeMenu, onDismissRequest = { homeMenu = false }) {
                        DropdownMenuItem(text = { Text("Personnaliser l'écran d'accueil") }, onClick = {
                            homeMenu = false; SystemTools.openMaison(ctx, "ecran")
                        })
                        DropdownMenuItem(text = { Text("Changer le fond d'écran") }, onClick = {
                            homeMenu = false; SystemTools.openMaison(ctx, "style")
                        })
                        DropdownMenuItem(text = { Text("Ouvrir Maison Chahyn") }, onClick = {
                            homeMenu = false; SystemTools.openMaison(ctx, "accueil")
                        })
                    }
                }

                PageDots(pager, pageCount, library)
                if (Prefs.lSearchBar.value) {
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                        LiquidGlass(shape = CircleShape, modifier = Modifier.clickable { searchOpen = true }) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Rechercher", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                if (dockOn) {
                    LiquidGlass(
                        shape = RoundedCornerShape(34.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dockApps.isEmpty()) {
                                Text(
                                    "Appui long sur une appli → « Ajouter au Dock »",
                                    color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
                                    modifier = Modifier.height(iconSize).padding(top = iconSize / 3)
                                )
                            }
                            dockApps.forEach { app ->
                                AppIcon(app, iconSize, showLabel = false, allApps = apps, inDock = true)
                            }
                        }
                    }
                } else Spacer(Modifier.height(12.dp))
            }

            LauncherUi.customize.value?.let { app ->
                AppCustomizeDialog(app) { LauncherUi.customize.value = null }
            }

            AnimatedVisibility(
                visible = searchOpen,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f)
            ) {
                SearchOverlay(apps, onClose = { searchOpen = false })
            }
        }
    }
}

@Composable
fun PageDots(pager: PagerState, count: Int, library: Boolean) {
    if (count <= 1) {
        Spacer(Modifier.height(8.dp)); return
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        LiquidGlass(shape = CircleShape, blur = 12.dp, tintAlpha = 0.10f, highlight = false, border = 0.2f) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(count) { i ->
                    val sel = pager.currentPage == i
                    Box(
                        Modifier
                            .size(if (library && i == count - 1) 7.dp else 7.dp)
                            .clip(if (library && i == count - 1) RoundedCornerShape(2.dp) else CircleShape)
                            .background(Color.White.copy(alpha = if (sel) 1f else 0.4f))
                    )
                }
            }
        }
    }
}

@Composable
fun AppPage(apps: List<AppEntry>, cols: Int, showClock: Boolean, cellH: Dp, iconSize: Dp, allApps: List<AppEntry>) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (showClock) {
            ClockWidget()
            Spacer(Modifier.height(12.dp))
        }
        apps.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().height(cellH), verticalAlignment = Alignment.Top) {
                row.forEach { app ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        AppIcon(app, iconSize, showLabel = Prefs.lShowLabels.value, allApps = allApps)
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(app: AppEntry, size: Dp, showLabel: Boolean, allApps: List<AppEntry>, inDock: Boolean = false) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = IconShapes.of(Prefs.lIconShape.value)
    val style = Prefs.lIconStyle.value

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 22.dp)) {
        Box {
            Box(
                Modifier
                    .size(size)
                    .scale(if (pressed) 0.92f else 1f)
                    .onGloballyPositioned { bounds = it.boundsInWindow() }
                    .shadow(if (IconStyles.hasShadow(style)) 5.dp else 0.dp, shape)
                    .clip(shape)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { launchApp(ctx, view, app, bounds) },
                        onLongClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            menu = true
                        }
                    )
            ) {
                StyledIcon(app, size, shape = shape, style = style)
                if (pressed) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Personnaliser l'icône et le nom") }, onClick = {
                    menu = false; LauncherUi.customize.value = app
                })
                val dockKeys = Dock.keys(allApps)
                if (app.key in dockKeys) {
                    DropdownMenuItem(text = { Text("Retirer du Dock") }, onClick = {
                        menu = false; Dock.remove(allApps, app.key)
                    })
                } else {
                    DropdownMenuItem(text = { Text("Ajouter au Dock") }, onClick = {
                        menu = false
                        if (!Dock.add(allApps, app.key)) ctx.toast("Le Dock est plein (${Dock.MAX} applis max)")
                    })
                }
                if (!inDock) {
                    DropdownMenuItem(text = { Text("Masquer l'appli") }, onClick = {
                        menu = false
                        Prefs.lHidden.value = Prefs.lHidden.value + app.key
                        ctx.toast("${IconOverrides.label(app)} masquée — réaffiche-la dans Maison › Écran")
                    })
                }
                DropdownMenuItem(text = { Text("Infos sur l'appli") }, onClick = {
                    menu = false; SystemTools.appDetails(ctx, app.pkg)
                })
                DropdownMenuItem(text = { Text("Désinstaller") }, onClick = {
                    menu = false; SystemTools.uninstall(ctx, app.pkg)
                })
            }
        }
        if (showLabel) {
            Text(
                IconOverrides.label(app),
                color = Color.White,
                fontSize = Prefs.lLabelSize.value.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp),
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), 5f))
            )
        }
    }
}

@Composable
fun ClockWidget() {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            battery = try {
                ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } catch (e: Throwable) {
                -1
            }
            delay(1000)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.FRENCH).format(now) }
    val date = remember(now) {
        SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(now).replaceFirstChar { it.uppercase() }
    }
    LiquidGlass(Modifier.fillMaxWidth().height(170.dp)) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text(date, color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(time, color = Color.White, fontSize = 66.sp, fontWeight = FontWeight.Light, lineHeight = 70.sp)
            if (battery >= 0) Text("🔋 $battery %", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        }
    }
}

@Composable
fun AppLibrary(apps: List<AppEntry>, allApps: List<AppEntry>) {
    val groups = remember(apps) {
        apps.groupBy { AppRepository.categoryName(it.category) }.toSortedMap()
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Bibliothèque d'apps", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 1f), 6f))
            )
        }
        items(groups.entries.toList(), key = { it.key }) { (name, list) ->
            LiquidGlass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    list.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                            row.forEach { app ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                    AppIcon(app, 52.dp, showLabel = true, allApps = allApps)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun SearchOverlay(apps: List<AppEntry>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        try {
            focus.requestFocus()
        } catch (_: Throwable) {
        }
    }
    val results = remember(q, apps) {
        if (q.isBlank()) apps.take(8)
        else apps.filter { IconOverrides.label(it).contains(q.trim(), ignoreCase = true) || it.label.contains(q.trim(), ignoreCase = true) }
    }
    LiquidGlass(
        Modifier.fillMaxSize(),
        shape = RectangleShape, blur = 40.dp, tintAlpha = 0.35f, dark = true, border = 0f, highlight = false
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiquidGlass(shape = CircleShape, modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (q.isEmpty()) Text("Rechercher", color = Color.White.copy(alpha = 0.6f), fontSize = 17.sp)
                            BasicTextField(
                                value = q, onValueChange = { q = it }, singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                                cursorBrush = SolidColor(Color.White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    results.firstOrNull()?.let { launchApp(ctx, view, it, Rect.Zero); onClose() }
                                }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text("Annuler", color = Color.White, fontSize = 16.sp, modifier = Modifier.clickable { onClose() })
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (q.isBlank()) "Suggestions Siri… façon Maison" else "Applications",
                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f)) {
                items(results, key = { it.key }) { app ->
                    Box(Modifier.padding(bottom = 14.dp), contentAlignment = Alignment.TopCenter) {
                        AppIcon(app, 56.dp, showLabel = true, allApps = apps)
                    }
                }
            }
            if (q.isNotBlank()) {
                LiquidGlass(Modifier.fillMaxWidth().clickable {
                    SystemTools.safeStart(ctx, Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q))
                    onClose()
                }) {
                    Text(
                        "🔎  Rechercher « $q » sur le Web", color = Color.White, fontSize = 16.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
