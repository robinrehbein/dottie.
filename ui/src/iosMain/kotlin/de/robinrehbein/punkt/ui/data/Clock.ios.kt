package de.robinrehbein.punkt.ui.data

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMTForDate
import kotlin.math.floor
import platform.Foundation.timeIntervalSince1970

actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun deviceHourAndMonth(): Pair<Int, Int> {
    val teile = NSCalendar.currentCalendar.components(
        NSCalendarUnitHour or NSCalendarUnitMonth,
        fromDate = NSDate()
    )
    return teile.hour.toInt() to teile.month.toInt()
}

actual fun deviceCalendar(): DeviceCalendar {
    val teile = NSCalendar.currentCalendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour,
        fromDate = NSDate()
    )
    // Epoch-Day aus der lokalen Zeitzone, wie Javas LocalDate.toEpochDay:
    // Sekunden seit 1970 plus Zonenversatz, abgerundet auf ganze Tage.
    val jetzt = NSDate()
    val versatz = NSTimeZone.localTimeZone.secondsFromGMTForDate(jetzt)
    val lokal = jetzt.timeIntervalSince1970 + versatz.toDouble()
    return DeviceCalendar(
        epochDay = floor(lokal / 86400.0).toLong(),
        month = teile.month.toInt(),
        year = teile.year.toInt(),
        hour = teile.hour.toInt()
    )
}
