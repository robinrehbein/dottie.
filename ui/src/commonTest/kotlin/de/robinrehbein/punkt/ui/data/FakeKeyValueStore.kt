package de.robinrehbein.punkt.ui.data

/**
 * Ein Speicher im Arbeitsspeicher — genau das, was [GameStore] von einer
 * Plattform erwartet. Damit sind die Spielstands-Regeln ohne Android und
 * ohne iOS pruefbar; das war der Sinn der [KeyValueStore]-Schnittstelle.
 *
 * Er steht in einer eigenen Datei, weil inzwischen mehr als ein Test ihn
 * braucht (Abgleich mit der Uhr, Twist-Erklaerungen) — und weil ein
 * zweiter, leicht abweichender Nachbau genau die Art Unterschied waere,
 * die man erst am Geraet merkt.
 */
internal class FakeKeyValueStore : KeyValueStore {

    private val werte = mutableMapOf<String, Any>()

    override fun int(key: String, fallback: Int): Int = werte[key] as? Int ?: fallback

    override fun long(key: String, fallback: Long): Long = werte[key] as? Long ?: fallback

    override fun boolean(key: String, fallback: Boolean): Boolean =
        werte[key] as? Boolean ?: fallback

    override fun string(key: String): String? = werte[key] as? String

    override fun edit(block: KeyValueEditor.() -> Unit) {
        object : KeyValueEditor {
            override fun putInt(key: String, value: Int) { werte[key] = value }
            override fun putLong(key: String, value: Long) { werte[key] = value }
            override fun putBoolean(key: String, value: Boolean) { werte[key] = value }
            override fun putString(key: String, value: String) { werte[key] = value }
            override fun remove(key: String) { werte.remove(key) }
        }.block()
    }
}
