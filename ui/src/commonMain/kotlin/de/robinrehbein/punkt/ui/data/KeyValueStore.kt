package de.robinrehbein.punkt.ui.data

/**
 * Ein Schluessel-Wert-Speicher — die einzige Plattform-Annahme, die
 * [GameStore] macht.
 *
 * Android legt das auf SharedPreferences, iOS auf NSUserDefaults. Beide
 * koennen genau das: eine Handvoll Zahlen und Zeichenketten synchron
 * lesen und schreiben. Mehr braucht dieses Spiel nicht, und weniger
 * anzunehmen macht die Spielstands-Regeln teilbar.
 */
interface KeyValueStore {

    fun int(key: String, fallback: Int = 0): Int

    fun long(key: String, fallback: Long = 0L): Long

    fun boolean(key: String, fallback: Boolean = false): Boolean

    fun string(key: String): String?

    /**
     * Mehrere Schreibvorgaenge als ein Block.
     *
     * Die Klammer ist kein Ziergitter: `submitRun` schreibt acht Werte,
     * die zusammengehoeren — Rekord, Laufzahl, Tageszaehler, Saison. Ein
     * Absturz mittendrin duerfte keinen halben Lauf hinterlassen.
     */
    fun edit(block: KeyValueEditor.() -> Unit)
}

/** Die Schreibseite von [KeyValueStore]. */
interface KeyValueEditor {
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String)
}
