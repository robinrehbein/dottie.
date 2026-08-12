package de.robinrehbein.punkt.ads

/**
 * Frequenz-Deckel für Interstitials — bewusst als reine Kotlin-Logik
 * ohne Android-Bezug, damit die Regel testbar bleibt statt im
 * AdMob-Wrapper zu verschwinden.
 *
 * Zwei Bremsen, beide müssen erfüllt sein:
 * 1. Die ersten [MIN_DEATHS] Tode einer Sitzung bleiben werbefrei — wer
 *    gerade erst angefangen hat, soll das Spiel kennenlernen und nicht
 *    nach dem ersten Fehlversuch abgewürgt werden.
 * 2. Danach mindestens [MIN_INTERVAL_MILLIS] Abstand zum letzten
 *    gezeigten Spot. Dottie.-Läufe dauern oft nur Sekunden — ohne
 *    Zeitfenster käme sonst nach jedem zweiten Lauf Werbung.
 *
 * Die Uhr wird injiziert ([nowMillis]), damit Tests keine echte Zeit
 * abwarten müssen. Die Instanz lebt genau eine App-Sitzung: der
 * Todeszähler startet bei jedem Kaltstart neu.
 */
class InterstitialGate(private val nowMillis: () -> Long) {

    /** Tode in dieser Sitzung — die Zählung startet bei jedem App-Start neu. */
    var deathCount: Int = 0
        private set

    /** Zeitpunkt des zuletzt gezeigten Interstitials, null = noch keins. */
    private var lastShownAt: Long? = null

    /**
     * Meldet einen Tod und beantwortet in einem Rutsch, ob dafür ein
     * Interstitial gezeigt werden darf. Bewusst kombiniert: So kann der
     * Zähler nicht aus Versehen doppelt oder gar nicht hochlaufen.
     */
    fun onDeathShouldShow(): Boolean {
        deathCount++
        if (deathCount < MIN_DEATHS) return false
        val last = lastShownAt ?: return true
        return nowMillis() - last >= MIN_INTERVAL_MILLIS
    }

    /**
     * Bestätigt, dass wirklich ein Spot gelaufen ist. Getrennt vom
     * Fragen, weil der Spot trotz Erlaubnis ausfallen kann (nichts
     * geladen, kein Consent) — dann darf das Zeitfenster nicht
     * verbrannt werden.
     */
    fun markShown() {
        lastShownAt = nowMillis()
    }

    companion object {
        /** Ab dem wievielten Tod einer Sitzung Werbung erlaubt ist. */
        const val MIN_DEATHS = 4

        /** Mindestabstand zwischen zwei Interstitials. */
        const val MIN_INTERVAL_MILLIS = 90_000L
    }
}
