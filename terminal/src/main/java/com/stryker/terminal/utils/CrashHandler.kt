package com.stryker.terminal.utils

import android.content.Intent
import com.stryker.terminal.App
import com.stryker.terminal.ui.other.CrashActivity

object CrashHandler : Thread.UncaughtExceptionHandler {
  private lateinit var defaultHandler: Thread.UncaughtExceptionHandler

  fun init() {
    defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler(this)
  }

  override fun uncaughtException(t: Thread?, e: Throwable?) {
    e?.printStackTrace()

    val intent = Intent(App.get(), CrashActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.putExtra("exception", e)
    App.get().startActivity(intent)
    defaultHandler.uncaughtException(t, e)
  }
}
