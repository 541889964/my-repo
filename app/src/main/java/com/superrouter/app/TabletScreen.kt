package com.superrouter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
//  TabletScreen — 平板模式双栏布局
//  左：常驻会话侧栏（320dp 玻璃）
//  右：主对话区
// ============================================================

@Composable
fun TabletScreen(
    vm: ChatVM,
    current: Session?,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onLan: () -> Unit,
    onTree: () -> Unit,
    onCool: () -> Unit,
    onToken: () -> Unit,
    onPalette: () -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        TabletSidebar(
            vm = vm,
            current = current,
            onNew = onNew,
            onSettings = onSettings,
            modifier = Modifier.width(320.dp).fillMaxHeight()
        )

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f).fillMaxHeight()) {
            TabletHeader(current, onLan, onTree, onCool, onPalette)
            Spacer(Modifier.height(6.dp))
            InsightDeck(vm)
            Spacer(Modifier.height(8.dp))
            QuickActionsRow(vm, onLan, onTree, onCool, onSettings)
            Spacer(Modifier.height(8.dp))
            ChatList(vm, current, Modifier.weight(1f))
            GlassStatusBar(vm, onToken)
            GlassInputBar(vm)
        }
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
fun TabletSidebar(
    vm: ChatVM,
    current: Session?,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions by vm.sessions.collectAsState()
    val currentId = current?.id

    Box(modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp)) {
        GlassBox(
            shape = RoundedCornerShape(Radius.xl),
            tint = Color.White, tintAlpha = 0.06f, strokeAlpha = 0.38f,
            flowEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(GoldGradient), contentAlignment = Alignment.Center) {
                        Text("S", color = Color(0xFF1A1710), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("SUPERROUTER", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                        Text("全自动 AI 代理", color = DeepSeekMuted, fontSize = 10.sp)
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(GoldGradient))

                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(Radius.md))
                            .background(GoldGradient).clickable { onNew() },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color(0xFF1A1710), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("新建项目", color = Color(0xFF1A1710), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    "会话 (${sessions.size})",
                    color = DeepSeekMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(sessions.reversed()) { s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(if (s.id == currentId) Gold.copy(alpha = 0.10f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (s.id == currentId) Gold.copy(alpha = 0.35f) else Color.Transparent,
                                    RoundedCornerShape(Radius.md)
                                )
                                .clickable { vm.selectSession(s.id) }
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
                            .background(DeepSeekSurfaceHigh.copy(alpha = 0.55f))
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
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TabletHeader(
    current: Session?,
    onLan: () -> Unit,
    onTree: () -> Unit,
    onCool: () -> Unit,
    onPalette: () -> Unit
) {
    val running = current?.runs?.lastOrNull()?.running == true
    val pendingWait = current?.pendingPrompt != null && !running
    val lastFailed = current?.runs?.lastOrNull()?.finalReply?.startsWith("[网络错误]") == true

    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp)) {
        GlassBox(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.lg),
            tint = Color.White, tintAlpha = 0.06f, strokeAlpha = 0.35f,
            flowEnabled = false
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(GoldGradient)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onPalette() }
                        ),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Bolt, null, tint = Color(0xFF1A1710), modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current?.repoName?.uppercase() ?: "DEFAULT_PROJECT",
                        color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            current?.title ?: "就绪",
                            color = DeepSeekText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
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
                IconButton(onClick = onCool, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Timer, null, tint = DeepSeekAccent, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onLan, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Wifi, null, tint = DeepSeekAccent, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onTree, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.AccountTree, null, tint = DeepSeekAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
