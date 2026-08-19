@file:OptIn(ExperimentalForeignApi::class)

package de.robinrehbein.punkt.ui.platform

import de.robinrehbein.punkt.ui.data.GameStore
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDateComponents
import platform.Foundation.NSUserDefaults
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Die taegliche Daily-Challenge-Erinnerung auf dem iPhone.
 *
 * Das Gegenstueck zu `notify/DailyReminder.kt` in `:app` — dieselbe
 * Absicht, ein anderer Mechanismus, weil die Plattformen sich hier
 * grundlegend unterscheiden:
 *
 * Android laesst einen WorkManager-Job laufen, der abends *nachschaut*,
 * ob die heutige Daily schon gespielt wurde, und nur dann meldet. Unter
 * iOS gibt es diesen Hintergrund-Blick nicht: Eine Benachrichtigung wird
 * im Voraus geplant und feuert dann, egal was inzwischen passiert ist.
 * Deshalb steht hier eine einzige, sich wiederholende Anmeldung
 * ([UNCalendarNotificationTrigger] mit `repeats = true`) zur gleichen
 * Stunde wie am Telefon — und deshalb erinnert das iPhone auch an einem
 * Tag, an dem die Daily schon gespielt wurde. Das ist der bewusst
 * gezahlte Preis fuer eine Planung, die ohne jeden Hintergrund-Lauf
 * auskommt und die auch dann noch stimmt, wenn die App wochenlang nicht
 * geoeffnet wird.
 *
 * Aus demselben Grund steht im Text keine Serie: Der Inhalt einer
 * wiederholten Anmeldung wird einmal beim Planen festgelegt. „Serie: 5
 * Tage" waere ab dem ersten verpassten Tag eine Behauptung, die niemand
 * mehr prueft — der allgemeine Satz stimmt immer.
 *
 * **Berechtigung:** [UNUserNotificationCenter.requestAuthorizationWithOptions]
 * zeigt den Dialog nur beim ersten Mal; danach antwortet es sofort mit
 * der frueheren Entscheidung. Der Rueckgabewert ist damit die einzige
 * verlaessliche Quelle dafuer, ob der Schalter „AN" zeigen darf — genau
 * wie das Ergebnis des POST_NOTIFICATIONS-Dialogs auf Android.
 *
 * **Info.plist und project.yml bleiben unveraendert.** Lokale
 * Benachrichtigungen kennen keinen Usage-Description-Schluessel (den
 * gibt es nur fuer Kamera, Mikrofon, Ortung und Aehnliches) — die
 * Berechtigung wird ausschliesslich zur Laufzeit erfragt. Und
 * `UserNotifications.framework` wird wie `AVFAudio` (siehe [IosSounds])
 * ueber die Auto-Link-Angaben des Kotlin/Native-Frameworks gezogen;
 * `ios/project.yml` listet auch heute schon keine System-Frameworks.
 */
class IosReminder(private val store: GameStore) {

    /**
     * Der Schalter wurde umgelegt. Beim Einschalten haengt alles an der
     * Berechtigung: Ein aktivierter Schalter ohne Zustellung waere eine
     * Luege, deshalb meldet [onResult] genau das, was iOS erlaubt hat.
     * Beim Ausschalten wird die wartende Anmeldung entfernt.
     */
    fun set(wanted: Boolean, onResult: (Boolean) -> Unit) {
        if (!wanted) {
            cancel()
            onResult(false)
            return
        }
        arm(onResult)
    }

    /**
     * Beim Start auffrischen — das Gegenstueck zum idempotenten
     * `DailyReminder.schedule(context)` in der Android-Schale.
     *
     * Zwei Dinge klaert der Aufruf: Die Anmeldung steht wieder (sie
     * ueberlebt zwar App-Updates, aber nicht jede Neuinstallation), und
     * eine in den iOS-Einstellungen zurueckgenommene Berechtigung
     * schaltet den gespeicherten Wunsch ab — sonst behauptete der
     * Schalter beim naechsten Oeffnen des Blattes ein „AN", das keine
     * Benachrichtigung mehr einloest. Dasselbe prueft Android vor jedem
     * Anzeigen mit `areNotificationsEnabled()`.
     *
     * Der Aufruf fragt nur nach, wenn die Erinnerung ueberhaupt gewollt
     * ist; ohne Wunsch wird kein Dialog ausgeloest. Und wo schon einmal
     * entschieden wurde, kommt gar keiner mehr — der einzige Fall, in
     * dem hier doch einer erscheint, ist ein aus der Sicherung
     * zurueckgeholter Wunsch auf einem Geraet, das nie gefragt wurde.
     */
    fun refresh() {
        if (!store.reminderEnabled) {
            // Aufraeumen: Was ohne Wunsch noch wartet, hat hier nichts
            // mehr zu suchen (z. B. nach einem Abbruch beim Ausschalten).
            cancel()
            return
        }
        arm { granted ->
            if (!granted) store.reminderEnabled = false
        }
    }

    /**
     * Berechtigung erfragen und danach planen oder abbestellen.
     *
     * Der Rueckruf von iOS kommt auf einem beliebigen Faden. Was daran
     * haengt, landet deshalb per [dispatch_async] auf der Hauptschlange:
     * [onResult] fasst den Compose-Zustand des Einstellungs-Blattes an,
     * und Compose gehoert dem Haupt-Faden.
     */
    private fun arm(onResult: (Boolean) -> Unit) {
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(
                // Banner und Ton genuegen; ein Zaehler am App-Symbol
                // waere eine Zahl, die dieses Spiel nirgends fuehrt.
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound
            ) { granted, _ ->
                dispatch_async(dispatch_get_main_queue()) {
                    if (granted) schedule() else cancel()
                    onResult(granted)
                }
            }
    }

    /**
     * Plant die taegliche Erinnerung. Idempotent: Eine Anmeldung mit
     * bekanntem Bezeichner ersetzt die vorherige, statt sich zu doppeln.
     */
    private fun schedule() {
        val titel = text(TITLE_DE, TITLE_EN)
        val satz = text(BODY_DE, BODY_EN)
        val content = UNMutableNotificationContent().apply {
            setTitle(titel)
            setBody(satz)
            setSound(UNNotificationSound.defaultSound)
        }
        // Nur Stunde und Minute: Was offen bleibt, ist fuer den Kalender
        // ein Platzhalter — mit `repeats = true` passt das Muster damit
        // auf jeden Tag um REMINDER_HOUR:00. Die Minute steht bewusst
        // mit da; ohne sie waere auch jede andere Minute dieser Stunde
        // ein Treffer.
        val components = NSDateComponents().apply {
            hour = REMINDER_HOUR
            minute = 0
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            REQUEST_ID,
            content,
            UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, true)
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request, null)
    }

    /** Die wartende Anmeldung zuruecknehmen (Schalter aus). */
    private fun cancel() {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(REQUEST_ID))
    }

    /**
     * Deutsch oder Englisch — dieselbe Entscheidung, die Compose
     * Resources fuer alle anderen Texte trifft, hier von Hand: Die
     * Liste der bevorzugten Sprachen liegt in den Voreinstellungen
     * (`AppleLanguages`), gelesen ueber denselben Weg wie der ganze
     * Spielstand (siehe `IosKeyValueStore`).
     */
    private fun text(deutsch: String, englisch: String): String {
        val bevorzugt = NSUserDefaults.standardUserDefaults
            .stringArrayForKey(APPLE_LANGUAGES)
            ?.firstOrNull()
            ?.toString()
            .orEmpty()
        return if (bevorzugt.startsWith("de", ignoreCase = true)) deutsch else englisch
    }

    private companion object {

        /** Ein fester Bezeichner — die Erinnerung gibt es genau einmal. */
        const val REQUEST_ID = "daily-reminder"

        /**
         * Uhrzeit der Erinnerung — abends, wenn der Tag noch zu retten
         * ist. Derselbe Wert wie `REMINDER_TIME` am Telefon. `Long`,
         * weil NSDateComponents auf allen Apple-Zielen dieses Projekts
         * mit NSInteger (64 Bit) rechnet.
         */
        const val REMINDER_HOUR = 18L

        const val APPLE_LANGUAGES = "AppleLanguages"

        /**
         * Die beiden Saetze der Benachrichtigung.
         *
         * Sie stehen hier und nicht in den geteilten Compose-Resources —
         * aus zwei Gruenden, und beide sind Bestand, nicht Bequemlichkeit:
         *
         * 1. Die Android-Fassung liest `notif_*` aus `app/src/main/res`.
         *    Solange das so ist, waere ein zweiter Satz derselben Texte in
         *    `:ui` keine gemeinsame Quelle, sondern eine dritte Kopie.
         * 2. Der Lese-Weg von Compose Resources (`getString`) ist eine
         *    suspend-Funktion und will eine Zusammensetzung oder wenigstens
         *    einen Coroutine-Rahmen. Geplant wird hier aber ausserhalb der
         *    Oberflaeche — beim Start und im Rueckruf einer
         *    Berechtigungs-Abfrage.
         *
         * Wortgleich mit `notif_title` und `notif_text` in
         * `app/src/main/res/values[-de]/strings.xml`; wandern die Texte
         * eines Tages nach `:ui`, faellt dieser Block ersatzlos weg.
         */
        const val TITLE_DE = "Deine Daily Challenge wartet!"
        const val TITLE_EN = "Your Daily Challenge is waiting!"
        const val BODY_DE = "Die heutige Abfolge ist bereit. Ein Tap entscheidet."
        const val BODY_EN = "Today's pattern is ready. One tap decides."
    }
}
