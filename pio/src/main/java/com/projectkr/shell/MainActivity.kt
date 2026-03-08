package com.projectkr.shell

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.FragmentActivity
import com.omarea.common.shared.FilePathResolver
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.krscript.config.PageConfigReader
import com.omarea.krscript.config.PageConfigSh
import com.omarea.krscript.model.*
import com.omarea.krscript.ui.ActionListFragment
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.omarea.vtools.FloatMonitor
import com.projectkr.shell.databinding.ActivityMainBinding
import com.projectkr.shell.permissions.CheckRootStatus
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.More
enum class MainTab {
    Home, Favourites, Pages
}
class MainActivity : ComponentActivity() {
    private val progressBarDialog = ProgressBarDialog(this)
    private var handler = Handler()
    private var krScriptConfig = KrScriptConfig()
    private lateinit var binding: ActivityMainBinding

    private fun checkPermission(permission: String): Boolean = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED

    @SuppressLint("ContextCastToActivity")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeModeState.switchTheme(this)
        krScriptConfig = KrScriptConfig()

        setContent {
            progressBarDialog.showDialog(getString(R.string.please_wait))
            val page2Config = krScriptConfig.pageListConfig
            val favoritesConfig = krScriptConfig.favoriteConfig
            val pages = getItems(page2Config)
            val favorites = getItems(favoritesConfig)
            handler.post {
                progressBarDialog.hideDialog()
            }
            val items = listOf(
                if (CheckRootStatus.lastCheckResult && krScriptConfig.allowHomePage) NavigationItem(
                    label = getString(R.string.tab_home),
                    icon = MiuixIcons.MapAlbum) else null,
                if (favorites != null && favorites.size > 0) NavigationItem(label = getString(R.string.tab_favorites), icon = MiuixIcons.Favorites) else null,
                if (pages != null && pages.size > 0) NavigationItem(label = getString(R.string.tab_pages), icon = MiuixIcons.More) else null
            )
            val pagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { items.filter { (it != null) }.size }
            )
            val coroutineScope = rememberCoroutineScope()

            // 同步 pager 状态到当前页面索引
            val currentPage by remember { derivedStateOf { pagerState.currentPage } }
            if (!(checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) && checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 111);
            }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = getString(R.string.app_name))
                },
                bottomBar = {
                    NavigationBar(
                        mode = NavigationDisplayMode.IconAndText,
                        content = {
                            items.forEachIndexed { index, item ->
                                if (item != null) {
                                NavigationBarItem(
                                    icon = item.icon,
                                    label = item.label,
                                    selected = currentPage == index,
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                )}
                            }
                        })
                }
            ) {
                val fragmentManager = (LocalContext.current as FragmentActivity).supportFragmentManager
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                        beyondViewportPageCount = 1,
                        key = { MainTab.entries[it].name }
                    ) { page ->
                        when (MainTab.entries[page]) {
                            MainTab.Home -> {
                                if (CheckRootStatus.lastCheckResult && krScriptConfig.allowHomePage) {
                                    val home = FragmentHome()
                                    val fragmentManager = fragmentManager
                                    val transaction = fragmentManager.beginTransaction()
                                    transaction.replace(R.id.main_tabhost_cpu, home)
                                    transaction.commitAllowingStateLoss()
                                }
                            }

                            MainTab.Favourites -> {
                                val favoritesFragment = ActionListFragment.create(favorites, getKrScriptActionHandler(favoritesConfig, true), null, ThemeModeState.getThemeMode())
                                fragmentManager.beginTransaction().replace(R.id.list_favorites, favoritesFragment).commitAllowingStateLoss()
                            }

                            MainTab.Pages -> {
                                val allItemFragment = ActionListFragment.create(pages, getKrScriptActionHandler(page2Config, false), null, ThemeModeState.getThemeMode())
                                fragmentManager.beginTransaction().replace(R.id.list_pages, allItemFragment).commitAllowingStateLoss()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getItems(pageNode: PageNode): ArrayList<NodeInfoBase>? {
        var items: ArrayList<NodeInfoBase>? = null

        if (pageNode.pageConfigSh.isNotEmpty()) {
            items = PageConfigSh(this, pageNode.pageConfigSh, null).execute()
        }
        if (items == null && pageNode.pageConfigPath.isNotEmpty()) {
            items = PageConfigReader(this.applicationContext, pageNode.pageConfigPath, null).readConfigXml()
        }

        return items
    }

    private fun reloadFavoritesTab() {
        Thread {
            val favoritesConfig = krScriptConfig.favoriteConfig
            val favorites = getItems(favoritesConfig)
            favorites?.run {
                handler.post {
                    updateFavoritesTab(this, favoritesConfig)
                }
            }
        }.start()
    }

    private fun reloadMoreTab() {
        Thread {
            val page2Config = krScriptConfig.pageListConfig
            val pages = getItems(page2Config)

            pages?.run {
                handler.post {
                    updateMoreTab(this, page2Config)
                }
            }
        }.start()
    }

    private fun getKrScriptActionHandler(pageNode: PageNode, isFavoritesTab: Boolean): KrScriptActionHandler {
        return object : KrScriptActionHandler {
            override fun onActionCompleted(runnableNode: RunnableNode) {
                if (runnableNode.autoFinish ) {
                    finishAndRemoveTask()
                } else if (runnableNode.reloadPage) {
                    // TODO:多线程优化
                    if (isFavoritesTab) {
                        reloadFavoritesTab()
                    } else {
                        reloadMoreTab()
                    }
                }
            }

            override fun addToFavorites(clickableNode: ClickableNode, addToFavoritesHandler: KrScriptActionHandler.AddToFavoritesHandler) {
                val page = clickableNode as? PageNode
                    ?: if (clickableNode is RunnableNode) {
                        pageNode
                    } else {
                        return
                    }

                val intent = Intent()

                intent.component = ComponentName(this@MainActivity.applicationContext, ActionPage::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

                if (clickableNode is RunnableNode) {
                    intent.putExtra("autoRunItemId", clickableNode.key)
                }
                intent.putExtra("page", page)

                addToFavoritesHandler.onAddToFavorites(clickableNode, intent)
            }

            override fun onSubPageClick(pageNode: PageNode) {
                _openPage(pageNode)
            }

            override fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
                return chooseFilePath(fileSelectedInterface)
            }
        }
    }

    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null
    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val ACTION_FILE_PATH_CHOOSER_INNER = 65300

    private fun chooseFilePath(extension: String) {
        try {
            val intent = Intent(this, ActivityFileSelector::class.java)
            intent.putExtra("extension", extension)
            startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER_INNER)
        } catch (ex: java.lang.Exception) {
            Toast.makeText(this, "启动内置文件选择器失败！", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseFilePath(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(com.omarea.krscript.R.string.kr_write_external_storage), Toast.LENGTH_LONG).show()
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2);
            return false
        } else {
            return try {
                val suffix = fileSelectedInterface.suffix()
                if (suffix != null && suffix.isNotEmpty()) {
                    chooseFilePath(suffix)
                } else {
                    val intent = Intent(Intent.ACTION_GET_CONTENT);
                    val mimeType = fileSelectedInterface.mimeType()
                    if (mimeType != null) {
                        intent.type = mimeType
                    } else {
                        intent.type = "*/*"
                    }
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER);
                }
                this.fileSelectedInterface = fileSelectedInterface
                true;
            } catch (ex: java.lang.Exception) {
                false
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACTION_FILE_PATH_CHOOSER) {
            val result = if (data == null || resultCode != Activity.RESULT_OK) null else data.data
            if (fileSelectedInterface != null) {
                if (result != null) {
                    val absPath = getPath(result)
                    fileSelectedInterface?.onFileSelected(absPath)
                } else {
                    fileSelectedInterface?.onFileSelected(null)
                }
            }
            this.fileSelectedInterface = null
        } else if (requestCode == ACTION_FILE_PATH_CHOOSER_INNER) {
            val absPath = if (data == null || resultCode != Activity.RESULT_OK) null else data.getStringExtra("file")
            fileSelectedInterface?.onFileSelected(absPath)
            this.fileSelectedInterface = null
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun getPath(uri: Uri): String? {
        try {
            return FilePathResolver().getPath(this, uri)
        } catch (ex: Exception) {
            return null
        }
    }

    fun _openPage(pageNode: PageNode) {
        OpenPageHelper(this).openPage(pageNode)
    }

    private fun getDensity(): Int {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        return dm.densityDpi
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        menu.findItem(R.id.action_graph).isVisible = (binding.mainTabhostCpu.visibility == View.VISIBLE)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.option_menu_info -> {
                val layoutInflater = LayoutInflater.from(this)
                val layout = layoutInflater.inflate(R.layout.dialog_about, null)
                val transparentUi = layout.findViewById<CompoundButton>(R.id.transparent_ui);
                val themeConfig = ThemeConfig(this)
                transparentUi.setOnClickListener {
                    val isChecked = (it as CompoundButton).isChecked
                    if (isChecked && !checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        it.isChecked = false
                        Toast.makeText(this@MainActivity, com.omarea.krscript.R.string.kr_write_external_storage, Toast.LENGTH_SHORT).show()
                    } else {
                        themeConfig.setAllowTransparentUI(isChecked)
                    }
                }
                transparentUi.isChecked = themeConfig.getAllowTransparentUI()

                DialogHelper.customDialog(this, layout)
            }
            R.id.option_menu_reboot -> {
                DialogPower(this).showPowerMenu()
            }
            R.id.action_graph -> {
                if (FloatMonitor.isShown == true) {
                    FloatMonitor(this).hidePopupWindow()
                    return false
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    if (Settings.canDrawOverlays(this)) {
                        FloatMonitor(this).showPopupWindow()
                        Toast.makeText(this, getString(R.string.float_monitor_tips), Toast.LENGTH_LONG).show()
                    } else {
                        //若没有权限，提示获取
                        //val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        //startActivity(intent);
                        val intent = Intent()
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                        intent.data = Uri.fromParts("package", this.packageName, null)

                        Toast.makeText(applicationContext, getString(R.string.permission_float), Toast.LENGTH_LONG).show()

                        try {
                            startActivity(intent)
                        } catch (ex: Exception) {
                        }
                    }
                } else {
                    FloatMonitor(this).showPopupWindow()
                    Toast.makeText(this, getString(R.string.float_monitor_tips), Toast.LENGTH_LONG).show()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
