package de.robinrehbein.punkt.ui.data

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun deviceHourAndMonth(): Pair<Int, Int> {
    val teile = NSCalendar.currentCalendar.components(
        NSCalendarUnitHour or NSCalendarUnitMonth,
        fromDate = NSDate()
    )
    return teile.hour.toInt() to teile.month.toInt()
}
