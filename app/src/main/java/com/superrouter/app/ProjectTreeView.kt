package com.superrouter.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProjectTreeView(nodes: List<FileNode>, modifier: Modifier = Modifier) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(nodes) {
        nodes.forEach { n -> if (n.isDir && expanded[n.path] == null) expanded[n.path] = true }
    }
    val rows = remember(nodes, expanded.toMap()) {
        val list = mutableListOf<TreeRow>()
        flatten(nodes, isLast = true, ancestorsLast = emptyList(), expanded = expanded, rows = list)
        list
    }
    LazyColumn(modifier = modifier) {
        items(rows.size) { idx -> TreeRowView(rows[idx], expanded) }
    }
}

private data class TreeRow(
    val node: FileNode,
    val isLast: Boolean,
    val ancestorsLast: List<Boolean>,
    val hasChildren: Boolean,
    val isExpanded: Boolean
)

private fun flatten(
    nodes: List<FileNode>, isLast: Boolean, ancestorsLast: List<Boolean>,
    expanded: Map<String, Boolean>, rows: MutableList<TreeRow>
) {
    nodes.forEachIndexed { idx, node ->
        val nodeIsLast = idx == nodes.size - 1
        val hasChildren = node.isDir && !node.children.isNullOrEmpty()
        val isExp = expanded[node.path] ?: (node.depth == 0)
        rows.add(TreeRow(node, nodeIsLast, ancestorsLast, hasChildren, isExp))
        if (hasChildren && isExp) {
            flatten(node.children ?: emptyList(), isLast = nodeIsLast,
                ancestorsLast = ancestorsLast + nodeIsLast, expanded = expanded, rows = rows)
        }
    }
}

@Composable
private fun TreeRowView(row: TreeRow, expanded: MutableMap<String, Boolean>) {
    val node = row.node
    var hovered by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(if (hovered) DeepSeekSurface.copy(alpha = 0.6f) else Color.Transparent)
            .clickable {
                if (row.hasChildren) {
                    expanded[node.path] = !(expanded[node.path] ?: (node.depth == 0))
                }
            }
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        row.ancestorsLast.forEach { ancestorIsLast ->
            Box(Modifier.width(20.dp).fillMaxHeight()) {
                if (!ancestorIsLast) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawLine(DeepSeekBorder, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 1.dp.toPx())
                    }
                }
            }
        }
        if (row.ancestorsLast.isNotEmpty() || node.depth > 0) {
            Box(Modifier.width(20.dp).fillMaxHeight()) {
                Canvas(Modifier.fillMaxSize()) {
                    val x = size.width / 2; val y = size.height / 2; val st = 1.dp.toPx()
                    if (row.isLast) drawLine(DeepSeekBorder, Offset(x, 0f), Offset(x, y), st)
                    else drawLine(DeepSeekBorder, Offset(x, 0f), Offset(x, size.height), st)
                    drawLine(DeepSeekBorder, Offset(x, y), Offset(size.width, y), st)
                }
            }
        }
        if (row.hasChildren) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.Gray,
                modifier = Modifier.size(16.dp).rotate(if (row.isExpanded) 0f else -90f))
        } else Spacer(Modifier.width(16.dp))
        Spacer(Modifier.width(4.dp))
        val (icon, tint) = iconFor(node)
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(node.name, color = if (node.isDir) Color.White else DeepSeekText,
            fontSize = 13.sp, fontFamily = FontFamily.Monospace,
            fontWeight = if (node.isDir) FontWeight.Medium else FontWeight.Normal)
    }
}

private fun iconFor(node: FileNode): Pair<ImageVector, Color> {
    if (node.isDir) return Icons.Default.Folder to Color(0xFFFFC857)
    val n = node.name.lowercase()
    return when {
        n.endsWith(".kt") || n.endsWith(".java") -> Icons.Default.Code to Color(0xFF7BAAF7)
        n.endsWith(".xml") -> Icons.Default.Description to Color(0xFF84C878)
        n.endsWith(".gradle") || n.endsWith(".kts") || n.endsWith(".properties") -> Icons.Default.Settings to Color(0xFFE67E22)
        n.endsWith(".yml") || n.endsWith(".yaml") -> Icons.Default.Settings to Color(0xFF9C88FF)
        n.endsWith(".md") -> Icons.Default.Article to Color(0xFFB0BEC5)
        n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".webp") -> Icons.Default.Image to Color(0xFFF06292)
        n.endsWith(".so") || n.endsWith(".jar") || n.endsWith(".aar") -> Icons.Default.Memory to Color(0xFFFFB74D)
        n.endsWith(".json") -> Icons.Default.DataObject to Color(0xFFFFD54F)
        n.endsWith(".cpp") || n.endsWith(".h") -> Icons.Default.Code to Color(0xFF4DD0E1)
        else -> Icons.Default.InsertDriveFile to Color.Gray
    }
}
