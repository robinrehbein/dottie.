package de.robinrehbein.punkt.ui.data

/**
 * Die Uhr des Geraets in Millisekunden seit 1970.
 *
 * Kotlins Standardbibliothek hat dafuer keine gemeinsame Funktion —
 * `System.currentTimeMillis()` gibt es nur auf der JVM. Gebraucht wird
 * sie fuer die Zeitstempel der Auswahl (Skin, Kulisse, Ton-Set): Beim
 * Abgleich mit der Uhr gewinnt die neuere Entscheidung, nicht die
 * "groessere".
 */
expect fun epochMillis(): Long

/**
 * Stunde (0-23) und Kalendermonat (1-12) des Geraets.
 *
 * TAGESZEIT und JAHRESZEIT ziehen daraus ihr Kleid — es sind Eingaben
 * fuer eine Spielregel, keine Spielregel. Deshalb hier und nicht in
 * `:core`, und deshalb je Plattform eigen: Auf der JVM ist es
 * `LocalDateTime`, auf iOS `NSCalendar`.
 */
expect fun deviceHourAndMonth(): Pair<Int, Int>

/**
 * Der Kalender des Geraets in einer Ablesung.
 *
 * Alle vier Werte zusammen, weil sie zusammengehoeren: Ein Lauf um
 * Mitternacht darf seinen Tag nicht aus der einen und seinen Monat nicht
 * aus der naechsten Ablesung bekommen.
 */
data class DeviceCalendar(
    /** Tage seit 1970-01-01 in der lokalen Zeitzone. */
    val epochDay: Long,
    /** Kalendermonat 1-12. */
    val month: Int,
    val year: Int,
    /** Stunde 0-23. */
    val hour: Int
)

expect fun deviceCalendar(): DeviceCalendar
