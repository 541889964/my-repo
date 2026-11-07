package com.superrouter.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
//  CreativeKit — 洞察 / 快捷 / 命令面板 / 时间线装饰
// ============================================================

/** Insight Deck：横滑洞察卡组 */
@Composable
fun InsightDeck(vm: ChatVM, modifier: Modifier = Modifier) {
    val sessions by vm.sessions.collectAsState()
    val currentId by vm.currentId.collectAsState()
    val current = sessions.find { it.id == currentId }
    val runs = current?.runs ?: emptyList()
    val totalSteps = runs.sumOf { it.steps.size }
    val totalTokens = current?.accumulatedTokens ?: 0L
    val successRuns = runs.count { r -> r.steps.all { it.success } && r.steps.isNotEmpty() }
    val runCount = runs.size.coerceAtLeast(1)
    val successRate = if (runs.isEmpty()) 0 else (successRuns * 100 / runCount)
    val avgSteps = if (runs.isEmpty()) 0 else totalSteps / runCount

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { InsightCard("总步数", totalSteps.toString(), Icons.Default.Timeline, Gold) }
        item { InsightCard("会话数", runs.size.toString(), Icons.Default.Chat, StatusThink) }
        item { InsightCard("Token", if (totalTokens >= 1000) "%.1fk".format(totalTokens / 1000.0) else totalTokens.toString(), Icons.Default.Token, StatusWait) }
        item { InsightCard("成功率", "$successRate%", Icons.Default.CheckCircle, if (successRate >= 80) DeepSeekSuccess else DeepSeekWarning) }
        item { InsightCard("均步", avgSteps.toString(), Icons.Default.Insights, DeepSeekAccentAlt) }
    }
}

@Composable
fun InsightCard(label: String, value: String, icon: ImageVector, accent: Color) {
    GlassBox(
        shape = RoundedCornerShape(Radius.md),
        tint = accent, tintAlpha = 0.08f, strokeAlpha = 0.30f, flowEnabled = false,
        modifier = Modifier.width(96.dp).height(60.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, color = DeepSeekMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

/** Quick Actions：快捷动作行 */
@Composable
fun QuickActionsRow(
    vm: ChatVM,
    onLan: () -> Unit,
    onTree: () -> Unit,
    onCool: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickActionChip("局域网", Icons.Default.Wifi, onLan)
        QuickActionChip("项目", Icons.Default.AccountTree, onTree)
        QuickActionChip("冷却", Icons.Default.Timer, onCool)
        QuickActionChip("外观", Icons.Default.Palette, onSettings)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun QuickActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Radius.pill))
            .background(DeepSeekSurfaceHigh.copy(alpha = 0.55f))
            .border(1.dp, Gold.copy(alpha = 0.30f), RoundedCornerShape(Radius.pill))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Gold, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

/** Command Palette：全屏命令面板 */
@Composable
fun CommandPalette(
    vm: ChatVM,
    visible: Boolean,
    onDismiss: () -> Unit,
    onNewProject: () -> Unit,
    onLan: () -> Unit,
    onTree: () -> Unit,
    onCool: () -> Unit,
    onToken: () -> Unit,
    onSettings: () -> Unit,
    onTogglePhone: () -> Unit,
    onToggleTablet: () -> Unit
) {
    if (!visible) return
    val deepThink by vm.deepThink.collectAsState()
    val smartSearch by vm.smartSearch.collectAsState()
    val overlayMode by vm.overlayMode.collectAsState()

    data class Cmd(
        val title: String,
        val desc: String,
        val icon: ImageVector,
        val accent: Color,
        val action: () -> Unit
    )

    val commands = listOf(
        Cmd("新建项目", "创建新的 AI 会话", Icons.Default.AddCircle, Gold) { onNewProject(); onDismiss() },
        Cmd("切换深度思考", if (deepThink) "当前：开" else "当前：关", Icons.Default.Psychology, StatusThink) { vm.setDeepThink(!deepThink) },
        Cmd("切换智能搜索", if (smartSearch) "当前：开" else "当前：关", Icons.Default.Search, DeepSeekAccentAlt) { vm.setSmartSearch(!smartSearch) },
        Cmd("冷却设置", "区间随机等待", Icons.Default.Timer, DeepSeekWarning) { onCool(); onDismiss() },
        Cmd("记忆阈值", "上下文压缩", Icons.Default.Memory, StatusWait) { onToken(); onDismiss() },
        Cmd("局域网", "8099 网关", Icons.Default.Wifi, DeepSeekSuccess) { onLan(); onDismiss() },
        Cmd("项目结构", "查看文件树", Icons.Default.AccountTree, DeepSeekAccentAlt) { onTree(); onDismiss() },
        Cmd("外观与壁纸", "手机/平板独立配置", Icons.Default.Palette, Gold) { onSettings(); onDismiss() },
        Cmd("悬浮样式", "0=窗 1=岛 2=关（点击循环）", Icons.Default.Layers, Gold) { vm.setOverlayMode((overlayMode + 1) % 3) },
        Cmd("强制手机布局", "重启布局分辨率", Icons.Default.Smartphone, DeepSeekMuted) { onTogglePhone(); onDismiss() },
        Cmd("强制平板布局", ">=600dp 双栏", Icons.Default.TabletMac, DeepSeekMuted) { onToggleTablet(); onDismiss() },
        Cmd("退出面板", "关闭命令面板", Icons.Default.Close, DeepSeekError) { onDismiss() }
    )

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { onDismiss() }) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 3 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 3 })
        ) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp)) {
                GlassBox(
                    shape = RoundedCornerShape(Radius.xl),
                    tint = Color.White, tintAlpha = 0.08f, strokeAlpha = 0.5f, flowEnabled = true,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                ) {
                    Column(Modifier.fillMaxSize().clickable(enabled = false) {}) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(GoldGradient), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Bolt, null, tint = Color(0xFF1A1710), modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("命令面板", style = TextStyle(brush = GoldGradient, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp))
                                Text("长按 Logo 或点空白关闭", color = DeepSeekMuted, fontSize = 10.sp)
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(GoldGradient.copy(alpha = 0.3f)))
                        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(commands) { c ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))
                                        .background(DeepSeekSurfaceHigh.copy(alpha = 0.4f))
                                        .clickable { c.action() }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                        Icon(c.icon, null, tint = c.accent, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(c.title, color = DeepSeekText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(c.desc, color = DeepSeekMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = DeepSeekMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
