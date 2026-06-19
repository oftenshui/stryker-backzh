package com.stryker.terminal.component.userscript

import android.content.Context
import android.system.Os
import com.stryker.terminal.App
import com.stryker.terminal.component.NeoComponent
import com.stryker.terminal.component.config.NeoTermPath
import com.stryker.terminal.ui.other.SuUtils
import com.stryker.terminal.utils.NLog
import com.stryker.terminal.utils.extractAssetsDir
import java.io.File

class UserScript(val scriptFile: File)

class UserScriptComponent : NeoComponent {
  var binFiles = listOf<UserScript>()
  var userScripts = listOf<UserScript>()
  private val binDir = File(NeoTermPath.BIN_PATH)
  private val scriptDir = File(NeoTermPath.USER_SCRIPT_PATH)

  override fun onServiceInit() = checkForFiles()

  override fun onServiceDestroy() {
  }

  override fun onServiceObtained() = checkForFiles()

  fun extractDefaultScript(context: Context) = kotlin.runCatching {
    SuUtils.customCommand("mkdir -p ${NeoTermPath.USR_PATH}/")
    SuUtils.customCommand("rm -rf ${NeoTermPath.BIN_PATH}/*")

    context.extractAssetsDir("scripts", NeoTermPath.USER_SCRIPT_PATH)
    scriptDir.listFiles()?.forEach {
      Os.chmod(it.absolutePath, 448)
    }

    context.extractAssetsDir("bin", NeoTermPath.BIN_PATH)
    SuUtils.customCommand("chmod 448 ${NeoTermPath.BIN_PATH}/*")

  }.onFailure {
    NLog.e("UserScript", "Failed to extract default user scripts: ${it.localizedMessage}")
  }


  private fun checkForFiles() {
    extractDefaultScript(App.get())
    reloadScripts()
  }

  private fun reloadScripts() {
    userScripts = (scriptDir.listFiles() ?: emptyArray())
      .takeWhile { it.canExecute() }
      .map { UserScript(it) }
      .toList()

    binFiles = (binDir.listFiles() ?: emptyArray())
      .takeWhile { it.canExecute() }
      .map { UserScript(it) }
      .toList()
  }

}
