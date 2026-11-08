package com.superrouter.app

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class FormFactor { Phone, Tablet }

object DeviceMode {
    fun of(context: Context): FormFactor {
        val orientation = context.resources.configuration.orientation
        return if (orientation == Configuration.ORIENTATION_LANDSCAPE) FormFactor.Tablet else FormFactor.Phone
    }
}

object UiState {
    var phoneKind  by mutableStateOf(0)
    var tabletKind by mutableStateOf(0)
    var phoneStaticPath  by mutableStateOf<String?>(null)
    var phoneDynamicPath by mutableStateOf<String?>(null)
    var tabletStaticPath  by mutableStateOf<String?>(null)
    var tabletDynamicPath by mutableStateOf<String?>(null)

    fun load(context: Context) {
        val p = context.getSharedPreferences("sr_ui", Context.MODE_PRIVATE)
        phoneKind = p.getInt("phoneKind", 0)
        tabletKind = p.getInt("tabletKind", 0)
        phoneStaticPath  = p.getString("phoneStatic", null)
        phoneDynamicPath = p.getString("phoneDynamic", null)
        tabletStaticPath  = p.getString("tabletStatic", null)
        tabletDynamicPath = p.getString("tabletDynamic", null)
    }

    fun save(context: Context) {
        context.getSharedPreferences("sr_ui", Context.MODE_PRIVATE).edit()
            .putInt("phoneKind", phoneKind)
            .putInt("tabletKind", tabletKind)
            .putString("phoneStatic", phoneStaticPath)
            .putString("phoneDynamic", phoneDynamicPath)
            .putString("tabletStatic", tabletStaticPath)
            .putString("tabletDynamic", tabletDynamicPath)
            .apply()
    }
}
