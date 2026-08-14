package de.robinrehbein.punkt.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import de.robinrehbein.punkt.game.SyncState

/**
 * Gleicht den Spielstand zwischen Telefon und Uhr ab.
 *
 * Grundgedanke: Es gibt keine Haupt- und keine Nebenrolle. Jedes Gerät
 * legt seinen eigenen Stand in den Data Layer, hört auf den der anderen
 * Seite und führt beides mit [SyncState.mergedWith] zusammen. Weil das
 * Zusammenführen kommutativ und idempotent ist, landen beide Seiten
 * zwangsläufig beim selben Ergebnis — egal, wer zuerst da war und wie oft
 * dieselbe Nachricht ankommt.
 *
 * Bewusst ohne WearableListenerService: Der Abgleich läuft nur, während
 * eine der beiden Apps offen ist. Für ein Spiel reicht das, denn beim
 * Öffnen wird der zuletzt abgelegte Stand der Gegenseite ohnehin gelesen
 * ([pullAndPublish]) — der Data Layer hält ihn vor, auch wenn die andere
 * App längst geschlossen ist. Ein Hintergrunddienst würde dafür in beiden
 * Modulen Manifest-Einträge und einen eigenen Lebenszyklus brauchen,
 * ohne dass der Spieler etwas davon hätte.
 *
 * @param read  liefert den aktuellen lokalen Stand
 * @param write übernimmt den zusammengeführten Stand lokal; gibt zurück,
 *              ob sich dabei tatsächlich etwas geändert hat
 */
class StatsSync(
    context: Context,
    private val read: () -> SyncState,
    private val write: (SyncState) -> Boolean
) {

    private val dataClient: DataClient = Wearable.getDataClient(context.applicationContext)

    /**
     * Zuletzt selbst veröffentlichter Stand. Der Data Layer verwirft
     * zwar inhaltsgleiche Schreibvorgänge von sich aus, aber jeder
     * Aufruf kostet trotzdem einen Rundlauf — und ohne diese Bremse
     * würde jede empfangene Nachricht eine Antwort auslösen, die die
     * Gegenseite wieder antworten ließe.
     */
    private var lastPublished: SyncState? = null

    private val listener = DataClient.OnDataChangedListener { events ->
        onDataChanged(events)
    }

    /** Ab jetzt auf Änderungen der Gegenseite hören. */
    fun start() {
        dataClient.addListener(listener)
        pullAndPublish()
    }

    /** Beim Pausieren der App aufräumen — sonst bleibt der Listener hängen. */
    fun stop() {
        dataClient.removeListener(listener)
    }

    /**
     * Einmal beide Richtungen: erst holen, was schon im Data Layer liegt
     * (auch von einer längst geschlossenen Gegenseite), dann den eigenen
     * — jetzt zusammengeführten — Stand hinterlegen.
     */
    fun pullAndPublish() {
        dataClient.dataItems.addOnSuccessListener { buffer ->
            try {
                var merged = read()
                for (item in buffer) {
                    if (item.uri.path != PATH) continue
                    merged = merged.mergedWith(DataMapItem.fromDataItem(item).dataMap.toState())
                }
                write(merged)
            } catch (t: Throwable) {
                Log.w(TAG, "Lesen des Data Layer fehlgeschlagen", t)
            } finally {
                buffer.release()
            }
            publish()
        }.addOnFailureListener { t ->
            // Kein Play-Dienst, keine gekoppelte Uhr, keine Berechtigung:
            // Der Abgleich ist eine Zugabe, das Spiel läuft ohne ihn
            // vollständig weiter.
            Log.i(TAG, "Kein Data Layer verfügbar — Abgleich bleibt aus: ${t.message}")
        }
    }

    /**
     * Den eigenen Stand veröffentlichen. Nach jedem Lauf und jeder
     * Skin-Wahl aufzurufen — der Aufruf ist billig, solange sich nichts
     * geändert hat.
     */
    fun publish() {
        val state = read()
        if (state == lastPublished) return
        lastPublished = state
        val request = PutDataMapRequest.create(PATH).apply { dataMap.putState(state) }
        dataClient.putDataItem(request.asPutDataRequest())
            .addOnFailureListener { t -> Log.w(TAG, "Senden fehlgeschlagen", t) }
    }

    private fun onDataChanged(events: DataEventBuffer) {
        try {
            var merged = read()
            var sawSomething = false
            for (event in events) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                if (event.dataItem.uri.path != PATH) continue
                merged = merged.mergedWith(
                    DataMapItem.fromDataItem(event.dataItem).dataMap.toState()
                )
                sawSomething = true
            }
            if (!sawSomething) return
            // Nur antworten, wenn der eigene Stand dabei tatsächlich
            // gewachsen ist. Sonst schaukeln sich zwei Geräte gegenseitig
            // hoch, ohne dass sich je etwas ändert.
            if (write(merged)) publish()
        } catch (t: Throwable) {
            Log.w(TAG, "Empfang fehlgeschlagen", t)
        } finally {
            events.release()
        }
    }

    private companion object {
        const val TAG = "StatsSync"

        /**
         * Jedes Gerät schreibt unter diesen Pfad. Das kollidiert nicht:
         * Der Data Layer hängt die Knoten-ID des Absenders an die URI, es
         * gibt also pro Gerät einen eigenen Eintrag.
         */
        const val PATH = "/dottie/stats"

        const val KEY_BEST = "best_score"
        const val KEY_RUNS = "run_count"
        const val KEY_BEST_PERFECT = "best_perfect"
        const val KEY_DAILY_DAY = "daily_day"
        const val KEY_DAILY_BEST = "daily_best"
        const val KEY_DAILY_STREAK = "daily_streak"
        const val KEY_SKIN = "skin"
        const val KEY_SKIN_CHANGED = "skin_changed_at"

        // Ausdauer-Achsen und Saison-Maske (ab v2.20). Fehlen sie in einer
        // Nachricht, weil die Gegenseite noch die alte App fährt, greifen
        // die Standardwerte — und das Zusammenführen nimmt ohnehin den
        // größeren Wert bzw. die vereinigte Maske.
        const val KEY_TOTAL_SCORE = "total_score"
        const val KEY_DAYS_PLAYED = "days_played"
        const val KEY_LAST_PLAYED_DAY = "last_played_day"
        const val KEY_MONTHS_PLAYED = "months_played"
        const val KEY_SEASON_EARNED = "season_earned"

        // Kulissen-Wahl (ab v2.21). Wie der Skin eine Entscheidung: Fehlt
        // sie in einer Nachricht, weil die Gegenseite eine ältere App
        // fährt, bleibt der Zeitstempel 0 — dann gewinnt die lokale Wahl.
        const val KEY_SCENE = "scene"
        const val KEY_SCENE_CHANGED = "scene_changed_at"

        // Ton-Set-Wahl (ab v2.23), nach derselben Regel: Fehlt sie in
        // einer Nachricht, bleibt der Zeitstempel 0 und die lokale Wahl
        // gewinnt — eine ältere App darf keinen Klang zurückdrehen.
        const val KEY_SOUND = "sound"
        const val KEY_SOUND_CHANGED = "sound_changed_at"

        fun DataMap.putState(s: SyncState) {
            putInt(KEY_BEST, s.bestScore)
            putInt(KEY_RUNS, s.runCount)
            putInt(KEY_BEST_PERFECT, s.bestPerfectStreak)
            putLong(KEY_DAILY_DAY, s.dailyDay)
            putInt(KEY_DAILY_BEST, s.dailyBest)
            putInt(KEY_DAILY_STREAK, s.dailyStreak)
            putInt(KEY_TOTAL_SCORE, s.totalScore)
            putInt(KEY_DAYS_PLAYED, s.daysPlayed)
            putLong(KEY_LAST_PLAYED_DAY, s.lastPlayedDay)
            putInt(KEY_MONTHS_PLAYED, s.monthsPlayed)
            putInt(KEY_SEASON_EARNED, s.seasonEarned)
            putString(KEY_SKIN, s.skin)
            putLong(KEY_SKIN_CHANGED, s.skinChangedAt)
            putString(KEY_SCENE, s.scene)
            putLong(KEY_SCENE_CHANGED, s.sceneChangedAt)
            putString(KEY_SOUND, s.sound)
            putLong(KEY_SOUND_CHANGED, s.soundChangedAt)
        }

        fun DataMap.toState() = SyncState(
            bestScore = getInt(KEY_BEST, 0),
            runCount = getInt(KEY_RUNS, 0),
            bestPerfectStreak = getInt(KEY_BEST_PERFECT, 0),
            dailyDay = getLong(KEY_DAILY_DAY, 0L),
            dailyBest = getInt(KEY_DAILY_BEST, 0),
            dailyStreak = getInt(KEY_DAILY_STREAK, 0),
            totalScore = getInt(KEY_TOTAL_SCORE, 0),
            daysPlayed = getInt(KEY_DAYS_PLAYED, 0),
            lastPlayedDay = getLong(KEY_LAST_PLAYED_DAY, 0L),
            monthsPlayed = getInt(KEY_MONTHS_PLAYED, 0),
            seasonEarned = getInt(KEY_SEASON_EARNED, 0),
            skin = getString(KEY_SKIN, ""),
            skinChangedAt = getLong(KEY_SKIN_CHANGED, 0L),
            scene = getString(KEY_SCENE, ""),
            sceneChangedAt = getLong(KEY_SCENE_CHANGED, 0L),
            sound = getString(KEY_SOUND, ""),
            soundChangedAt = getLong(KEY_SOUND_CHANGED, 0L)
        )
    }
}
