package de.robinrehbein.punkt.ads

/**
 * Die Bremse hinter dem Nachladen von Anzeigen — wie [InterstitialGate]
 * bewusst reine Kotlin-Logik ohne Android-Bezug, damit die Regel testbar
 * bleibt statt im AdMob-Wrapper zu verschwinden.
 *
 * Warum es sie überhaupt braucht: Ein Ladeversuch scheitert regelmäßig,
 * ohne dass irgendetwas kaputt wäre — kein Netz beim App-Start, oder
 * "no fill", weil Google gerade keine Anzeige für diesen Block hat.
 * Vorher wurde nur beim SDK-Start und nach einem GEZEIGTEN Spot geladen;
 * ein Fehlschlag beim Laden hielt damit die ganze Sitzung an: kein
 * Zwischenspot mehr und, schlimmer, kein Tagespass-Angebot in der
 * Skin-Sammlung.
 *
 * Also darf jetzt nachgeladen werden, wo es ohnehin passt (Game-Over,
 * Öffnen der Sammlung) — aber nicht bei jeder Gelegenheit neu:
 *
 * 1. Ein laufender Versuch wird nicht verdoppelt. Sonst löst ein
 *    schneller Tod nach dem anderen einen Schwall paralleler Anfragen aus.
 * 2. Nach einem Fehlschlag vergeht [RETRY_MILLIS], bevor es einen neuen
 *    gibt. Bei dauerhaftem "no fill" bliebe es sonst bei Dauerfeuer —
 *    Funk und Akku für nichts.
 *
 * Die Uhr wird injiziert ([nowMillis]), damit Tests keine echte Zeit
 * abwarten müssen. Die Instanz lebt genau eine App-Sitzung.
 */
class AdLoadRetry(private val nowMillis: () -> Long) {

    /** Läuft gerade eine Anfrage? */
    private var loading = false

    /** Frühester Zeitpunkt für den nächsten Versuch; 0 = jederzeit. */
    private var blockedUntil = 0L

    /**
     * Darf jetzt geladen werden? Ein "ja" gilt zugleich als Start des
     * Versuchs — bewusst kombiniert, damit der Zustand nicht am Aufrufer
     * hängt und zwei Aufrufe nicht dieselbe Anfrage zweimal starten.
     */
    fun shouldStart(): Boolean {
        if (loading) return false
        if (nowMillis() < blockedUntil) return false
        loading = true
        return true
    }

    /** Anzeige ist da: Der Weg ist wieder frei für den nächsten Bedarf. */
    fun onLoaded() {
        loading = false
        blockedUntil = 0L
    }

    /** Fehlgeschlagen: Der nächste Versuch wartet [RETRY_MILLIS]. */
    fun onFailed() {
        loading = false
        blockedUntil = nowMillis() + RETRY_MILLIS
    }

    companion object {
        /**
         * Abstand nach einem Fehlschlag. Eine Minute ist lang genug, dass
         * ein dauerhaftes "no fill" nicht dauernd anklopft, und kurz
         * genug, dass ein Netz, das nach dem Start zurückkommt, in
         * derselben Sitzung noch bemerkt wird.
         */
        const val RETRY_MILLIS = 60_000L
    }
}
