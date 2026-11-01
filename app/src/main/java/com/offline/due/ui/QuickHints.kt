package com.offline.due.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf

object QuickHints {

    private const val PREFIX = "写一句，比如 "

    val HINTS: List<String> = listOf(
        "明天 早起跑步",
        "下周一 重新开始",
        "三天后 体检",
        "每周 读一本书",
        "每天 走一万步",
        "下月5号 发工资",
        "明天 打电话回家",
        "这周六 看电影",
        "十天后 生日",
        "下周三 项目上线",
        "后天 见老友",
        "下周五 去看海",
        "每月 存点钱",
        "一周后 去旅行",
        "每天 早点睡",
        "下周日 陪家人",
        "两周后 考试",
        "明天 开始健身",
        "三个月后 搬家",
        "今天 开始运动",
    )

    private const val PREFS = "due_quick_hint"
    private const val KEY_INDEX = "index"

    private val index = mutableStateOf(0)

    private var app: Context? = null

    val text: String get() = PREFIX + HINTS[index.value % HINTS.size]

    fun start(context: Context) {
        val ctx = context.applicationContext
        app = ctx
        index.value = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_INDEX, 0)
            .coerceIn(0, HINTS.lastIndex)
        advance()
    }

    fun next() = advance()

    private fun advance() {
        val next = (index.value + 1) % HINTS.size
        index.value = next
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putInt(KEY_INDEX, next)
            ?.apply()
    }
}

