package com.stryker.terminal.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.stryker.terminal.R
import com.stryker.terminal.component.config.DefaultValues
import com.stryker.terminal.component.config.NeoPreference

class GeneralPrefsFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.settings_terminal_general, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val commandValue = view.findViewById<MaterialTextView>(R.id.initial_command_value)
    refreshCommandValue(commandValue)
    view.findViewById<View>(R.id.row_initial_command).setOnClickListener {
      showInitialCommandDialog(commandValue)
    }

    bindSwitch(
      view, R.id.row_backspace, R.id.switch_backspace,
      R.string.key_generaL_backspace_map_to_esc, DefaultValues.enableBackButtonBeMappedToEscape
    )
    bindSwitch(
      view, R.id.row_volume, R.id.switch_volume,
      R.string.key_general_volume_as_control, DefaultValues.enableSpecialVolumeKeys
    )
    bindSwitch(
      view, R.id.row_word_ime, R.id.switch_word_ime,
      R.string.key_general_enable_word_based_ime, DefaultValues.enableWordBasedIme
    )
  }

  private fun bindSwitch(root: View, rowId: Int, switchId: Int, keyRes: Int, default: Boolean) {
    val sw = root.findViewById<SwitchMaterial>(switchId)
    sw.isChecked = NeoPreference.loadBoolean(keyRes, default)
    sw.setOnCheckedChangeListener { _, checked -> NeoPreference.store(keyRes, checked) }
    root.findViewById<View>(rowId).setOnClickListener { sw.isChecked = !sw.isChecked }
  }

  private fun refreshCommandValue(label: MaterialTextView) {
    val current = NeoPreference.getInitialCommand()
    label.text = if (current.isBlank()) getString(R.string.term_settings_value_none) else current
  }

  private fun showInitialCommandDialog(label: MaterialTextView) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_terminal_input, null)
    val field = dialogView.findViewById<TextInputEditText>(R.id.input_value)
    field.setText(NeoPreference.getInitialCommand())

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.term_pref_initial_command_title)
      .setView(dialogView)
      .setPositiveButton(R.string.term_dialog_save) { _, _ ->
        NeoPreference.store(R.string.key_general_initial_command, field.text?.toString()?.trim() ?: "")
        refreshCommandValue(label)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
