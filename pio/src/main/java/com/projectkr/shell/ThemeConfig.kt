package com.projectkr.shell

import android.app.Activity
import android.content.Context

class ThemeConfig (private val activity: Activity) {
    private val config = activity.getSharedPreferences("theme", Context.MODE_PRIVATE)

    fun getAllowTransparentUI(): Boolean {
        return config.getBoolean("TransparentUI", false)
    }

    fun setAllowTransparentUI(allow: Boolean) {
        config.edit().putBoolean("TransparentUI", allow).apply()
        activity.recreate()
    }
}