package com.stryker.terminal.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.stryker.terminal.R
import com.stryker.terminal.component.config.DefaultValues
import com.stryker.terminal.component.config.NeoPreference

class UiPrefsFragment : Fragment() {

  private lateinit var eksWeightRow: View
  private lateinit var eksWeightSwitch: SwitchMaterial

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.settings_terminal_ui, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    bindSwitch(
      view, R.id.row_fullscreen, R.id.switch_fullscreen,
      R.string.key_ui_fullscreen, DefaultValues.enableFullScreen
    )
    bindSwitch(
      view, R.id.row_hide_toolbar, R.id.switch_hide_toolbar,
      R.string.key_ui_hide_toolbar, DefaultValues.enableAutoHideToolbar
    )
    bindSwitch(
      view, R.id.row_next_tab, R.id.switch_next_tab,
      R.string.key_ui_next_tab_anim, DefaultValues.enableSwitchNextTab
    )

    eksWeightRow = view.findViewById(R.id.row_eks_weight)
    eksWeightSwitch = view.findViewById(R.id.switch_eks_weight)

    val eksSwitch = view.findViewById<SwitchMaterial>(R.id.switch_eks)
    eksSwitch.isChecked = NeoPreference.loadBoolean(R.string.key_ui_eks_enabled, DefaultValues.enableExtraKeys)
    eksSwitch.setOnCheckedChangeListener { _, checked ->
      NeoPreference.store(R.string.key_ui_eks_enabled, checked)
      setEksWeightEnabled(checked)
    }
    view.findViewById<View>(R.id.row_eks).setOnClickListener { eksSwitch.isChecked = !eksSwitch.isChecked }

    eksWeightSwitch.isChecked =
      NeoPreference.loadBoolean(R.string.key_ui_eks_weight_explicit, DefaultValues.enableExplicitExtraKeysWeight)
    eksWeightSwitch.setOnCheckedChangeListener { _, checked ->
      NeoPreference.store(R.string.key_ui_eks_weight_explicit, checked)
    }
    eksWeightRow.setOnClickListener { eksWeightSwitch.isChecked = !eksWeightSwitch.isChecked }

    setEksWeightEnabled(eksSwitch.isChecked)
  }

  private fun bindSwitch(root: View, rowId: Int, switchId: Int, keyRes: Int, default: Boolean) {
    val sw = root.findViewById<SwitchMaterial>(switchId)
    sw.isChecked = NeoPreference.loadBoolean(keyRes, default)
    sw.setOnCheckedChangeListener { _, checked -> NeoPreference.store(keyRes, checked) }
    root.findViewById<View>(rowId).setOnClickListener { sw.isChecked = !sw.isChecked }
  }

  private fun setEksWeightEnabled(enabled: Boolean) {
    eksWeightRow.isEnabled = enabled
    eksWeightRow.isClickable = enabled
    eksWeightRow.alpha = if (enabled) 1f else 0.4f
    eksWeightSwitch.isEnabled = enabled
  }
}
