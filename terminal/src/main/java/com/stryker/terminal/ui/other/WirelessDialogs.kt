package com.stryker.terminal.ui.other

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stryker.terminal.R

object WirelessDialogs {

    const val OP_TIMEOUT_MS = 25_000L

    fun showInterfaces(activity: Activity) = InterfacesDialog(activity).show()

    fun showUsbDevices(activity: Activity) = UsbDialog(activity).show()
}

private fun tintLow(color: Int) = ColorStateList.valueOf((color and 0x00FFFFFF) or 0x33000000)

private class InterfacesDialog(private val activity: Activity) {

    private val root: View =
        LayoutInflater.from(activity).inflate(R.layout.dialog_tools_list, null)
    private val subtitle = root.findViewById<TextView>(R.id.dialog_subtitle)
    private val progress = root.findViewById<ProgressBar>(R.id.dialog_progress)
    private val empty = root.findViewById<TextView>(R.id.dialog_empty)
    private val body = root.findViewById<LinearLayout>(R.id.dialog_body)

    private val dialog: AlertDialog = MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.term_wifi_dialog_title)
        .setIcon(R.drawable.ic_wifi_iface)
        .setView(root)
        .setNeutralButton(R.string.term_dialog_refresh, null)
        .setPositiveButton(R.string.term_dialog_close, null)
        .create()

    private val handler = Handler(Looper.getMainLooper())
    private val watchdog = Runnable { onTimeout() }

    private var busy = false

    fun show() {
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { if (!busy) refresh() }
            refresh()
        }
        dialog.setOnDismissListener { handler.removeCallbacks(watchdog) }
        dialog.show()
    }

    private fun alive() = !activity.isFinishing && !activity.isDestroyed && dialog.isShowing

    private fun arm() {
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WirelessDialogs.OP_TIMEOUT_MS)
    }

    private fun refresh() {
        if (activity.isFinishing || activity.isDestroyed) return
        busy = true
        body.alpha = 1f
        body.removeAllViews()
        empty.visibility = View.GONE
        progress.visibility = View.VISIBLE
        subtitle.setText(R.string.term_wifi_scanning)
        arm()
        SuUtils.getMonitorInterfaces(activity) { list -> render(list) }
    }

    private fun render(list: ArrayList<SuUtils.Iface>) {
        handler.removeCallbacks(watchdog)
        busy = false
        if (!alive()) return
        progress.visibility = View.GONE
        body.alpha = 1f
        body.removeAllViews()
        subtitle.text = activity.getString(R.string.term_wifi_count, list.size)
        if (list.isEmpty()) {
            empty.setText(R.string.term_wifi_empty)
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE
        val inflater = LayoutInflater.from(activity)
        for (iface in list) {
            val row = inflater.inflate(R.layout.dialog_iface_row, body, false)
            bindRow(row, iface)
            body.addView(row)
        }
    }

    private fun onTimeout() {
        busy = false
        if (!alive()) return
        progress.visibility = View.GONE
        body.alpha = 1f
        body.removeAllViews()
        empty.setText(R.string.term_op_timeout)
        empty.visibility = View.VISIBLE
    }

    private fun bindRow(row: View, iface: SuUtils.Iface) {
        val name = row.findViewById<TextView>(R.id.iface_name)
        val typeChip = row.findViewById<TextView>(R.id.iface_type)
        val toggle = row.findViewById<MaterialButton>(R.id.iface_toggle)

        name.text = iface.name
        val monitor = iface.isMonitor
        val color = ContextCompat.getColor(
            activity,
            if (monitor) R.color.term_chip_monitor_text else R.color.term_chip_managed_text
        )

        typeChip.setText(if (monitor) R.string.term_iface_type_monitor else R.string.term_iface_type_managed)
        typeChip.setTextColor(color)
        typeChip.backgroundTintList = tintLow(color)

        toggle.setText(if (monitor) R.string.term_monitor_disable else R.string.term_monitor_enable)
        toggle.setTextColor(color)
        toggle.strokeColor = ColorStateList.valueOf(color)
        toggle.iconTint = ColorStateList.valueOf(color)
        toggle.setOnClickListener {
            if (busy) return@setOnClickListener
            busy = true
            subtitle.setText(R.string.term_monitor_working)
            toggle.setText(R.string.term_monitor_working)
            body.alpha = 0.45f
            for (i in 0 until body.childCount) body.getChildAt(i).isEnabled = false
            arm()
            SuUtils.setMonitorMode(activity, iface.name, !monitor) { refresh() }
        }
    }
}

private class UsbDialog(private val activity: Activity) {

    private val root: View =
        LayoutInflater.from(activity).inflate(R.layout.dialog_tools_list, null)
    private val subtitle = root.findViewById<TextView>(R.id.dialog_subtitle)
    private val progress = root.findViewById<ProgressBar>(R.id.dialog_progress)
    private val empty = root.findViewById<TextView>(R.id.dialog_empty)
    private val body = root.findViewById<LinearLayout>(R.id.dialog_body)

    private val dialog: AlertDialog = MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.term_usb_dialog_title)
        .setIcon(R.drawable.ic_usb_device)
        .setView(root)
        .setNeutralButton(R.string.term_dialog_refresh, null)
        .setPositiveButton(R.string.term_dialog_close, null)
        .create()

    private val handler = Handler(Looper.getMainLooper())
    private val watchdog = Runnable { onTimeout() }
    private var busy = false

    fun show() {
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { if (!busy) refresh() }
            refresh()
        }
        dialog.setOnDismissListener { handler.removeCallbacks(watchdog) }
        dialog.show()
    }

    private fun alive() = !activity.isFinishing && !activity.isDestroyed && dialog.isShowing

    private fun arm() {
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WirelessDialogs.OP_TIMEOUT_MS)
    }

    private fun refresh() {
        if (activity.isFinishing || activity.isDestroyed) return
        busy = true
        body.removeAllViews()
        empty.visibility = View.GONE
        progress.visibility = View.VISIBLE
        subtitle.setText(R.string.term_usb_scanning)
        arm()
        SuUtils.getUsbDevicesDetailed(activity) { list -> render(list) }
    }

    private fun render(list: ArrayList<SuUtils.UsbInfo>) {
        handler.removeCallbacks(watchdog)
        busy = false
        if (!alive()) return
        progress.visibility = View.GONE
        body.removeAllViews()
        subtitle.text = activity.getString(R.string.term_usb_count, list.size)
        if (list.isEmpty()) {
            empty.setText(R.string.term_usb_empty)
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE
        val inflater = LayoutInflater.from(activity)
        val accent = ContextCompat.getColor(activity, R.color.term_accent_bright)
        for (dev in list) {
            val row = inflater.inflate(R.layout.dialog_usb_row, body, false)
            val desc = row.findViewById<TextView>(R.id.usb_desc)
            val location = row.findViewById<TextView>(R.id.usb_location)
            val id = row.findViewById<TextView>(R.id.usb_id)
            desc.text = if (dev.description.isEmpty()) dev.id else dev.description
            location.text = dev.location
            id.text = dev.id
            id.backgroundTintList = tintLow(accent)
            body.addView(row)
        }
    }

    private fun onTimeout() {
        busy = false
        if (!alive()) return
        progress.visibility = View.GONE
        body.removeAllViews()
        empty.setText(R.string.term_op_timeout)
        empty.visibility = View.VISIBLE
    }
}
