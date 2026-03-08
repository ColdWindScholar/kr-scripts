package com.projectkr.shell

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.omarea.common.shell.ShellExecutor
import com.omarea.krscript.executor.ScriptEnvironmen
import com.projectkr.shell.permissions.CheckRootStatus
import java.io.BufferedReader
import java.io.DataOutputStream
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


class SplashActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val showStartLog = remember { mutableStateOf(false) }
            val startStateText = remember { mutableStateOf(getString(R.string.pop_before_start)) }
            LaunchedEffect(Unit) {
                if (ScriptEnvironmen.isInited()) {
                    if (isTaskRoot) {
                        gotoHome()
                    }
                    return@LaunchedEffect
                }

                updateThemeStyle()

                showStartLog.value = true
                checkRoot {
                    startStateText.value = getString(R.string.pio_permission_checking)
                    hasRoot = true
                    startStateText.value = getString(R.string.pop_started)

                    val config = KrScriptConfig().init(context)
                    if (config.beforeStartSh.isNotEmpty()) {
                        BeforeStartThread(context, config, UpdateLogViewHandler(start_state_text) {
                            gotoHome()
                        }).start()
                    } else {
                        gotoHome()
                    }
                }
            }
            // 对应 FrameLayout
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = colorResource(id = R.color.splash_bg_color)
                    )
                    .padding(WindowInsets.systemBars.asPaddingValues()) // 模拟 clipToPadding 和 fitsSystemWindows 的处理
            ) {
                // 对应 LinearLayout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .align(Alignment.Center), // layout_gravity="center"
                    horizontalAlignment = Alignment.CenterHorizontally, // gravity="center"
                    verticalArrangement = Arrangement.Center
                ) {
                    // 对应 ImageView
                    if (showStartLog.value){
                    Image(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = null,
                        modifier = Modifier.size(200.dp)
                    )}

                    // 对应第一个 TextView (fullscreen_content)
                    Text(
                        text = stringResource(id = R.string.pio_starting),
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0x44FFFFFF),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 50.dp)
                    )
                    // 对应第二个 TextView (start_state_text)
                    Text(
                        text = startStateText.value,
                        fontSize = 13.sp,
                        color = Color(0xAAFFFFFF),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, top = 15.dp, end = 20.dp, bottom = 40.dp),
                        minLines = 8
                    )
                }
            }
        }

    }

    /**
     * 界面主题样式调整
     */
    private fun updateThemeStyle() {
        getWindow().setNavigationBarColor(getColorAccent())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.setNavigationBarColor(getColor(R.color.splash_bg_color))
        } else {
            window.setNavigationBarColor(resources.getColor(R.color.splash_bg_color))
        }

        //  得到当前界面的装饰视图
        if (Build.VERSION.SDK_INT >= 21) {
            val decorView = getWindow().getDecorView();
            //让应用主题内容占用系统状态栏的空间,注意:下面两个参数必须一起使用 stable 牢固的
            val option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            decorView.setSystemUiVisibility(option);
            //设置状态栏颜色为透明
            window.setStatusBarColor(0x00000000)
        }
    }

    private fun getColorAccent(): Int {
        val typedValue = TypedValue()
        this.theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, typedValue, true)
        return typedValue.data
    }

    /**
     * 开始检查必需权限
     */

    private fun checkPermission(permission: String): Boolean = PermissionChecker.checkSelfPermission(this.applicationContext, permission) == PermissionChecker.PERMISSION_GRANTED

    /**
     * 检查权限 主要是文件读写权限
     */
    private fun checkFileWrite(next: Runnable) {
        Thread(Runnable {
            CheckRootStatus.grantPermission(this)
            if (!(checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) && checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    ActivityCompat.requestPermissions(
                            this@SplashActivity,
                            arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                                    Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Manifest.permission.WAKE_LOCK
                            ),
                            0x11
                    )
                } else {
                    ActivityCompat.requestPermissions(
                            this@SplashActivity,
                            arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                                    Manifest.permission.WAKE_LOCK
                            ),
                            0x11
                    )
                }
            }
            myHandler.post {
                next.run()
            }
        }).start()
    }

    private var hasRoot = false
    private var myHandler = Handler()

    private fun checkRoot(next: Runnable) {
        CheckRootStatus(this, next).forceGetRoot()
    }

    /**
     * 启动完成
     */
    private fun startToFinish() {

    }

    private fun gotoHome() {
        if (this.intent != null && this.intent.hasExtra("JumpActionPage") && this.intent.getBooleanExtra("JumpActionPage", false)) {
            val actionPage = Intent(this.applicationContext, ActionPage::class.java)
            actionPage.putExtras(this.intent)
            startActivity(actionPage)
        } else {
            val home = Intent(this.applicationContext, MainActivity::class.java)
            startActivity(home)
        }
        finish()
    }

    private class UpdateLogViewHandler(private var logView: TextView, private val onExit: Runnable) {
        private val handler = Handler(Looper.getMainLooper())
        private var notificationMessageRows = ArrayList<String>()
        private var someIgnored = false

        fun onLogOutput(log: String) {
            handler.post {
                synchronized(notificationMessageRows) {
                    if (notificationMessageRows.size > 6) {
                        notificationMessageRows.remove(notificationMessageRows.first())
                        someIgnored = true
                    }
                    notificationMessageRows.add(log)
                    logView.setText(notificationMessageRows.joinToString("\n", if (someIgnored) "……\n" else "").trim())
                }
            }
        }

        fun onExit() {
            handler.post { onExit.run() }
        }
    }

    private class BeforeStartThread(private var context: Context, private val config: KrScriptConfig, private var updateLogViewHandler: UpdateLogViewHandler) : Thread() {
        val params = config.getVariables();

        override fun run() {
            try {
                val process = if (CheckRootStatus.lastCheckResult) ShellExecutor.getSuperUserRuntime() else ShellExecutor.getRuntime()
                if (process != null) {
                    val outputStream = DataOutputStream(process.outputStream)

                    ScriptEnvironmen.executeShell(context, outputStream, config.beforeStartSh, params, null, "pio-splash")

                    StreamReadThread(process.inputStream.bufferedReader(), updateLogViewHandler).start()
                    StreamReadThread(process.errorStream.bufferedReader(), updateLogViewHandler).start()

                    process.waitFor()
                    updateLogViewHandler.onExit()
                } else {
                    updateLogViewHandler.onExit()
                }
            } catch (ex: Exception) {
                updateLogViewHandler.onExit()
            }
        }
    }

    private class StreamReadThread(private var reader: BufferedReader, private var updateLogViewHandler: UpdateLogViewHandler) : Thread() {
        override fun run() {
            var line: String? = ""
            while (true) {
                line = reader.readLine()
                if (line == null) {
                    break
                } else {
                    updateLogViewHandler.onLogOutput(line)
                }
            }
        }
    }
}