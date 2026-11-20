package com.focustracker.detector

/**
 * 应用分类（白名单/黑名单）。
 * 初版使用内置默认列表；后续可改为用户可配置并持久化。
 */
object AppClassifier {
    const val SELF_PACKAGE = "com.focustracker"

    // 默认白名单（专注/学习/工作类）
    private val focusList = setOf(
        "com.google.android.apps.docs",
        "com.google.android.apps.docs.editors.docs",
        "com.google.android.apps.docs.editors.sheets",
        "com.google.android.apps.docs.editors.slides",
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint",
        "org.wikipedia",
        "com.duolingo",
        "com.termux",
        "com.vscode.coderunner",
        "com.aistra.halfnote",
    )

    // 默认黑名单（分心类：社交/短视频/游戏/购物）
    private val distractList = setOf(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.tencent.tmgp.sgame",
        "com.tencent.tmgp.pubgmhd",
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.pdd",
        "com.xunmeng.pinduoduo",
    )

    fun classify(packageName: String?): AppCategory = when {
        packageName == null -> AppCategory.NEUTRAL
        packageName == SELF_PACKAGE -> AppCategory.SELF
        packageName in distractList -> AppCategory.DISTRACT
        packageName in focusList -> AppCategory.FOCUS
        else -> AppCategory.NEUTRAL
    }
}
