import DottieCore
import Foundation
import UserNotifications

/// Tägliche Daily-Challenge-Erinnerung — komplett lokal, ohne jeden
/// Server-Kontakt. Port von notify/DailyReminder.kt, aber mit einem
/// anderen Mechanismus, weil die Plattformen sich hier grundlegend
/// unterscheiden:
///
/// Android lässt einen Hintergrund-Job laufen, der abends *nachschaut*,
/// ob die heutige Daily schon gespielt wurde, und nur dann meldet. Unter
/// iOS gibt es das nicht — eine Benachrichtigung wird im Voraus geplant
/// und feuert dann, egal was inzwischen passiert ist.
///
/// Deshalb dreht diese Fassung die Logik um: Sie plant die nächsten paar
/// Tage im Voraus und **plant sie jedes Mal neu**, wenn die App geöffnet
/// oder eine Daily gespielt wird ([refresh]). Wer heute schon gespielt
/// hat, verliert damit die heutige Erinnerung wieder. Das Ergebnis fühlt
/// sich an wie am Phone, ohne dass im Hintergrund etwas laufen müsste.
enum DailyReminder {

    /// Uhrzeit der Erinnerung — abends, wenn der Tag noch zu retten ist.
    /// Gleicher Wert wie REMINDER_TIME am Phone.
    private static let reminderHour = 18

    /// Wie viele Tage im Voraus geplant werden. iOS erlaubt 64 wartende
    /// Benachrichtigungen pro App; sieben sind reichlich, weil bei jedem
    /// Öffnen ohnehin neu geplant wird. Wer die App eine Woche lang gar
    /// nicht öffnet, braucht auch keine Erinnerung mehr.
    private static let daysAhead = 7

    private static let identifierPrefix = "daily-reminder-"

    /// Fragt die Berechtigung an und meldet, ob sie erteilt wurde. Beim
    /// zweiten Aufruf zeigt iOS keinen Dialog mehr, sondern antwortet
    /// sofort mit der früheren Entscheidung — deshalb ist der Rückgabewert
    /// die einzige verlässliche Quelle dafür, ob der Schalter greifen darf.
    static func requestPermission(completion: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound]) { granted, _ in
                DispatchQueue.main.async { completion(granted) }
            }
    }

    /// Plant neu, was ansteht. Idempotent: Erst wird alles Wartende
    /// verworfen, dann frisch gesetzt — so kann sich nichts doppeln, egal
    /// wie oft die App geöffnet wird.
    static func refresh(store: ScoreStore) {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        guard store.reminderEnabled else { return }

        center.getNotificationSettings { settings in
            guard settings.authorizationStatus == .authorized ||
                    settings.authorizationStatus == .provisional else { return }
            schedule(store: store, center: center)
        }
    }

    /// Erinnerungen abbestellen (Schalter aus).
    static func cancel() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
    }

    private static func schedule(store: ScoreStore, center: UNUserNotificationCenter) {
        let calendar = Calendar.current
        let now = Date()
        let today = DailyChallenge.todayEpochDay()
        // Heute schon gespielt? Dann erst ab morgen erinnern — das ist die
        // Entsprechung zur Abfrage im Android-Worker.
        let playedToday = store.dailyDay == today
        let streakIfPlayedNow = store.dailyStreakPreviewFor(epochDay: today)

        for offset in 0..<daysAhead {
            guard let day = calendar.date(byAdding: .day, value: offset, to: now),
                  let fireDate = calendar.date(
                    bySettingHour: reminderHour, minute: 0, second: 0, of: day
                  )
            else { continue }
            if offset == 0 && (playedToday || fireDate <= now) { continue }

            let content = UNMutableNotificationContent()
            content.title = L10n.text("notif_title")
            // Nur für die nächste Erinnerung lässt sich die Serie
            // seriös beziffern; für spätere Tage wüssten wir nicht, ob
            // zwischendurch gespielt wurde. Lieber allgemein bleiben als
            // eine Zahl behaupten, die dann nicht stimmt.
            if offset <= 1 && streakIfPlayedNow > 1 {
                content.body = L10n.format("notif_text_streak", streakIfPlayedNow)
            } else if offset <= 1 && streakIfPlayedNow == 1 {
                content.body = L10n.text("notif_text_streak_one")
            } else {
                content.body = L10n.text("notif_text")
            }
            content.sound = .default

            let components = calendar.dateComponents(
                [.year, .month, .day, .hour, .minute], from: fireDate
            )
            let request = UNNotificationRequest(
                identifier: identifierPrefix + String(offset),
                content: content,
                trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            )
            center.add(request, withCompletionHandler: nil)
        }
    }
}
