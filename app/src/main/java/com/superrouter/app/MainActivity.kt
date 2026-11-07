package com.superrouter.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// ============================================================
//  MainActivity — 入口
// ============================================================
class MainActivity : ComponentActivity() {
    private val storagePerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                storagePerm.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
                catch (e: Exception) { try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (e2: Exception) { } }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (e: Exception) { }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            MaterialTheme(colorScheme = AppDark, typography = AppTypography) { RootScreen() }
        }
    }
}

// ============================================================
//  RootScreen — 决定手机/平板，套壁纸 + 抽屉 + 主界面
// ============================================================
@Composable
fun RootScreen(vm: ChatVM = viewModel()) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { UiState.load(ctx) }
    val form = UiState.forceForm ?: DeviceMode.of(ctx)

    val sessions by vm.sessions.collectAsState()
    val currentId by vm.currentId.collectAsState()
    val current = sessions.find { it.id == currentId }

    var drawerOpen by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var showRepo by remember { mutableStateOf(false) }
    var showTree by remember { mutableStateOf(false) }
    var showLan by remember { mutableStateOf(false) }
    var showCool by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showPalette by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        WallpaperLayer(form)

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                GlassSessionDrawer(
                    vm = vm,
                    currentId = currentId,
                    onClose = { drawerOpen = false },
                    onNew = { drawerOpen = false; showRepo = true },
                    onSettings = { drawerOpen = false; showSettings = true }
                )
            }
        ) {
            if (form == FormFactor.Tablet) {
                TabletScreen(
                    vm = vm,
                    current = current,
                    onNew = { showRepo = true },
                    onSettings = { showSettings = true },
                    onLan = { showLan = true },
                    onTree = { showTree = true },
                    onCool = { showCool = true },
                    onToken = { showToken = true },
                    onPalette = { showPalette = true }
                )
            } else {
                PhoneScreen(
                    vm = vm,
                    current = current,
                    onMenu = { drawerOpen = true },
                    onLan = { showLan = true },
                    onTree = { showTree = true },
                    onCool = { showCool = true },
                    onToken = { showToken = true }
                )
            }
        }
    }

    LaunchedEffect(drawerOpen) { if (drawerOpen) drawerState.open() else drawerState.close() }

    if (showCool) CoolDialog(vm) { showCool = false }
    if (showLan) LanDialog(vm) { showLan = false }
    if (showToken) TokenDialog(vm) { showToken = false }
    if (showTree && current != null) TreeDialog(vm, current) { showTree = false }
    showDelete?.let { sid -> DeleteDialog(vm, sid) { showDelete = null } }
    if (showRepo) RepoDialog(vm) { showRepo = false }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
    CommandPalette(
        vm = vm,
        visible = showPalette,
        onDismiss = { showPalette = false },
        onNewProject = { showRepo = true },
        onLan = { showLan = true },
        onTree = { showTree = true },
        onCool = { showCool = true },
        onToken = { showToken = true },
        onSettings = { showSettings = true },
        onTogglePhone = { UiState.forceForm = FormFactor.Phone },
        onToggleTablet = { UiState.forceForm = FormFactor.Tablet }
    )
}

// ============================================================
//  PhoneScreen — 手机模式主界面
// ============================================================
@Composable
fun PhoneScreen(
    vm: ChatVM,
    current: Session?,
    onMenu: () -> Unit,
    onLan: () -> Unit,
    onTree: () -> Unit,
    onCool: () -> Unit,
    onToken: () -> Unit,
    onPalette: () -> Unit,
    onSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PhoneHeader(current, onMenu, onLan, onTree, onCool, onPalette)
        Spacer(Modifier.height(6.dp))
        InsightDeck(vm)
        Spacer(Modifier.height(8.dp))
        QuickActionsRow(vm, onLan, onTree, onCool, onSettings)
        Spacer(Modifier.height(8.dp))
        ChatList(vm, current, Modifier.weight(1f))
        GlassStatusBar(vm, onToken)
        GlassInputBar(vm)
    }
}

// ============================================================
//  PhoneHeader — 玻璃顶栏 + Agent Orb
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneHeader(
    current: Session?,
    onMenu: () -> Unit,
    onLan: () -> Unit,
    onTree: () -> Unit,
    onCool: () -> Unit,
    onPalette: () -> Unit
) {
    val running = current?.runs?.lastOrNull()?.running == true
    val pendingWait = current?.pendingPrompt != null && !running
    val lastFailed = current?.runs?.lastOrNull()?.finalReply?.startsWith("[网络错误]") == true

    Box(
        Modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        GlassBox(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.xl),
            tint = Color.White,
            tintAlpha = 0.06f,
            strokeAlpha = 0.35f,
            flowEnabled = false
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenu, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.Menu, null, tint = Gold, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))

                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(GoldGradient)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onPalette() }
                        ),
                    contentAlignment = Alignment.Center
                ) { Text("S", color = Color(0xFF1A1710), fontSize = 18.sp, fontWeight = FontWeight.Black) }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "SUPERROUTER", color = Gold, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            current?.title ?: "就绪",
                            color = DeepSeekText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        AgentOrb(
                            when {
                                running -> OrbState.Running
                                lastFailed -> OrbState.Failed
                                pendingWait -> OrbState.Waiting
                                else -> OrbState.Idle
                            }, size = 8.dp
                        )
                    }
                }

                Box(Modifier.size(34.dp).clip(CircleShape).background(DeepSeekSurfaceHigh.copy(alpha = 0.55f)).clickable { onCool() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Timer, null, tint = DeepSeekAccent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(34.dp).clip(CircleShape).background(DeepSeekSurfaceHigh.copy(alpha = 0.55f)).clickable { onLan() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Wifi, null, tint = DeepSeekAccent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(34.dp).clip(CircleShape).background(DeepSeekSurfaceHigh.copy(alpha = 0.55f)).clickable { onTree() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountTree, null, tint = DeepSeekAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

enum class OrbState { Idle, Running, Waiting, Failed }

@Composable
fun AgentOrb(state: OrbState, size: Dp = 8.dp) {
    val inf = rememberInfiniteTransition(label = "orb")
    val (color, period) = when (state) {
        OrbState.Idle -> DeepSeekMuted to 2200
        OrbState.Running -> StatusRun to 800
        OrbState.Waiting -> StatusWait to 1400
        OrbState.Failed -> StatusFail to 1000
    }
    val a by inf.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period), RepeatMode.Reverse),
        label = "a"
    )
    Box(Modifier.size(size * 2).clip(CircleShape).background(color.copy(alpha = 0.22f * a)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

// ============================================================
//  ChatList — 消息时间线
// ============================================================
@Composable
fun ChatList(vm: ChatVM, current: Session?, modifier: Modifier = Modifier) {
    val activeRun by vm.activeRun.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(current?.runs?.size, activeRun?.steps?.size) {
        val count = (current?.runs?.size ?: 0) + (if (activeRun != null) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (current == null || (current.runs.isEmpty() && activeRun == null)) {
            item { EmptyHero() }
        } else {
            items(current.runs, key = { it.id }) { run -> TimelineRunCard(run) }
            activeRun?.let { run -> item { TimelineRunCard(run) } }
        }
    }
}

@Composable
fun EmptyHero() {
    Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassBox(
                shape = RoundedCornerShape(24.dp),
                tintAlpha = 0.10f, strokeAlpha = 0.5f,
                modifier = Modifier.size(88.dp)
            ) {
                Icon(Icons.Default.Bolt, null, tint = GoldGlow, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("准备就绪", style = TextStyle(brush = GoldGradient, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
            Spacer(Modifier.height(6.dp))
            Text("输入任务，AI 立即执行", color = DeepSeekMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun TimelineRunCard(run: AgentRun) {
    var expanded by remember { mutableStateOf(run.running) }
    val ctx = LocalContext.current

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GlassBox(
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                tint = Gold.copy(alpha = 0.35f), tintAlpha = 0.14f,
                strokeAlpha = 0.45f, flowEnabled = false
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (!run.chineseName.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(GoldGradient), contentAlignment = Alignment.Center) {
                                Text("AI", color = Color(0xFF1A1710), fontSize = 7.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(run.chineseName!!, color = Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(run.task, color = DeepSeekText, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(30.dp)) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(GoldGradient), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SmartToy, null, tint = Color(0xFF1A1710), modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(1.dp))
                if (expanded && run.steps.isNotEmpty()) {
                    Box(Modifier.width(1.dp).height((run.steps.size * 24).dp.coerceAtLeast(8.dp)).background(GoldGradient.copy(alpha = 0.4f)))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (run.running) "执行中 · ${run.steps.size} 步" else "已完成 · ${run.steps.size} 步",
                        color = DeepSeekMuted, fontSize = 11.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = DeepSeekMuted, modifier = Modifier.size(16.dp))
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        run.steps.forEach { step ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(7.dp).clip(CircleShape)
                                        .background(if (step.success) DeepSeekSuccess else DeepSeekError)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(step.actionName.take(40), color = DeepSeekMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                if (run.downloadProgress > 0f && run.downloadProgress < 1f) {
                    Spacer(Modifier.height(6.dp))
                    Text("下载 ${(run.downloadProgress * 100).toInt()}%", color = Gold, fontSize = 10.sp)
                    Spacer(Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = run.downloadProgress,
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                        color = Gold, trackColor = DeepSeekSurfaceHigh
                    )
                }

                if (!run.running && run.finalReply != null) {
                    Spacer(Modifier.height(8.dp))
                    GlassBox(
                        shape = RoundedCornerShape(Radius.lg),
                        tint = Color.White, tintAlpha = 0.06f, strokeAlpha = 0.3f, flowEnabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Box(
                                    Modifier.size(24.dp).clip(CircleShape)
                                        .background(DeepSeekSurfaceHigh.copy(alpha = 0.6f))
                                        .clickable {
                                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("AI", run.finalReply))
                                            android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.ContentCopy, null, tint = Gold, modifier = Modifier.size(12.dp)) }
                            }
                            Text(run.finalReply, color = DeepSeekText, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  GlassStatusBar — 状态按钮 + 记忆条
// ============================================================
@Composable
fun GlassStatusBar(vm: ChatVM, onToken: () -> Unit) {
    val deepThink by vm.deepThink.collectAsState()
    val smartSearch by vm.smartSearch.collectAsState()
    val overlayMode by vm.overlayMode.collectAsState()
    val cooldownMin by vm.cooldownMinMs.collectAsState()
    val cooldownMax by vm.cooldownMaxMs.collectAsState()
    val maxSteps by vm.maxSteps.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val currentId by vm.currentId.collectAsState()
    val usedTokens = sessions.find { it.id == currentId }?.accumulatedTokens ?: 0L
    val maxTokens = vm.compressionThreshold.collectAsState().value
    val pct = if (maxTokens > 0) (usedTokens.toFloat() / maxTokens).coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassToggle("深度", Icons.Default.Psychology, deepThink) { vm.setDeepThink(!deepThink) }
            GlassToggle("搜索", Icons.Default.Search, smartSearch) { vm.setSmartSearch(!smartSearch) }
            Spacer(Modifier.weight(1f))
            Text("%.1f~%.1fs".format(cooldownMin / 1000.0, cooldownMax / 1000.0), color = DeepSeekMuted, fontSize = 9.sp)
            Spacer(Modifier.width(4.dp))
            OverlaySeg(overlayMode, onChange = { vm.setOverlayMode(it) })
        }
        Spacer(Modifier.height(4.dp))
        val risk = remember { RiskGuard.snapshot() }
        if (risk.failStreak > 0 || risk.lowProfile || risk.rateLimitedRemainSec > 0 || risk.powSpike) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(
                    when {
                        risk.rateLimitedRemainSec > 0 -> DeepSeekError
                        risk.powSpike -> DeepSeekWarning
                        risk.lowProfile -> DeepSeekWarning
                        else -> DeepSeekError
                    }
                ))
                Spacer(Modifier.width(4.dp))
                Text(
                    when {
                        risk.rateLimitedRemainSec > 0 -> "429 冷却 ${risk.rateLimitedRemainSec}s"
                        risk.powSpike -> "PoW 难度突增 · 已降速"
                        risk.lowProfile -> "低调模式 · 连败${risk.failStreak}"
                        else -> "风控预警 · 连败${risk.failStreak}"
                    },
                    color = if (risk.rateLimitedRemainSec > 0) DeepSeekError else DeepSeekWarning,
                    fontSize = 9.sp
                )
                if (risk.lowActivityHour) {
                    Spacer(Modifier.width(6.dp))
                    Text("凌晨低频", color = DeepSeekDim, fontSize = 9.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().clickable { onToken() }, verticalAlignment = Alignment.CenterVertically) {
            Text("记忆", color = DeepSeekMuted, fontSize = 9.sp)
            Spacer(Modifier.width(6.dp))
            LinearProgressIndicator(
                progress = pct,
                modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)),
                color = if (pct > 0.8f) DeepSeekError else Gold,
                trackColor = DeepSeekSurfaceHigh.copy(alpha = 0.6f)
            )
            Spacer(Modifier.width(6.dp))
            Text("${(pct * 100).toInt()}% · ${maxSteps}步", color = DeepSeekMuted, fontSize = 9.sp)
        }
    }
}

@Composable
fun GlassToggle(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Radius.pill))
            .background(if (on) Gold.copy(alpha = 0.22f) else DeepSeekSurfaceHigh.copy(alpha = 0.55f))
            .border(1.dp, if (on) Gold.copy(alpha = 0.65f) else DeepSeekBorder, RoundedCornerShape(Radius.pill))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (on) Gold else DeepSeekMuted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (on) Gold else DeepSeekMuted, fontSize = 10.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun OverlaySeg(current: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Radius.pill))
            .background(DeepSeekSurfaceHigh.copy(alpha = 0.6f))
            .border(1.dp, DeepSeekBorder, RoundedCornerShape(Radius.pill))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(Icons.Default.OpenInNew, Icons.Default.RemoveRedEye, Icons.Default.VisibilityOff).forEachIndexed { i, icon ->
            val on = i == current
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (on) GoldGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onChange(i) },
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = if (on) Color(0xFF1A1710) else DeepSeekMuted, modifier = Modifier.size(11.dp)) }
        }
    }
}

// ============================================================
//  GlassInputBar — 玻璃输入胶囊
// ============================================================
@Composable
fun GlassInputBar(vm: ChatVM) {
    val running by vm.running.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val currentId by vm.currentId.collectAsState()
    val current = sessions.find { it.id == currentId }
    var input by remember { mutableStateOf("") }

    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        GlassBox(
            shape = RoundedCornerShape(Radius.xl),
            tint = Color.White,
            tintAlpha = if (input.isNotBlank()) 0.12f else 0.06f,
            strokeAlpha = if (input.isNotBlank()) 0.55f else 0.32f,
            flowEnabled = input.isNotBlank(),
            modifier = Modifier.weight(1f)
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = TextStyle(color = DeepSeekText, fontSize = 15.sp),
                cursorBrush = SolidColor(Gold),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                maxLines = 5,
                decorationBox = { inner ->
                    if (input.isEmpty()) Text("要让 AI 做什么？", color = DeepSeekMuted, fontSize = 15.sp)
                    inner()
                }
            )
        }
        Spacer(Modifier.width(8.dp))
        if (running) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(DeepSeekError).clickable { vm.stopCurrent() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        } else if (current?.pendingPrompt != null) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(DeepSeekSurfaceHigh).clickable { vm.skipCurrent() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SkipNext, null, tint = Gold, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(50.dp).clip(CircleShape).background(DeepSeekSuccess).clickable { vm.resumeLast() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        } else {
            val ok = input.isNotBlank()
            Box(
                Modifier.size(50.dp).clip(CircleShape)
                    .background(if (ok) GoldGradient else Brush.linearGradient(listOf(DeepSeekSurfaceHigh, DeepSeekSurfaceHigh)))
                    .clickable { if (ok) { vm.send(input); input = "" } },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.ArrowUpward, null, tint = if (ok) Color(0xFF1A1710) else DeepSeekMuted, modifier = Modifier.size(24.dp)) }
        }
    }
}

// ============================================================
//  GlassSessionDrawer — 玻璃抽屉
// ============================================================
@Composable
fun GlassSessionDrawer(vm: ChatVM, currentId: String?, onClose: () -> Unit, onNew: () -> Unit, onSettings: () -> Unit) {
    val sessions by vm.sessions.collectAsState()
    Box(Modifier.fillMaxWidth(0.82f).fillMaxHeight()) {
        Box(Modifier.fillMaxSize().background(DeepSeekBg.copy(alpha = 0.85f)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.04f)))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(GoldGradient), contentAlignment = Alignment.Center) {
                        Text("S", color = Color(0xFF1A1710), fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("SUPERROUTER", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                        Text("全自动 AI 代理", color = DeepSeekMuted, fontSize = 10.sp)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(GoldGradient.copy(alpha = 0.25f)))
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(Radius.md)).background(GoldGradient).clickable { onNew() },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Add, null, tint = Color(0xFF1A1710), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("新建项目", color = Color(0xFF1A1710), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "会话", color = DeepSeekMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(sessions.reversed()) { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(if (s.id == currentId) Gold.copy(alpha = 0.10f) else Color.Transparent)
                            .border(1.dp, if (s.id == currentId) Gold.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(Radius.md))
                            .clickable { vm.selectSession(s.id); onClose() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.id == currentId) Gold else DeepSeekBorder))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title,
                                color = if (s.id == currentId) DeepSeekText else DeepSeekMuted,
                                fontSize = 14.sp,
                                fontWeight = if (s.id == currentId) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("${s.repoName} · ${s.accumulatedTokens}t", color = DeepSeekMuted, fontSize = 10.sp)
                        }
                        if (s.isProtected) Icon(Icons.Default.Lock, null, tint = Gold, modifier = Modifier.size(12.dp))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))
                        .background(DeepSeekSurfaceHigh.copy(alpha = 0.6f))
                        .border(1.dp, DeepSeekBorder, RoundedCornerShape(Radius.md))
                        .clickable { onSettings() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, null, tint = Gold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("外观与壁纸", color = DeepSeekText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = DeepSeekMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ============================================================
//  Dialogs（玻璃配色，功能保留）
// ============================================================
@Composable
fun CoolDialog(vm: ChatVM, onDismiss: () -> Unit) {
    val minMs by vm.cooldownMinMs.collectAsState()
    val maxMs by vm.cooldownMaxMs.collectAsState()
    var minStr by remember { mutableStateOf("%.2f".format(minMs / 1000.0)) }
    var maxStr by remember { mutableStateOf("%.2f".format(maxMs / 1000.0)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("冷却区间", color = Gold, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("每步执行前在区间内随机等待，精确到 0.01 秒", color = DeepSeekMuted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { GlassInputField(minStr, { minStr = it }, "0.00", true) }
                    Text(" ~ ", color = DeepSeekMuted, fontSize = 16.sp)
                    Box(Modifier.weight(1f)) { GlassInputField(maxStr, { maxStr = it }, "3.00", true) }
                    Spacer(Modifier.width(8.dp))
                    Text("秒", color = DeepSeekMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("0~0", "0.5~3", "1~5", "3~10").forEach { p ->
                        val parts = p.split("~")
                        Box(Modifier.clip(RoundedCornerShape(Radius.sm))
                            .background(DeepSeekSurfaceHigh)
                            .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(Radius.sm))
                            .clickable { minStr = parts[0]; maxStr = parts[1] }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text(p, color = Gold, fontSize = 11.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setCooldownRange((minStr.toDoubleOrNull() ?: 0.0) * 1000, (maxStr.toDoubleOrNull() ?: 0.0) * 1000)
                onDismiss()
            }) { Text("保存", color = Gold, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DeepSeekMuted) } },
        containerColor = DeepSeekSurface.copy(alpha = 0.95f)
    )
}

@Composable
fun GlassInputField(value: String, onChange: (String) -> Unit, placeholder: String, numeric: Boolean = false) {
    Surface(shape = RoundedCornerShape(Radius.sm), color = DeepSeekSurfaceHigh, border = BorderStroke(1.dp, DeepSeekBorder)) {
        BasicTextField(
            value = value, onValueChange = onChange,
            textStyle = TextStyle(color = DeepSeekText, fontSize = 14.sp),
            cursorBrush = SolidColor(Gold),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, color = DeepSeekMuted, fontSize = 14.sp); inner() }
        )
    }
}

@Composable
fun LanDialog(vm: ChatVM, onDismiss: () -> Unit) {
    val ip = remember { LanServer.localIp() }
    val port = LanServer.PORT
    val key by vm.lanKey.collectAsState()
    val devices by LanServer.devices.collectAsState()
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5000); now = System.currentTimeMillis() } }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.xl),
            color = DeepSeekSurface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, Gold.copy(alpha = 0.45f)),
            modifier = Modifier.padding(20.dp).fillMaxWidth().animateContentSize()
        ) {
            Column(Modifier.padding(20.dp).heightIn(max = 560.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(GoldGradient), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Wifi, null, tint = Color(0xFF1A1710), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("局域网桥接", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Key 调用即用", color = DeepSeekMuted, fontSize = 10.sp)
                    }
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (LanServer.isRunning()) DeepSeekSuccess else DeepSeekError))
                    Spacer(Modifier.width(6.dp))
                    Text(if (LanServer.isRunning()) "在线" else "离线", color = if (LanServer.isRunning()) DeepSeekSuccess else DeepSeekError, fontSize = 11.sp)
                }
                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(Radius.sm), color = DeepSeekCodeBg, border = BorderStroke(1.dp, DeepSeekBorder)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, null, tint = Gold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("http://$ip:$port", color = DeepSeekText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("lan", "http://$ip:$port"))
                            android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.ContentCopy, null, tint = DeepSeekMuted, modifier = Modifier.size(12.dp)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(Radius.sm), color = DeepSeekSurfaceHigh, border = BorderStroke(1.dp, Gold.copy(alpha = 0.3f))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, null, tint = Gold, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("API KEY", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.refreshLanKey() }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(22.dp)) {
                                Text("刷新", color = Gold, fontSize = 10.sp)
                            }
                        }
                        Text(key, color = Gold, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("X-Api-Key: <KEY>", color = DeepSeekMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("设备 (${devices.size})", color = DeepSeekText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                if (devices.isEmpty()) {
                    Text("暂无设备", color = DeepSeekMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    LazyColumn(Modifier.heightIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(devices, key = { it.ip }) { d ->
                            Surface(shape = RoundedCornerShape(Radius.sm), color = DeepSeekSurfaceHigh, border = BorderStroke(1.dp, if (d.banned) DeepSeekError.copy(alpha = 0.4f) else DeepSeekBorder)) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(26.dp).clip(CircleShape).background((if (d.banned) DeepSeekError else Gold).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                        Icon(if (d.banned) Icons.Default.Block else Icons.Default.Smartphone, null, tint = if (d.banned) DeepSeekError else Gold, modifier = Modifier.size(13.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(d.ip, color = if (d.banned) DeepSeekError else DeepSeekText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                        val ago = ((now - d.lastSeen) / 1000).coerceAtLeast(0)
                                        Text("${d.requests} 次 · ${ago}s前", color = DeepSeekMuted, fontSize = 9.sp)
                                    }
                                    TextButton(onClick = { if (d.banned) LanServer.unkick(d.ip) else LanServer.kick(d.ip) }) {
                                        Text(if (d.banned) "恢复" else "踢出", color = if (d.banned) DeepSeekSuccess else DeepSeekError, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭", color = DeepSeekMuted, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun TokenDialog(vm: ChatVM, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(vm.compressionThreshold.value.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记忆阈值", color = Gold, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("达到此 Token 数后自动压缩并迁移新会话", color = DeepSeekMuted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                GlassInputField(v, { v = it }, "40000", true)
            }
        },
        confirmButton = {
            TextButton(onClick = { v.toLongOrNull()?.let { vm.setCompressionThreshold(it) }; onDismiss() }) {
                Text("保存", color = Gold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DeepSeekMuted) } },
        containerColor = DeepSeekSurface.copy(alpha = 0.95f)
    )
}

@Composable
fun TreeDialog(vm: ChatVM, current: Session, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("项目结构 · ${current.repoName}", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Surface(color = DeepSeekCodeBg, shape = RoundedCornerShape(Radius.sm), border = BorderStroke(1.dp, DeepSeekBorder), modifier = Modifier.fillMaxWidth().height(400.dp)) {
                val nodes = remember(current.repoName, current.runs.size) { vm.getProjectTree(current.repoName) }
                if (nodes.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("空目录", color = DeepSeekMuted) }
                else ProjectTreeView(nodes, Modifier.fillMaxSize().padding(8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = Gold) } },
        containerColor = DeepSeekSurface.copy(alpha = 0.95f)
    )
}

@Composable
fun DeleteDialog(vm: ChatVM, sessionId: String, onDismiss: () -> Unit) {
    var del by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除会话", color = Gold) },
        text = {
            Column {
                Text("同时删除本地代码文件夹？", color = DeepSeekText, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.clickable { del = !del }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = del, onCheckedChange = { del = it }, colors = CheckboxDefaults.colors(checkedColor = Gold))
                    Text("删除本地文件", color = if (del) DeepSeekError else DeepSeekMuted, fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.deleteSessionWithFiles(sessionId, del); onDismiss() }) { Text("确认", color = DeepSeekError) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DeepSeekMuted) } },
        containerColor = DeepSeekSurface.copy(alpha = 0.95f)
    )
}

@Composable
fun RepoDialog(vm: ChatVM, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建项目", color = Gold, fontWeight = FontWeight.Bold) },
        text = { GlassInputField(name, { name = it }, "仓库名（英文小写）") },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) { vm.createNewSession(name.trim()); onDismiss() } }) {
                Text("创建", color = Gold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DeepSeekMuted) } },
        containerColor = DeepSeekSurface.copy(alpha = 0.95f)
    )
}


// ============================================================
//  SettingsDialog — 外观与壁纸（系统相册接入）
// ============================================================
@Composable
fun SettingsDialog(vm: ChatVM, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val form = UiState.forceForm ?: DeviceMode.of(ctx)

    var editForm by remember { mutableStateOf(form) }
    var tick by remember { mutableStateOf(0) }

    val pickPhoneStatic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = WallpaperStore.import(ctx, uri, WallpaperStore.Form.Phone, WallpaperStore.Kind.Static)
            if (path != null) {
                UiState.phoneStaticPath = path
                UiState.phoneKind = 1
                UiState.save(ctx); tick++
            }
        }
    }
    val pickPhoneDynamic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = WallpaperStore.import(ctx, uri, WallpaperStore.Form.Phone, WallpaperStore.Kind.Dynamic)
            if (path != null) {
                UiState.phoneDynamicPath = path
                UiState.phoneKind = 2
                UiState.save(ctx); tick++
            }
        }
    }
    val pickTabletStatic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = WallpaperStore.import(ctx, uri, WallpaperStore.Form.Tablet, WallpaperStore.Kind.Static)
            if (path != null) {
                UiState.tabletStaticPath = path
                UiState.tabletKind = 1
                UiState.save(ctx); tick++
            }
        }
    }
    val pickTabletDynamic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = WallpaperStore.import(ctx, uri, WallpaperStore.Form.Tablet, WallpaperStore.Kind.Dynamic)
            if (path != null) {
                UiState.tabletDynamicPath = path
                UiState.tabletKind = 2
                UiState.save(ctx); tick++
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.xl),
            color = DeepSeekSurface.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, Gold.copy(alpha = 0.45f)),
            modifier = Modifier.padding(20.dp).fillMaxWidth().heightIn(max = 640.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(GoldGradient), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Palette, null, tint = Color(0xFF1A1710), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("外观与壁纸", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("手机 / 平板壁纸独立配置", color = DeepSeekMuted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = DeepSeekMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.pill))
                        .background(DeepSeekSurfaceHigh).padding(3.dp)
                ) {
                    listOf("手机" to FormFactor.Phone, "平板" to FormFactor.Tablet).forEach { (label, f) ->
                        val on = editForm == f
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(Radius.pill))
                                .background(if (on) GoldGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                .clickable { editForm = f }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (on) Color(0xFF1A1710) else DeepSeekMuted, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        val curKind = if (editForm == FormFactor.Phone) UiState.phoneKind else UiState.tabletKind
                        Text("壁纸类型", color = DeepSeekMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(DeepSeekSurfaceHigh).padding(3.dp)) {
                            listOf(0 to "纯色", 1 to "静态图", 2 to "动态").forEach { (k, label) ->
                                val on = curKind == k
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(Radius.xs))
                                        .background(if (on) Gold.copy(alpha = 0.22f) else Color.Transparent)
                                        .border(1.dp, if (on) Gold.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(Radius.xs))
                                        .clickable {
                                            if (editForm == FormFactor.Phone) UiState.phoneKind = k else UiState.tabletKind = k
                                            UiState.save(ctx); tick++
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (on) Gold else DeepSeekMuted, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    item {
                        WallpaperSlot(
                            label = "静态图",
                            path = if (editForm == FormFactor.Phone) UiState.phoneStaticPath else UiState.tabletStaticPath,
                            isVideo = false,
                            onPick = { if (editForm == FormFactor.Phone) pickPhoneStatic.launch("image/*") else pickTabletStatic.launch("image/*") },
                            onClear = {
                                if (editForm == FormFactor.Phone) { UiState.phoneStaticPath = null; WallpaperStore.remove(WallpaperStore.Form.Phone, WallpaperStore.Kind.Static) }
                                else { UiState.tabletStaticPath = null; WallpaperStore.remove(WallpaperStore.Form.Tablet, WallpaperStore.Kind.Static) }
                                UiState.save(ctx); tick++
                            },
                            tick = tick
                        )
                    }

                    item {
                        WallpaperSlot(
                            label = "动态视频",
                            path = if (editForm == FormFactor.Phone) UiState.phoneDynamicPath else UiState.tabletDynamicPath,
                            isVideo = true,
                            onPick = { if (editForm == FormFactor.Phone) pickPhoneDynamic.launch("video/*") else pickTabletDynamic.launch("video/*") },
                            onClear = {
                                if (editForm == FormFactor.Phone) { UiState.phoneDynamicPath = null; WallpaperStore.remove(WallpaperStore.Form.Phone, WallpaperStore.Kind.Dynamic) }
                                else { UiState.tabletDynamicPath = null; WallpaperStore.remove(WallpaperStore.Form.Tablet, WallpaperStore.Kind.Dynamic) }
                                UiState.save(ctx); tick++
                            },
                            tick = tick
                        )
                    }

                    item {
                        Text("屏幕宽度 ≥ 600dp 自动切换平板布局", color = DeepSeekMuted, fontSize = 10.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("完成", color = Gold, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun WallpaperSlot(
    label: String,
    path: String?,
    isVideo: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
    tick: Int
) {
    val file = remember(path, tick) { if (path.isNullOrBlank()) null else java.io.File(path) }
    val exists = file != null && file.exists() && file.length() > 0

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = DeepSeekMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            if (exists) {
                Text("${file!!.length() / 1024} KB", color = DeepSeekDim, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(22.dp)) {
                    Text("清除", color = DeepSeekError, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().height(74.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(DeepSeekSurfaceHigh)
                .border(1.dp, if (exists) Gold.copy(alpha = 0.4f) else DeepSeekBorder, RoundedCornerShape(Radius.sm))
                .clickable { onPick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (exists && !isVideo) {
                val bmp = remember(path, tick) {
                    try { android.graphics.BitmapFactory.decodeFile(path) } catch (_: Throwable) { null }
                }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(74.dp).padding(6.dp).clip(RoundedCornerShape(Radius.xs))
                    )
                } else {
                    Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
                        Icon(if (isVideo) Icons.Default.PlayCircle else Icons.Default.Image, null, tint = Gold, modifier = Modifier.size(28.dp))
                    }
                }
            } else {
                Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (!exists) Icons.Default.AddCircle else Icons.Default.PlayCircle,
                        null, tint = if (exists) Gold else DeepSeekMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (exists) (if (isVideo) "已设置动态视频" else "已设置静态图") else "未设置",
                    color = if (exists) DeepSeekText else DeepSeekMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    if (exists) "点击更换" else "点击从相册选择",
                    color = DeepSeekDim, fontSize = 10.sp
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = DeepSeekMuted, modifier = Modifier.size(16.dp).padding(end = 12.dp))
        }
    }
}
