package com.example

import android.content.Context

object BindingRepository {
    private const val PREFS_NAME = "octomap_prefs"

    fun saveBindings(context: Context, packageName: String, bindings: List<KeyBinding>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = bindings.joinToString(";") { "${it.keyCode},${it.keyName},${it.x},${it.y}" }
        prefs.edit().putString("bindings_$packageName", str).apply()
    }

    fun loadBindings(context: Context, packageName: String): MutableList<KeyBinding> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = prefs.getString("bindings_$packageName", "") ?: ""
        if (str.isEmpty()) return mutableListOf()
        return str.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size == 4) {
                try {
                    KeyBinding(parts[0].toInt(), parts[1], parts[2].toFloat(), parts[3].toFloat())
                } catch(e: Exception) { null }
            } else null
        }.toMutableList()
    }

    fun getPresets(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("preset_names", emptySet())?.toList() ?: emptyList()
    }

    fun savePreset(context: Context, presetName: String, bindings: List<KeyBinding>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = bindings.joinToString(";") { "${it.keyCode},${it.keyName},${it.x},${it.y}" }
        prefs.edit().putString("preset_$presetName", str).apply()
        
        val presets = getPresets(context).toMutableSet()
        presets.add(presetName)
        prefs.edit().putStringSet("preset_names", presets).apply()
    }

    fun loadPreset(context: Context, presetName: String): MutableList<KeyBinding> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = prefs.getString("preset_$presetName", "") ?: ""
        if (str.isEmpty()) return mutableListOf()
        return str.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size == 4) {
                try {
                    KeyBinding(parts[0].toInt(), parts[1], parts[2].toFloat(), parts[3].toFloat())
                } catch(e: Exception) { null }
            } else null
        }.toMutableList()
    }
}
