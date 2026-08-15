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
