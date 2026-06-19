package com.stryker.terminal.ui.other

import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stryker.terminal.R
import com.stryker.terminal.component.config.NeoTermPath
import com.stryker.terminal.ui.settings.SettingsHomeFragment
import com.stryker.terminal.utils.extractAssetsDir
import com.topjohnwu.superuser.Shell

class SettingsActivity : AppCompatActivity() {

  private lateinit var toolbar: MaterialToolbar

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.ui_settings)

    toolbar = findViewById(R.id.settings_toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    supportFragmentManager.addOnBackStackChangedListener { syncToolbarTitle() }

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(R.id.settings_nav_host, SettingsHomeFragment())
        .commit()
    }
    syncToolbarTitle()
  }

  fun openChild(fragment: Fragment, tag: String, @StringRes title: Int) {
    if (topBackStackTag() == tag) return
    supportFragmentManager.beginTransaction()
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .replace(R.id.settings_nav_host, fragment, tag)
      .addToBackStack(tag)
      .commit()
    supportActionBar?.setTitle(title)
  }

  private fun topBackStackTag(): String? {
    val count = supportFragmentManager.backStackEntryCount
    return if (count > 0) supportFragmentManager.getBackStackEntryAt(count - 1).name else null
  }

  private fun syncToolbarTitle() {
    val titleRes = when (topBackStackTag()) {
      "general" -> R.string.term_settings_general_title
      "ui" -> R.string.term_settings_ui_title
      else -> R.string.settings
    }
    supportActionBar?.setTitle(titleRes)
  }

  fun confirmAndReset() {
    MaterialAlertDialogBuilder(this)
      .setMessage(R.string.reset_app_warning)
      .setPositiveButton(android.R.string.yes) { _, _ -> resetApp() }
      .setNegativeButton(android.R.string.no, null)
      .show()
  }

  private fun resetApp() {
    Thread {
      val usr = NeoTermPath.USR_PATH
      val bin = NeoTermPath.BIN_PATH
      Runtime.getRuntime().exec("mkdir -p $usr/").waitFor()
      Shell.cmd("/system/bin/rm -rf $bin").exec()
      Thread.sleep(1200)
      extractAssetsDir("bin", "$bin/")
      Thread.sleep(800)
      Shell.cmd("/system/bin/chmod +x $bin/bash").exec()
      Shell.cmd("/system/bin/chmod +x $bin/stryker-ch").exec()
      Shell.cmd("/system/bin/chmod +x $bin/android-su").exec()
      runOnUiThread {
        if (!isFinishing && !isDestroyed) {
          MaterialAlertDialogBuilder(this)
            .setMessage(R.string.done)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        }
      }
    }.start()
  }

  override fun onSupportNavigateUp(): Boolean {
    stepBack()
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      stepBack()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  private fun stepBack() {
    if (supportFragmentManager.backStackEntryCount > 0) {
      supportFragmentManager.popBackStack()
    } else {
      finish()
    }
  }

  @Suppress("DEPRECATION")
  override fun onBackPressed() {
    if (supportFragmentManager.backStackEntryCount > 0) {
      supportFragmentManager.popBackStack()
    } else {
      super.onBackPressed()
    }
  }
}
