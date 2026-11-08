package com.superrouter.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object FloatingState {
    var name by mutableStateOf("AI任务")
    var step by mutableStateOf(0)
    var action by mutableStateOf("准备中")
    var mode by mutableStateOf(0) // 0=窗 1=岛 2=关
    var cooldown by mutableStateOf(0.0)
    var cooldownTotal by mutableStateOf(0.0)
    var isExpanded by mutableStateOf(false)
}
