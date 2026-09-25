package de.robinrehbein.punkt.ui.data

import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Der Abgleich mit der Uhr, von der Speicher-Seite aus gesehen.
 *
 * Zwei Eigenschaften hängen hier dran, die man am Gerät erst merkt, wenn
 * sie fehlen: dass die Oberfläche vom Merge überhaupt erfährt
 * ([GameStore.syncRevision]), und dass eine abgelehnte Wahl der Uhr beim
 * nächsten Abgleich wieder anklopft (der Zeitstempel bleibt stehen).
 */
class GameStoreSyncTest {

    private fun store() = GameStore(FakeKeyValueStore())

    @Test
    fun `ein uebernommener Abgleich meldet sich bei der Oberflaeche`() {
        val store = store()
        assertEquals(0, store.syncRevision)

        assertTrue(store.applySync(SyncState(bestScore = 42)))

        assertEquals(42, store.bestScore)
        assertEquals(1, store.syncRevision, "Die Oberfläche muss nachziehen können")
    }

    @Test
    fun `ein Abgleich ohne Neuigkeit meldet sich nicht`() {
        val store = store()
        store.applySync(SyncState(bestScore = 42))
        val vorher = store.syncRevision

        // Derselbe Stand ein zweites Mal: Der Merge ist idempotent, also
        // gibt es auch nichts nachzuziehen.
        store.applySync(store.syncState())

        assertEquals(vorher, store.syncRevision)
    }

    @Test
    fun `jede uebernommene Entscheidung zaehlt einzeln`() {
        val store = store()
        store.applySync(SyncState(bestScore = 10))
        store.applySync(SyncState(bestScore = 20))

        assertEquals(2, store.syncRevision)
        assertEquals(20, store.bestScore)
    }

    @Test
    fun `der gewaehlte Skin kommt aus dem Abgleich in den Speicher`() {
        val store = store()

        store.applySync(
            SyncState(bestScore = 15, skin = SkinId.MINZE.name, skinChangedAt = 5_000L)
        )

        assertEquals(SkinId.MINZE, store.selectedSkin)
        assertEquals(
            5_000L,
            store.syncState().skinChangedAt,
            "Eine uebernommene Wahl schreibt ihren Zeitstempel fort"
        )
    }

    @Test
    fun `ein abgelehnter Goenner-Skin klopft nach dem Kauf erneut an`() {
        val store = store()
        val vonDerUhr = SyncState(skin = SkinId.DIAMANT.name, skinChangedAt = 9_000L)

        store.applySync(vonDerUhr)

        // Der Kauf ist hier noch nicht bekannt — also bleibt die Wahl aus.
        assertEquals(SkinId.KLASSIK, store.selectedSkin)
        assertEquals(
            0L,
            store.syncState().skinChangedAt,
            "Ohne Uebernahme darf der Zeitstempel nicht mitwandern"
        )

        // Play meldet den Gönner-Kauf nach: Jetzt muss dieselbe Wahl
        // durchgehen. Mit fortgeschriebenem Zeitstempel waere sie fuer
        // immer verschluckt gewesen.
        store.patronOwned = true
        store.applySync(vonDerUhr)

        assertEquals(SkinId.DIAMANT, store.selectedSkin)
        assertEquals(9_000L, store.syncState().skinChangedAt)
    }

    @Test
    fun `eine noch ungedeckte Kulisse kommt spaeter erneut an`() {
        val store = store()
        // STADT haengt an Rekord 100 — den kennt hier noch niemand.
        val vonDerUhr = SyncState(
            bestScore = 50,
            scene = SceneId.STADT.name,
            sceneChangedAt = 7_000L
        )

        store.applySync(vonDerUhr)

        assertEquals(SceneId.WIESE, store.selectedScene)
        assertEquals(0L, store.syncState().sceneChangedAt)

        // Der Rekord kommt nach — beim naechsten Abgleich ist die Kulisse
        // gedeckt und wird uebernommen.
        store.submitRun(score = 100, epochDay = 20_000L, month = 6, year = 2026)
        store.applySync(vonDerUhr)

        assertEquals(SceneId.STADT, store.selectedScene)
        assertEquals(7_000L, store.syncState().sceneChangedAt)
    }

    // ===== Bestandsschutz der Welten (AP-13) =====

    /**
     * Ein Speicher, wie ihn die App vor der Welten-Leiter hinterlassen
     * hat: Rekord 90, die STADT gewählt, kein Versionsmerker.
     */
    private fun bestandMitRekord90(): FakeKeyValueStore = FakeKeyValueStore().apply {
        edit {
            putInt("best_score_timing", 90)
            putInt("run_count_timing", 40)
            putString("selected_scene", SceneId.STADT.name)
            putLong("scene_changed_at", 3_000L)
        }
    }

    @Test
    fun `Rekord 90 vor dem Update behaelt die STADT, und sie bleibt gewaehlt`() {
        val store = GameStore(bestandMitRekord90())

        assertTrue(SceneId.STADT.name in store.ownedScenes)
        assertTrue(ScenePaint.isUnlocked(SceneId.STADT, store.stats()))
        assertEquals(SceneId.STADT, store.selectedScene)
        // Die Wahl gilt auch im Austausch als gedeckt, sonst fiele die Uhr
        // auf die WIESE zurück.
        assertEquals(SceneId.STADT.name, store.syncState().scene)
        assertEquals(3_000L, store.syncState().sceneChangedAt)
    }

    @Test
    fun `ein frischer Spieler mit Rekord 90 bekommt die STADT nicht`() {
        val store = store()
        store.submitRun(score = 90, epochDay = 20_000L, month = 6, year = 2026)

        assertFalse(ScenePaint.isUnlocked(SceneId.STADT, store.stats()))
        assertFalse(SceneId.STADT.name in store.ownedScenes)

        // Auch ein Neustart holt die alte Regel nicht zurück.
        val prefs = FakeKeyValueStore()
        GameStore(prefs).submitRun(score = 90, epochDay = 20_000L, month = 6, year = 2026)
        assertFalse(ScenePaint.isUnlocked(SceneId.STADT, GameStore(prefs).stats()))
    }

    @Test
    fun `die Uebernahme laeuft genau einmal`() {
        val prefs = bestandMitRekord90()
        GameStore(prefs)
        assertEquals(setOf(SceneId.WIESE.name, SceneId.STADT.name), GameStore(prefs).ownedScenes)

        // Jemand räumt die Menge leer (etwa ein kaputtes Backup): Ein
        // zweiter Start darf die Übernahme nicht wiederholen, der Merker
        // steht.
        prefs.edit { putString("owned_scenes", "") }
        assertEquals(emptySet<String>(), GameStore(prefs).ownedScenes)
        assertFalse(ScenePaint.isUnlocked(SceneId.STADT, GameStore(prefs).stats()))
    }

    @Test
    fun `eine erfuellte Regel kommt dauerhaft in die Menge`() {
        val store = store()
        store.submitRun(score = 100, epochDay = 20_000L, month = 6, year = 2026)

        assertTrue(ScenePaint.isUnlocked(SceneId.STADT, store.stats()))
        assertTrue(SceneId.STADT.name in store.ownedScenes, "Die Welt steht jetzt im Besitz")
    }

    @Test
    fun `der Abgleich zweier Geraete vereinigt die Mengen`() {
        val telefon = GameStore(bestandMitRekord90())
        val uhr = SyncState(
            bestScore = 20,
            ownedScenes = setOf(SceneId.BERG.name, "AUS_EINER_NEUEREN_VERSION")
        )

        assertTrue(telefon.applySync(telefon.syncState().mergedWith(uhr)))

        assertEquals(
            setOf(SceneId.WIESE.name, SceneId.STADT.name, SceneId.BERG.name, "AUS_EINER_NEUEREN_VERSION"),
            telefon.ownedScenes
        )
        assertTrue(ScenePaint.isUnlocked(SceneId.BERG, telefon.stats()))
        // Und die Gegenseite bekommt die STADT über dieselbe Vereinigung.
        val zurueck = uhr.mergedWith(telefon.syncState())
        assertTrue(SceneId.STADT.name in zurueck.ownedScenes)
        assertEquals(zurueck, telefon.syncState().mergedWith(uhr), "kommutativ")
        assertEquals(zurueck, zurueck.mergedWith(zurueck), "idempotent")
    }

    @Test
    fun `eine Welt aus dem Besitz der Gegenseite darf gewaehlt uebernommen werden`() {
        val store = store()
        val vonDerUhr = SyncState(
            bestScore = 90,
            scene = SceneId.STADT.name,
            sceneChangedAt = 8_000L,
            ownedScenes = setOf(SceneId.STADT.name)
        )

        store.applySync(vonDerUhr)

        assertEquals(SceneId.STADT, store.selectedScene)
        assertEquals(8_000L, store.syncState().sceneChangedAt)
    }
}
