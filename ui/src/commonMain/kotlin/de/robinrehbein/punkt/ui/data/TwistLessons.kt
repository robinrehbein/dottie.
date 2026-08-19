package de.robinrehbein.punkt.ui.data

import de.robinrehbein.punkt.game.Twist

/**
 * Wer welchen Twist schon einmal erklaert bekommen hat.
 *
 * Bis v2.24 kuendigte ein Banner mitten im Lauf jeden frisch
 * freigeschalteten Twist an ("NEU: FALLEN-ZONE!") — und zwar in jedem
 * Lauf aufs Neue. Gelesen hat das niemand: Waehrend der Punkt kreist,
 * klebt der Blick am Ring, und wer die Falle zum zwanzigsten Mal
 * freischaltet, braucht keine Ansage mehr. Die Erklaerung steht deshalb
 * jetzt im Game-Over, wo ohnehin gelesen wird — und dort genau einmal je
 * Twist.
 *
 * Persistiert wird ueber die NAMEN, nicht ueber die Ordinale: `Twist` in
 * `:core` sagt nirgends zu, dass seine Reihenfolge stabil bleibt (anders
 * als `Season`, das sein Bit ausdruecklich selbst mitbringt). Eine Maske
 * ueber `ordinal` wuerde bei jedem Einschub in die Aufzaehlung still die
 * Bedeutung gespeicherter Staende verschieben — und ausgerechnet die
 * Falle waere dann die, die niemand mehr erklaert bekommt.
 */
object TwistLessons {

    /**
     * Der Twist, der jetzt erklaert wird: der erste des Laufs, den noch
     * nie jemand erklaert bekommen hat — oder null, wenn dieser Lauf
     * nichts Neues gebracht hat.
     *
     * Bewusst nur EINER je Tod. Das Game-Over ist der Atemzug vor dem
     * naechsten Versuch, keine Lehrstunde; die uebrigen Twists kommen bei
     * den naechsten Toden dran.
     */
    fun next(unlockedThisRun: List<Twist>, explained: Set<String>): Twist? =
        unlockedThisRun.firstOrNull { it.name !in explained }

    /** "FAKE,CHAIN" → {FAKE, CHAIN}. Leer und null heissen dasselbe. */
    fun decode(stored: String?): Set<String> =
        stored?.split(SEPARATOR)?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    /** Und zurueck. Die Reihenfolge ist die des Lernens, nicht die der Aufzaehlung. */
    fun encode(explained: Collection<String>): String = explained.joinToString(SEPARATOR)

    private const val SEPARATOR = ","
}
