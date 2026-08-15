package de.robinrehbein.punkt.ui.data

import android.content.Context
import android.content.SharedPreferences

/**
 * [KeyValueStore] auf SharedPreferences — synchron und simpel, genau
 * richtig fuer eine Handvoll Zahlen.
 *
 * Der Dateiname traegt weiterhin "punkt_scores": Er ist der Ort, an dem
 * die Bestleistungen aller bisherigen Installationen liegen. Ein anderer
 * Name waere ein stiller Datenverlust bei jedem Update.
 */
class AndroidKeyValueStore(context: Context, name: String = "punkt_scores") : KeyValueStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun int(key: String, fallback: Int): Int = prefs.getInt(key, fallback)

    override fun long(key: String, fallback: Long): Long = prefs.getLong(key, fallback)

    override fun boolean(key: String, fallback: Boolean): Boolean =
        prefs.getBoolean(key, fallback)

    override fun string(key: String): String? = prefs.getString(key, null)

    override fun edit(block: KeyValueEditor.() -> Unit) {
        val editor = prefs.edit()
        object : KeyValueEditor {
            override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
            override fun putLong(key: String, value: Long) { editor.putLong(key, value) }
            override fun putBoolean(key: String, value: Boolean) { editor.putBoolean(key, value) }
            override fun putString(key: String, value: String) { editor.putString(key, value) }
        }.block()
        editor.apply()
    }
}
