package de.robinrehbein.punkt.play

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import de.robinrehbein.punkt.R

/**
 * Duenner Wrapper um Play Games Services v2 (Bestenlisten).
 *
 * Die Integration ist hart feature-geflaggt: Solange in
 * res/values/games.xml die Platzhalter stehen (games_app_id = "0"),
 * wird das SDK nie initialisiert, es gibt keinen Google-Kontakt und
 * [available] bleibt false — die App verhaelt sich exakt wie ohne die
 * Abhaengigkeit. Erst mit echten IDs aus der Play Console (Anleitung
 * in PUBLISHING.md) meldet sich der automatische Games-Sign-in, und
 * Submit/Anzeige werden aktiv.
 */
class Leaderboards(private val activity: Activity?) {

    private val enabled: Boolean =
        activity != null && activity.getString(R.string.games_app_id) != "0"

    /** Sign-in erfolgreich — erst dann zeigen wir den RANGLISTE-Button. */
    var available by mutableStateOf(false)
        private set

    /** Einmal beim Start aufrufen; ohne echte IDs ein No-op. */
    fun connect() {
        if (!enabled || activity == null) return
        PlayGamesSdk.initialize(activity)
        PlayGames.getGamesSignInClient(activity).isAuthenticated
            .addOnCompleteListener { task ->
                available = task.isSuccessful && task.result.isAuthenticated
            }
    }

    /** Bester Lauf (STOPP gesamt). */
    fun submitBest(score: Int) = submit(R.string.leaderboard_rekord_id, score)

    /** Bester Daily-Lauf des Tages. */
    fun submitDaily(score: Int) = submit(R.string.leaderboard_daily_id, score)

    private fun submit(idRes: Int, score: Int) {
        if (!available || activity == null || score <= 0) return
        val id = activity.getString(idRes)
        if (id.isBlank()) return
        PlayGames.getLeaderboardsClient(activity).submitScore(id, score.toLong())
    }

    /** Oeffnet die Play-Games-Bestenlisten-UI. */
    fun show() {
        if (!available || activity == null) return
        PlayGames.getLeaderboardsClient(activity).allLeaderboardsIntent
            .addOnSuccessListener { intent ->
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, RC_LEADERBOARD)
            }
    }

    private companion object {
        const val RC_LEADERBOARD = 9004
    }
}
