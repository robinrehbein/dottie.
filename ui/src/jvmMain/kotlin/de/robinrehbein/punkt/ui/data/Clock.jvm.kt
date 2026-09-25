package de.robinrehbein.punkt.ui.data

/**
 * Feste Uhr für Werkzeuge: Die JVM ist kein Auslieferungsziel, sie
 * zeichnet nur Screenshots (ui/src/jvmTest). Damit zwei Läufe dieselben
 * Bilder liefern, darf dort weder die Stunde (TAGESZEIT) noch der Monat
 * (JAHRESZEIT) von der echten Uhr kommen. null = echte Uhr.
 */
internal var fixedCalendarForTools: DeviceCalendar? = null

actual fun epochMillis(): Long = System.currentTimeMillis()

actual fun deviceHourAndMonth(): Pair<Int, Int> {
    fixedCalendarForTools?.let { return it.hour to it.month }
    val now = java.time.LocalDateTime.now()
    return now.hour to now.monthValue
}

actual fun deviceCalendar(): DeviceCalendar {
    fixedCalendarForTools?.let { return it }
    val now = java.time.LocalDateTime.now()
    return DeviceCalendar(
        epochDay = now.toLocalDate().toEpochDay(),
        month = now.monthValue,
        year = now.year,
        hour = now.hour
    )
}
