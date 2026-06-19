package com.stryker.terminal.component.colorscheme

import android.content.Context
import android.os.Build
import io.neolang.frontend.ConfigVisitor
import com.stryker.terminal.App
import com.stryker.terminal.R
import com.stryker.terminal.component.ComponentManager
import com.stryker.terminal.component.ConfigFileBasedComponent
import com.stryker.terminal.component.codegen.CodeGenComponent
import com.stryker.terminal.component.config.NeoPreference
import com.stryker.terminal.component.config.NeoTermPath
import com.stryker.terminal.frontend.session.view.TerminalView
import com.stryker.terminal.frontend.session.view.extrakey.ExtraKeysView
import com.stryker.terminal.utils.extractAssetsDir
import java.io.File
import java.nio.file.Files

class ColorSchemeComponent : ConfigFileBasedComponent<NeoColorScheme>(NeoTermPath.COLORS_PATH) {
  companion object {
    fun colorFile(colorName: String): File {
      return File("${NeoTermPath.COLORS_PATH}/$colorName.nl")
    }
  }

  override val checkComponentFileWhenObtained
    get() = true

  private lateinit var DEFAULT_COLOR: NeoColorScheme
  private var colors: MutableMap<String, NeoColorScheme> = mutableMapOf()

  override fun onCheckComponentFiles() {
    val defaultColorFile = colorFile(DefaultColorScheme.colorName)
    if (!defaultColorFile.exists()) {
      if (!extractDefaultColor(App.get())) {
        DEFAULT_COLOR = DefaultColorScheme
        colors[DEFAULT_COLOR.colorName] = DEFAULT_COLOR
        return
      }
    }

    if (!reloadColorSchemes()) {
      DEFAULT_COLOR = DefaultColorScheme
      colors[DEFAULT_COLOR.colorName] = DEFAULT_COLOR
    }
  }

  override fun onCreateComponentObject(configVisitor: ConfigVisitor) = NeoColorScheme()

  fun reloadColorSchemes(): Boolean {
    colors.clear()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      File(baseDir)
        .listFiles(NEOLANG_FILTER)
        .mapNotNull { this.loadConfigure(it) }
        .forEach {
          colors.put(it.colorName, it)
        }
      if (colors.containsKey(DefaultColorScheme.colorName)) {
        DEFAULT_COLOR = colors[DefaultColorScheme.colorName]!!
        return true
      }
      return false
    } else {
      return false
    }
  }

  fun applyColorScheme(view: TerminalView?, extraKeysView: ExtraKeysView?, colorScheme: NeoColorScheme?) {
    colorScheme?.applyColorScheme(view, extraKeysView)
  }

  fun getCurrentColorScheme(): NeoColorScheme {
    return colors[getCurrentColorSchemeName()]!!
  }

  fun getCurrentColorSchemeName(): String {
    var currentColorName =
      NeoPreference.loadString(R.string.key_customization_color_scheme, DefaultColorScheme.colorName)
    if (!colors.containsKey(currentColorName)) {
      currentColorName = DefaultColorScheme.colorName
      NeoPreference.store(R.string.key_customization_color_scheme, DefaultColorScheme.colorName)
    }
    return currentColorName
  }

  fun getColorScheme(colorName: String): NeoColorScheme {
    return if (colors.containsKey(colorName)) colors[colorName]!! else getCurrentColorScheme()
  }

  fun getColorSchemeNames(): List<String> {
    val list = ArrayList<String>()
    list += colors.keys
    return list
  }

  fun setCurrentColorScheme(colorName: String) {
    NeoPreference.store(R.string.key_customization_color_scheme, colorName)
  }

  fun setCurrentColorScheme(color: NeoColorScheme) {
    setCurrentColorScheme(color.colorName)
  }

  private fun extractDefaultColor(context: Context) =
    kotlin.runCatching { context.extractAssetsDir("colors", baseDir) }.isSuccess

  fun saveColorScheme(colorScheme: NeoColorScheme) {
    val colorFile = colorFile(colorScheme.colorName)
    if (colorFile.exists()) {
      throw RuntimeException("ColorScheme already ${colorScheme.colorName} exists!")
    }

    val component = ComponentManager.getComponent<CodeGenComponent>()
    val content = component.newGenerator(colorScheme).generateCode(colorScheme)

    kotlin.runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Files.write(colorFile.toPath(), content.toByteArray())
      } else {
        return
      }
    }.onFailure {
      throw RuntimeException("Failed to save file ${colorFile.absolutePath}")
    }
  }
}

