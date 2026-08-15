package de.robinrehbein.punkt.ui.data

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
