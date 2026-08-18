package de.robinrehbein.punkt.ui.data

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun deviceHourAndMonth(): Pair<Int, Int> {
    val jetzt = deviceCalendar()
    return jetzt.hour to jetzt.month
}

actual fun deviceCalendar(): DeviceCalendar {
    val teile = NSCalendar.currentCalendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour,
        fromDate = NSDate()
    )
    val jahr = teile.year.toInt()
    val monat = teile.month.toInt()
    return DeviceCalendar(
        epochDay = epochDay(jahr, monat, teile.day.toInt()),
        month = monat,
        year = jahr,
        hour = teile.hour.toInt()
    )
}

/**
 * Tage seit 1970-01-01 aus einem Kalenderdatum — dieselbe Zahl, die
 * Javas `LocalDate.toEpochDay()` liefert.
 *
 * Gerechnet statt erfragt: `NSCalendar` gibt Jahr, Monat und Tag bereits
 * in der lokalen Zeitzone zurueck, und der Rest ist reine Arithmetik. Der
 * Weg ueber den Zonenversatz waere kuerzer gewesen, haengt aber an einer
 * Bindung, die sich nur auf einem Mac pruefen laesst — diese hier laesst
 * sich lesen.
 *
 * Das Verfahren verschiebt den Jahresanfang auf den 1. Maerz, damit der
 * Schalttag ans Jahresende faellt und keine Sonderfaelle macht.
 */
private fun epochDay(jahr: Int, monat: Int, tag: Int): Long {
    val y = if (monat <= 2) jahr - 1 else jahr
    val aera = (if (y >= 0) y else y - 399) / 400
    val jahrInAera = y - aera * 400                       // 0..399
    val tagImJahr = (153 * (monat + (if (monat > 2) -3 else 9)) + 2) / 5 + tag - 1
    val tagInAera = jahrInAera * 365 + jahrInAera / 4 - jahrInAera / 100 + tagImJahr
    return (aera.toLong() * 146097L + tagInAera.toLong() - 719468L)
}
