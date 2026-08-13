package io.xystudio.floatspace

import android.content.Context

object Favorites {
    private const val PREF = "floatspace"
    private const val KEY = "favorite_components"
    fun get(context: Context): Set<String> = context.getSharedPreferences(PREF, 0).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
    fun toggle(context: Context, component: String): Boolean {
        val values = get(context).toMutableSet()
        val added = if (component in values) { values.remove(component); false } else { values.add(component); true }
        context.getSharedPreferences(PREF, 0).edit().putStringSet(KEY, values).apply()
        LocalLogger.info("Favorit ${if (added) "ditambah" else "dihapus"}: $component")
        return added
    }
}
