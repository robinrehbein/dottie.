package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Zusammenführung ist der Kern des Handy-Uhr-Abgleichs: Sie läuft auf
 * beiden Geräten, ohne dass eine Seite entscheidet. Deshalb prüfen die
 * Tests nicht nur einzelne Regeln, sondern auch die beiden Eigenschaften,
 * ohne die der Abgleich nie zur Ruhe käme.
 */
class SyncStateTest {

    private val phone = SyncState(
        bestScore = 20,
        runCount = 40,
        bestPerfectStreak = 3,
        dailyDay = 100L,
        dailyBest = 8,
        dailyStreak = 2,
        skin = "GOLD",
        skinChangedAt = 1_000L
    )

    private val watch = SyncState(
        bestScore = 25,
        runCount = 12,
        bestPerfectStreak = 2,
        dailyDay = 100L,
        dailyBest = 5,
        dailyStreak = 2,
        skin = "FROST",
        skinChangedAt = 2_000L
    )

    @Test
    fun `Bestleistungen nehmen den hoeheren Wert`() {
        val merged = phone.mergedWith(watch)
        assertEquals(25, merged.bestScore)
        assertEquals(40, merged.runCount)
        assertEquals(3, merged.bestPerfectStreak)
    }

    @Test
    fun `Ausdauer-Zaehler nehmen den hoeheren Wert, Masken werden verodert`() {
        // Monats- und Saison-Masken dürfen NICHT maximiert werden: Wer im
        // März auf der Uhr und im Mai am Telefon gespielt hat, hat beide
        // Monate gesehen — das Maximum würde einen davon verschlucken.
        val a = phone.copy(
            totalScore = 900, daysPlayed = 12, lastPlayedDay = 100L,
            monthsPlayed = 0b000000000100, seasonEarned = 0b0001
        )
        val b = watch.copy(
            totalScore = 400, daysPlayed = 20, lastPlayedDay = 104L,
            monthsPlayed = 0b000000010000, seasonEarned = 0b0100
        )
        val merged = a.mergedWith(b)
        assertEquals(900, merged.totalScore)
        assertEquals(20, merged.daysPlayed)
        assertEquals(104L, merged.lastPlayedDay)
        assertEquals(0b000000010100, merged.monthsPlayed)
        assertEquals(0b0101, merged.seasonEarned)
        assertEquals("Auch hier darf die Reihenfolge nichts ändern", merged, b.mergedWith(a))
        assertEquals("Und zweimal zusammenführen ändert nichts", merged, merged.mergedWith(b))
    }

    @Test
    fun `ein verdienter Saison-Skin geht beim Abgleich nie verloren`() {
        // Der Kürbis ist im Oktober verdient worden. Ein Gerät, das ihn
        // nicht kennt, darf ihn beim Zusammenführen nicht wegnehmen —
        // verdient bleibt verdient, auch im November.
        val mitKuerbis = phone.copy(seasonEarned = Season.KUERBIS.bit)
        val ohne = watch.copy(seasonEarned = 0)
        assertEquals(Season.KUERBIS.bit, mitKuerbis.mergedWith(ohne).seasonEarned)
        assertEquals(Season.KUERBIS.bit, ohne.mergedWith(mitKuerbis).seasonEarned)
    }

    @Test
    fun `die juengere Skin-Wahl gewinnt`() {
        assertEquals("FROST", phone.mergedWith(watch).skin)
        assertEquals("FROST", watch.mergedWith(phone).skin)
    }

    @Test
    fun `bei gleichem Zeitstempel entscheiden beide Geraete gleich`() {
        val a = phone.copy(skin = "GOLD", skinChangedAt = 500L)
        val b = phone.copy(skin = "FROST", skinChangedAt = 500L)
        assertEquals(a.mergedWith(b).skin, b.mergedWith(a).skin)
    }

    @Test
    fun `die juengere Kulissen-Wahl gewinnt, unabhaengig vom Skin`() {
        // Kulisse und Skin sind zwei Sammlungen und zwei Entscheidungen:
        // Wer am Telefon die Kulisse und auf der Uhr den Skin gewechselt
        // hat, soll beides behalten.
        val a = phone.copy(scene = "MEER", sceneChangedAt = 3_000L)
        val b = watch.copy(scene = "WIESE", sceneChangedAt = 1_500L)
        assertEquals("MEER", a.mergedWith(b).scene)
        assertEquals("MEER", b.mergedWith(a).scene)
        assertEquals("FROST", a.mergedWith(b).skin)

        // Gleichstand: beide Geräte entscheiden gleich, sonst
        // überschreiben sie sich gegenseitig endlos.
        val c = phone.copy(scene = "BERG", sceneChangedAt = 500L)
        val d = phone.copy(scene = "STADT", sceneChangedAt = 500L)
        assertEquals(c.mergedWith(d).scene, d.mergedWith(c).scene)
    }

    @Test
    fun `die juengere Ton-Set-Wahl gewinnt, unabhaengig von Skin und Kulisse`() {
        // Drei Sammlungen, drei Entscheidungen: Wer am Telefon das
        // Ton-Set und auf der Uhr den Skin gewechselt hat, behält beides.
        val a = phone.copy(
            scene = "MEER", sceneChangedAt = 3_000L,
            sound = "GLOCKE", soundChangedAt = 4_000L
        )
        val b = watch.copy(
            scene = "WIESE", sceneChangedAt = 1_500L,
            sound = "AMBOSS", soundChangedAt = 2_500L
        )
        assertEquals("GLOCKE", a.mergedWith(b).sound)
        assertEquals("GLOCKE", b.mergedWith(a).sound)
        assertEquals("MEER", a.mergedWith(b).scene)
        assertEquals("FROST", a.mergedWith(b).skin)
        assertEquals(4_000L, a.mergedWith(b).soundChangedAt)

        // Ein Gerät, das noch nie gewählt hat, übernimmt die Wahl des
        // anderen — und nicht umgekehrt, sonst wäre jede Wahl nach dem
        // ersten Abgleich wieder weg.
        val frisch = SyncState()
        assertEquals("GLOCKE", frisch.mergedWith(a).sound)
        assertEquals("GLOCKE", a.mergedWith(frisch).sound)
    }

    @Test
    fun `bei gleichem Zeitstempel entscheiden beide Geraete auch beim Ton-Set gleich`() {
        val c = phone.copy(sound = "AMBOSS", soundChangedAt = 500L)
        val d = phone.copy(sound = "GLOCKE", soundChangedAt = 500L)
        assertEquals(c.mergedWith(d).sound, d.mergedWith(c).sound)
        // Und die Regel ist dieselbe wie bei Skin und Kulisse: Der
        // alphabetisch kleinere Name gewinnt.
        assertEquals("AMBOSS", c.mergedWith(d).sound)
    }

    @Test
    fun `die drei Wahlen bleiben zusammen kommutativ und idempotent`() {
        // Der Grund, warum das hier noch einmal steht: Ein neues Feld
        // fällt bei den allgemeinen Tests unten nur auf, wenn es dort
        // auch gesetzt ist.
        val a = phone.copy(
            skin = "GOLD", skinChangedAt = 1_000L,
            scene = "BERG", sceneChangedAt = 2_000L,
            sound = "GLOCKE", soundChangedAt = 3_000L
        )
        val b = watch.copy(
            skin = "FROST", skinChangedAt = 2_000L,
            scene = "STADT", sceneChangedAt = 1_000L,
            sound = "AMBOSS", soundChangedAt = 3_000L
        )
        val merged = a.mergedWith(b)
        assertEquals(merged, b.mergedWith(a))
        assertEquals(merged, merged.mergedWith(merged))
        assertEquals(merged, merged.mergedWith(a))
        assertEquals(merged, merged.mergedWith(b))
    }

    @Test
    fun `am selben Tag zaehlt der bessere Tageslauf`() {
        assertEquals(8, phone.mergedWith(watch).dailyBest)
    }

    @Test
    fun `gestern auf der Uhr und heute am Telefon setzt die Serie fort`() {
        val yesterdayOnWatch = watch.copy(dailyDay = 99L, dailyStreak = 5, dailyBest = 7)
        // Das Telefon steht bei 1, weil es von gestern nichts wusste.
        val todayOnPhone = phone.copy(dailyDay = 100L, dailyStreak = 1, dailyBest = 3)

        val merged = todayOnPhone.mergedWith(yesterdayOnWatch)
        assertEquals(100L, merged.dailyDay)
        assertEquals(6, merged.dailyStreak)
        assertEquals(3, merged.dailyBest)
    }

    @Test
    fun `ein ausgelassener Tag reisst die Serie`() {
        val longAgo = watch.copy(dailyDay = 90L, dailyStreak = 9)
        val today = phone.copy(dailyDay = 100L, dailyStreak = 1, dailyBest = 3)

        val merged = today.mergedWith(longAgo)
        assertEquals(100L, merged.dailyDay)
        assertEquals(1, merged.dailyStreak)
    }

    @Test
    fun `ein Geraet ohne Daily-Lauf uebernimmt den Stand des anderen`() {
        val fresh = SyncState()
        assertEquals(100L, fresh.mergedWith(phone).dailyDay)
        assertEquals(2, fresh.mergedWith(phone).dailyStreak)
        assertEquals(100L, phone.mergedWith(fresh).dailyDay)
    }

    @Test
    fun `zusammenfuehren ist kommutativ`() {
        assertEquals(phone.mergedWith(watch), watch.mergedWith(phone))
    }

    @Test
    fun `zusammenfuehren ist idempotent`() {
        val merged = phone.mergedWith(watch)
        assertEquals(merged, merged.mergedWith(merged))
        assertEquals(merged, merged.mergedWith(phone))
        assertEquals(merged, merged.mergedWith(watch))
    }

    @Test
    fun `auch die Serien-Fortsetzung kommt nur einmal zum Zug`() {
        val yesterday = watch.copy(dailyDay = 99L, dailyStreak = 5)
        val today = phone.copy(dailyDay = 100L, dailyStreak = 1)
        val merged = today.mergedWith(yesterday)
        // Ein zweiter Durchlauf mit demselben alten Stand darf die Serie
        // nicht ein weiteres Mal hochzaehlen.
        assertEquals(merged, merged.mergedWith(yesterday))
    }
}
