package com.omarea.krscript.model

open class ClickableNode : NodeInfoBase() {
    // 功能图标路径
    var iconPath = ""

    // 是否允许添加快捷方式（非false，且具有key则默认允许）
    var allowShortcut:Boolean? = null
}
