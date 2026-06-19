package com.stryker.terminal.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stryker.terminal.R
import com.stryker.terminal.ui.customize.CustomizeActivity
import com.stryker.terminal.ui.other.SettingsActivity

class SettingsHomeFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.settings_terminal_home, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val host = activity as? SettingsActivity

    val versionView = view.findViewById<TextView>(R.id.settings_version)
    versionView.text = buildString {
      append(getString(R.string.term_settings_subtitle))
      runCatching {
        val ctx = requireContext()
        val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        if (!version.isNullOrEmpty()) append(" · v").append(version)
      }
    }

    view.findViewById<View>(R.id.row_general).setOnClickListener {
      host?.openChild(GeneralPrefsFragment(), "general", R.string.term_settings_general_title)
    }
    view.findViewById<View>(R.id.row_ui).setOnClickListener {
      host?.openChild(UiPrefsFragment(), "ui", R.string.term_settings_ui_title)
    }
    view.findViewById<View>(R.id.row_customization).setOnClickListener {
      startActivity(Intent(requireContext(), CustomizeActivity::class.java))
    }
    view.findViewById<View>(R.id.row_reset).setOnClickListener {
      host?.confirmAndReset()
    }
    view.findViewById<View>(R.id.row_licenses).setOnClickListener {
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.source_code)
        .setIcon(R.drawable.ic_github)
        .setMessage(R.string.sources_licenses)
        .setPositiveButton(android.R.string.ok, null)
        .show()
    }
    view.findViewById<View>(R.id.row_github).setOnClickListener {
      runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/zalexdev/strykerapp")))
      }
    }
  }
}
