package de.robinrehbein.punkt.ui.data

import platform.Foundation.NSUserDefaults

/**
 * [KeyValueStore] auf NSUserDefaults — das iOS-Gegenstueck zu
 * SharedPreferences.
 *
 * Ein Unterschied verdient einen Satz: NSUserDefaults kennt keinen
 * Standardwert. `integerForKey` auf einem unbekannten Schluessel liefert
 * 0, nicht "nichts". Wo ein anderer Rueckfallwert gilt — der Tag des
 * letzten Laufs ist `Long.MIN_VALUE`, nicht 0 —, waere das ein stiller
 * Fehler. Deshalb fragt jeder Leser erst [NSUserDefaults.objectForKey],
 * ob es den Schluessel ueberhaupt gibt.
 */
class IosKeyValueStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : KeyValueStore {

    private fun has(key: String): Boolean = defaults.objectForKey(key) != null

    override fun int(key: String, fallback: Int): Int =
        if (has(key)) defaults.integerForKey(key).toInt() else fallback

    override fun long(key: String, fallback: Long): Long =
        if (has(key)) defaults.integerForKey(key) else fallback

    override fun boolean(key: String, fallback: Boolean): Boolean =
        if (has(key)) defaults.boolForKey(key) else fallback

    override fun string(key: String): String? = defaults.stringForKey(key)

    override fun edit(block: KeyValueEditor.() -> Unit) {
        // NSUserDefaults schreibt sofort und puffert selbst; die Klammer
        // ist hier nur die gemeinsame Form, kein eigener Vorgang.
        object : KeyValueEditor {
            override fun putInt(key: String, value: Int) =
                defaults.setInteger(value.toLong(), key)

            override fun putLong(key: String, value: Long) =
                defaults.setInteger(value, key)

            override fun putBoolean(key: String, value: Boolean) =
                defaults.setBool(value, key)

            override fun putString(key: String, value: String) =

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
                defaults.setObject(value, key)
        }.block()
    }
}
