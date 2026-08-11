import Foundation

/// 1:1-Port von core/.../DailyChallenge.kt: Ein Kalendertag bestimmt einen
/// festen Zufalls-Seed — alle Spieler:innen (und alle Versuche des Tages)
/// bekommen dieselbe Zonen- und Twist-Abfolge, plattformübergreifend
/// identisch zwischen Android und iOS.
enum DailyChallenge {

    /// Seed für einen Kalendertag. Der Epoch-Day wird mit einer großen
    /// Primzahl gespreizt — exakt wie in Kotlin:
    /// `epochDay * 0x9E3779B97F4A7C15UL.toLong()` (Long-Überlauf wrappt).
    static func seedFor(epochDay: Int64) -> Int64 {
        return epochDay &* Int64(bitPattern: 0x9E3779B97F4A7C15 as UInt64)
    }

    /// Fortschreibung der Tages-Serie beim ersten Daily-Lauf eines Tages:
    /// direkt aufeinanderfolgende Tage zählen hoch, derselbe Tag ändert
    /// nichts, eine Lücke setzt auf 1 zurück. `lastPlayedEpochDay <= 0`
    /// heißt: noch nie gespielt.
    static func nextStreak(
        lastPlayedEpochDay: Int64,
        currentStreak: Int,
        todayEpochDay: Int64
    ) -> Int {
        if lastPlayedEpochDay <= 0 {
            return 1
        }
        if todayEpochDay == lastPlayedEpochDay {
            return max(currentStreak, 1)
        }
        if todayEpochDay == lastPlayedEpochDay + 1 {
            return max(currentStreak, 0) + 1
        }
        return 1
    }

    /// Heutiger Kalendertag als Epoch-Day, äquivalent zu Javas
    /// `LocalDate.now().toEpochDay()`: Tage seit 1970-01-01 in der
    /// lokalen Zeitzone.
    static func todayEpochDay(date: Date = Date()) -> Int64 {
        let offset = TimeZone.current.secondsFromGMT(for: date)
        let localSeconds = date.timeIntervalSince1970 + Double(offset)
        return Int64(floor(localSeconds / 86400.0))
    }
}
