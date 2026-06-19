package com.stryker.terminal.utils

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stryker.terminal.R

object NeoPermission {
  const val REQUEST_APP_PERMISSION = 10086

  fun initAppPermission(context: AppCompatActivity, requestCode: Int) {
    if (ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
      )
      != PackageManager.PERMISSION_GRANTED
    ) {

      if (ActivityCompat.shouldShowRequestPermissionRationale(
          context,
          Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
      ) {
        MaterialAlertDialogBuilder(context).setMessage("Please allow storage permissions")
          .setPositiveButton(android.R.string.ok, { _: DialogInterface, _: Int ->
            doRequestPermission(context, requestCode)
          })
          .show()

      } else {
        doRequestPermission(context, requestCode)
      }
    }
  }

  private fun doRequestPermission(context: AppCompatActivity, requestCode: Int) {
    try {
      ActivityCompat.requestPermissions(
        context,
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        requestCode
      )
    } catch (ignore: ActivityNotFoundException) {
    }
  }
}
