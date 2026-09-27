package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.Twist

/**
 * Die drei Wähler der Uhr — eine kleine Sammlung: Vogel, Welt und Ton.
 * Wie am Telefon lässt sich nur wählen, was freigeschaltet ist; anders
 * als dort gibt es kein Schaufenster für Gesperrtes (siehe README,
 * „Wear-OS“): Auf dem runden Display wäre eine Liste mit Bedingungen und
 * Balken nicht mehr bedienbar.
 */
internal enum class WearPickerKind { SKIN, SCENE, SOUND }

/**
 * Der Cursor eines Wählers, ohne Compose und ohne Prefs: Die Krone
 * schiebt ihn zyklisch durch die Liste der wählbaren Einträge.
 */
internal object WearPickerCursor {

    /**
     * Der Eintrag [steps] Plätze nach [current], zyklisch. Steht [current]
     * nicht in der Liste (z. B. eine Welt, die nur über den Abgleich kam
     * und hier noch nicht als offen gilt), zählt der erste Eintrag als
     * Ausgangspunkt. Bei leerer Liste bleibt es bei [current].
     */
    fun <T> step(list: List<T>, current: T, steps: Int): T {
        if (list.isEmpty()) return current
        val at = list.indexOf(current).coerceAtLeast(0)
        val size = list.size
        return list[((at + steps) % size + size) % size]
    }
}

/**
 * Welcher Twist nach einem Tod erklärt wird — dieselbe Regel wie
 * TwistLessons am Telefon (:ui), hier ohne Abhängigkeit auf :ui: der
 * erste in diesem Lauf freigeschaltete Twist, den das Gerät noch nie
 * erklärt hat; nach einem Tod in den Bomben drängelt deren Erklärung vor.
 * Einer je Tod, gespeichert wird lokal (Didaktik, kein Fortschritt — wie
 * am Telefon nicht im Abgleich).
 */
internal object WearLessons {

    fun next(unlockedThisRun: List<Twist>, explained: Set<String>, diedInTrap: Boolean): Twist? {
        if (diedInTrap && Twist.FAKE.name !in explained) return Twist.FAKE
        return unlockedThisRun.firstOrNull { it.name !in explained }
    }

    /** "FAKE,CHAIN" → {FAKE, CHAIN}; gleiche Schreibweise wie am Telefon. */
    fun decode(stored: String?): Set<String> =
        stored?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun encode(explained: Collection<String>): String = explained.joinToString(",")
}
