package de.robinrehbein.punkt.ui.data

import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.Season
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die NEU-Markierung der Sammlung (Plan 7.1, AP-15): Übernahme beim
 * ersten Lesen, Saison und Gönner ohne NEU, Toleranz beim Dekodieren.
 */
class CollectionSeenTest {

    /** Ein Spielstand von vor dem Update: nur die Zahlen, keine Merker. */
    private fun bestand(block: KeyValueEditor.() -> Unit): FakeKeyValueStore =
        FakeKeyValueStore().apply { edit(block) }

    @Test
    fun `nach einer Neuinstallation ist nichts NEU`() {
        val store = GameStore(FakeKeyValueStore())
        assertEquals(emptySet(), store.collectionNew())
        // Auch der Taster-Punkt und das Banner haben nichts zu zeigen.
        assertEquals(emptyList(), CollectionSeen.newScenes(store.collectionNew()))
    }

    @Test
    fun `nach der Neuinstallation ist Erspieltes NEU`() {
        val store = GameStore(FakeKeyValueStore())
        assertEquals(emptySet(), store.collectionNew())
        repeat(5) { store.submitRun(score = 3, epochDay = 20_000L + it, month = 6, year = 2026) }
        // Fünf Läufe: MATCHA.
        assertEquals(setOf(CollectionSeen.key(SkinId.MATCHA)), store.collectionNew())
    }

    @Test
    fun `Bestand mit 130 Laeufen und Rekord 45 - WUESTE ist NEU, alte Skins nicht`() {
        val store = GameStore(bestand {
            putInt("run_count_timing", 130)
            putInt("best_score_timing", 45)
        })
        // WÜSTE gab es nach den alten Schwellen erst mit 500 Läufen. MINZE,
        // TIGER und die DOPPELLINIE hatte der Spieler schon.
        assertEquals(setOf(CollectionSeen.key(SceneId.WUESTE)), store.collectionNew())
        assertEquals(listOf(SceneId.WUESTE), CollectionSeen.newScenes(store.collectionNew()))
    }

    @Test
    fun `eine Welt, die schon nach den alten Schwellen offen war, ist nicht NEU`() {
        // Rekord 90: STADT nach der alten Regel (85), bleibt über die
        // Besitz-Menge offen — und ist nichts Neues.
        val store = GameStore(bestand {
            putInt("best_score_timing", 90)
            putInt("run_count_timing", 40)
        })
        assertFalse(CollectionSeen.key(SceneId.STADT) in store.collectionNew())
    }

    @Test
    fun `angesehen ist nicht mehr NEU, und die Uebernahme laeuft nur einmal`() {
        val store = GameStore(bestand {
            putInt("run_count_timing", 130)
            putInt("best_score_timing", 45)
        })
        store.markCollectionSeen(listOf(CollectionSeen.key(SceneId.WUESTE)))
        assertEquals(emptySet(), store.collectionNew())
        // Ein zweites Lesen übernimmt nicht noch einmal, und Neues kommt dazu.
        store.submitRun(score = 2_500, epochDay = 20_100L, month = 6, year = 2026)
        assertTrue(CollectionSeen.key(SceneId.MEER) in store.collectionNew())
    }

    @Test
    fun `Saison- und Goenner-Skins bekommen keine NEU-Markierung`() {
        val alles = SkinStats(
            bestScore = 0,
            bestPerfectStreak = 0,
            bestDailyStreak = 0,
            seasonEarned = 0b1111,
            patronOwned = true
        )
        val neu = CollectionSeen.newKeys(alles, seen = emptySet())
        Season.entries.forEach { assertFalse(CollectionSeen.key(it.skin) in neu, "${it.skin}") }
        listOf(SkinId.DIAMANT, SkinId.PHOENIX, SkinId.ONYX).forEach {
            assertFalse(CollectionSeen.key(it) in neu, "$it")
        }
        // Im Speicher genauso: Kauf nach der Übernahme macht nichts NEU.
        val store = GameStore(FakeKeyValueStore())
        store.collectionNew()
        store.patronOwned = true
        assertEquals(emptySet(), store.collectionNew())
    }

    @Test
    fun `KASKADE und hoeher sind NEU, wenn der WELTRAUM nach den alten Schwellen zu war`() {
        val alles = SkinStats(
            bestScore = 999,
            bestPerfectStreak = 99,
            bestDailyStreak = 99,
            runCount = 9_999,
            totalScore = 999_999,
            daysPlayed = 365,
            monthsPlayed = 12
        )
        val oben = listOf(CardFrame.KASKADE, CardFrame.PERLENKRANZ, CardFrame.KRONE)
            .map { CollectionSeen.key(it) }
        val ohneWeltraum = CollectionSeen.migrated(alles, legacyScenes = setOf("WIESE", "STADT"))
        oben.forEach { assertFalse(it in ohneWeltraum, it) }
        assertFalse(CollectionSeen.key(SceneId.WELTRAUM) in ohneWeltraum)
        assertTrue(CollectionSeen.key(CardFrame.PRACHT) in ohneWeltraum)
        assertTrue(CollectionSeen.key(SceneId.STADT) in ohneWeltraum)

        val mitWeltraum = CollectionSeen.migrated(alles, legacyScenes = SceneId.entries.map { it.name }.toSet())
        oben.forEach { assertTrue(it in mitWeltraum, it) }
    }

    @Test
    fun `Downgrade-Toleranz beim Dekodieren`() {
        assertEquals(emptySet(), CollectionSeen.decode(null))
        assertEquals(emptySet(), CollectionSeen.decode(""))
        assertEquals(
            setOf("SKIN:MINZE", "SCENE:ATLANTIS", "HUT:ZYLINDER"),
            CollectionSeen.decode(" SKIN:MINZE ,,SCENE:ATLANTIS,Müll,SOUND:,:X,HUT:ZYLINDER")
        )
        // Ein unbekannter Schlüssel aus einer neueren Version überlebt das
        // nächste Schreiben, und der gespeicherte Stand wird nicht noch
        // einmal übernommen.
        val prefs = bestand {
            putString(
                "collection_seen",
                "SCENE:ATLANTIS,SKIN:KLASSIK,SCENE:WIESE,SOUND:KLASSIK,FRAME:SCHLICHT"
            )
            putInt("best_score_timing", 12)
        }
        val store = GameStore(prefs)
        assertEquals(setOf(CollectionSeen.key(SkinId.MINZE)), store.collectionNew())
        store.markCollectionSeen(listOf(CollectionSeen.key(SkinId.MINZE)))
        assertTrue("SCENE:ATLANTIS" in CollectionSeen.decode(prefs.string("collection_seen")))
        assertTrue("SKIN:MINZE" in store.collectionSeen)
    }

    @Test
    fun `die Menge wandert nicht in den Abgleich`() {
        val store = GameStore(FakeKeyValueStore())
        val vorher = store.syncState()
        store.markCollectionSeen(listOf(CollectionSeen.key(SkinId.MINZE)))
        assertEquals(vorher, store.syncState())
    }
}
