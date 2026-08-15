package de.robinrehbein.punkt.ui.data

actual fun epochMillis(): Long = System.currentTimeMillis()

actual fun deviceHourAndMonth(): Pair<Int, Int> {
    val now = java.time.LocalDateTime.now()
    return now.hour to now.monthValue
}
