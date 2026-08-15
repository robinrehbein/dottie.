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
