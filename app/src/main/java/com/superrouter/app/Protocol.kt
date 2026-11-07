package com.superrouter.app

import java.util.UUID

data class AgentStep(val thought: String, val action: String, val tool: String, val params: Map<String, String> = emptyMap())

data class StepRecord(
    val n: Int, val tool: String, val actionName: String, val thought: String,
    val command: String? = null, val result: String? = null, val success: Boolean = true, val elapsedMs: Long = 0L
)

data class AgentRun(
    val id: String = UUID.randomUUID().toString(),
    val task: String,
    val chineseName: String? = null,
    val steps: List<StepRecord> = emptyList(),
    val currentProgress: String = "",
    val currentThinking: String = "",
    val running: Boolean = true,
    val finalReply: String? = null,
    val needsPush: Boolean = false,
    val isPushing: Boolean = false,
    val buildStatus: String = "",
    val buildUrl: String? = null,
    val apkUrl: String? = null,
    val errorLogs: String? = null,
    val downloadProgress: Float = 0f,
    val retryCount: Int = 0,
    val lastError: String? = null
)

data class ChatMsg(val isUser: Boolean, val content: String)

data class Session(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "新对话",
    val runs: List<AgentRun> = emptyList(),
    var remoteSessionId: String? = null,
    var lastRemoteMsgId: String? = null,
    var lastGoodMsgId: String? = null,
    var pendingPrompt: String? = null,
    var pendingRunId: String? = null,
    var retryCount: Int = 0,
    var lastActiveAt: Long = 0L,
    var repoName: String = "default_project",
    var vpnPackageName: String = "",
    var isProtected: Boolean = false,
    var accumulatedTokens: Long = 0L,
    var chatHistory: List<ChatMsg> = emptyList(),
    var compressionCount: Int = 0
)

data class FileNode(val name: String, val path: String, val isDir: Boolean, val children: List<FileNode>? = null, val depth: Int = 0)

// touched by force push at Fri Nov  6 00:12:51 CST 2026

// force push trigger: 5853

// force push trigger: 15869

// force push trigger: 28068

// force push trigger: 30874

// force push trigger: 29459
