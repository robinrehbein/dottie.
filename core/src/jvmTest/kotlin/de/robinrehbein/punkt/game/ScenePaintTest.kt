package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Die Regeln, die eine Kulisse von der Bahn trennen. Was hier durchfällt,
 * fällt in allen vier Renderern gleichzeitig auf — und zwar erst im
 * Spiel, wo es niemand mehr korrigiert.
 */
class ScenePaintTest {

    /** Zielzone (Perfekt-Kern und Band). */
    private val zielzone = listOf(0xFF74BF2EL, 0xFF9DE85AL)

    private val maxStats = SkinStats(
        bestScore = 999,
        bestPerfectStreak = 99,
        bestDailyStreak = 99,
        runCount = 9_999,
        totalScore = 999_999,
        daysPlayed = 365,
        monthsPlayed = 12
    )

    /** Alle Farben einer Kulisse — Himmel, Wolke, Boden, Requisiten, Hintergrund. */
    private fun farben(id: SceneId): List<Long> {
        val scene = ScenePaint.of(id)
        val out = mutableListOf<Long>()
        out += scene.sky.toList()
        scene.cloud?.let { out += it }
        scene.ground?.let { out += listOf(it.sand, it.sandShade, it.turfDark, it.turfLight) }
        scene.props.forEach { prop ->
            out += listOf(prop.dark, prop.body, prop.light, prop.stem, prop.stemShade)
            out += prop.accents
        }
        scene.backdrop?.let { out += it.colors }
        return out
    }

    @Test
    fun `keine Kulissenfarbe kommt der Zielzone nahe`() {
        // Die Kulisse ist die verkäufliche Fläche, die Bahn nicht. Damit
        // das trägt, darf keine Kulissenfarbe aussehen wie das, worauf
        // getippt wird (grün) — sonst verkauft die Kulisse Verwirrung.
        // Die Falle ist eine Kette von Minen und keine Farbfläche mehr;
        // ihren Kontrast prüft `jede Mine hebt sich von jedem Himmel ab`.
        //
        // Der Bestand der WIESE reißt diese Grenze selbst: Buschgrün liegt
        // 13 Schritte neben der Zonenfarbe, die Grasnarbe trägt sie exakt.
        // Diese Flächen liegen seit jeher am unteren Bildrand, nie im
        // Ringband (die Bahn endet bei 72 % Höhe, die Kronen beginnen bei
        // 74 %) — sie bleiben deshalb als benannte Ausnahme stehen, statt
        // stillschweigend umgefärbt zu werden.
        val bestandsgruen = ScenePaint.LEGACY_ZONE_GREENS.toSet()

        SceneId.entries.forEach { id ->
            farben(id).forEach { farbe ->
                if (farbe in bestandsgruen) {
                    assertEquals(
                        "Nur die WIESE darf das Bestandsgrün tragen",
                        SceneId.WIESE, id
                    )
                    return@forEach
                }
                zielzone.forEach { zone ->
                    assertTrue(
                        "$id trägt ${hex(farbe)} — nur ${abstand(farbe, zone)} " +
                            "vom Zielzonen-Ton ${hex(zone)} entfernt",
                        abstand(farbe, zone) >= ScenePaint.MIN_ZONE_DISTANCE
                    )
                }
            }
        }
    }

    @Test
    fun `das Bestandsgruen bleibt der WIESE vorbehalten und bleibt unveraendert`() {
        // Regression zur Ausnahme oben: Wenn jemand die Ausnahme
        // ausnutzen wollte, müsste er sie erst erweitern — und das fällt
        // hier auf.
        assertEquals(4, ScenePaint.LEGACY_ZONE_GREENS.size)
        assertEquals(
            listOf(0xFF71C837L, 0xFF5AA82CL, 0xFF9DE85AL, 0xFF74BF2EL),
            ScenePaint.LEGACY_ZONE_GREENS.toList()
        )
    }

    @Test
    fun `kein Himmel verschluckt die Zonensignale der Bahn`() {
        // Zone und Perfekt-Kern sind die Flächen im Bild, an denen eine
        // Entscheidung hängt. Ein Himmel, der eine davon schluckt, nimmt
        // dem Lauf seine Grundlage — und mit sechs Kulissen mal sieben
        // Stufen gibt es 42 Hintergründe, vor denen das passieren kann.
        //
        // Die Schwelle ist der Bestand selbst (siehe MIN_SKY_SIGNAL_DISTANCE):
        // Das knappste Paar ist die helle Zone vor der WÜSTE, Stufe 1. Der
        // Test sagt damit nicht "das ist gut", sondern "nichts Neues darf
        // schlechter werden". Die Minen der Falle prüft der nächste Test.
        val signale = mapOf(
            "Zone hell" to 0xFF9DE85AL,
            "Zone dunkel" to 0xFF74BF2EL
        )
        SceneId.entries.forEach { id ->
            ScenePaint.sky(id).forEachIndexed { stufe, himmel ->
                signale.forEach { (name, signal) ->
                    assertTrue(
                        "$id Stufe $stufe (${hex(himmel)}) liegt nur " +
                            "${abstand(himmel, signal)} vom Signal \"$name\" entfernt",
                        abstand(himmel, signal) >= ScenePaint.MIN_SKY_SIGNAL_DISTANCE
                    )
                }
            }
        }
    }

    @Test
    fun `jede Mine hebt sich von jedem Himmel ab`() {
        // Die Falle ist eine Kette von Minen: schwarze Kugel mit hellem
        // Rand. Vor hellen Himmeln trägt die Kugel, vor dunklen der Rand.
        // Es reicht, wenn eine der beiden klar absticht — aber eine muss
        // es, in jeder Welt und auf jeder Stufe.
        SceneId.entries.forEach { id ->
            ScenePaint.sky(id).forEachIndexed { stufe, himmel ->
                val kugel = abstand(himmel, TrapPaint.BALL)
                val rand = abstand(himmel, TrapPaint.RIM)
                assertTrue(
                    "$id Stufe $stufe (${hex(himmel)}): Kugel $kugel, Rand $rand — " +
                        "die Mine geht im Himmel unter",
                    maxOf(kugel, rand) >= MINE_CONTRAST
                )
            }
        }
    }

    @Test
    fun `kein Himmel ist mehr lila`() {
        // Die Tester haben den lila Himmel ab Score 10 für die Falle
        // gehalten. Die alten Töne dürfen nicht zurückkommen.
        val alt = setOf(0xFF7B6FD0L, 0xFF7B6B9EL, 0xFF3E1A78L, 0xFF6A1E6EL)
        SceneId.entries.forEach { id ->
            ScenePaint.sky(id).forEach { himmel ->
                assertFalse("$id trägt wieder ${hex(himmel)}", himmel in alt)
            }
        }
        assertEquals(0xFF3F6FC4L, ScenePaint.sky(SceneId.WIESE)[2])
        assertEquals(0xFF4A6AA8L, ScenePaint.sky(SceneId.STADT)[2])
        assertEquals(0xFF243A8CL, ScenePaint.sky(SceneId.WELTRAUM)[2])
    }

    @Test
    fun `die sieben Himmelsstufen einer Kulisse bleiben unterscheidbar`() {
        // Der Himmel ist Fortschrittsanzeige, kein Dekor: Wer eine Stufe
        // erreicht, muss den Wechsel sehen. Zwei Stufen, die sich nur um
        // eine Nuance unterscheiden, nehmen dem Lauf sein Feedback.
        SceneId.entries.forEach { id ->
            val sky = ScenePaint.sky(id)
            assertEquals("$id braucht genau sieben Stufen", 7, sky.size)
            for (i in 1 until sky.size) {
                assertTrue(
                    "$id: Stufe ${i - 1} und $i liegen nur ${abstand(sky[i - 1], sky[i])} auseinander",
                    abstand(sky[i - 1], sky[i]) >= ScenePaint.MIN_SKY_STEP
                )
            }
            // Und der Zähler von SkinPaint muss in jeder Kulisse in die
            // Tabelle treffen, auch weit jenseits der Nacht.
            (0..500).forEach { score ->
                assertTrue(
                    "$id: Score $score zeigt auf eine Stufe außerhalb der Tabelle",
                    SkinPaint.skyStage(score) in sky.indices
                )
            }
        }
    }

    @Test
    fun `jede Kulisse hat volle Deckkraft`() {
        SceneId.entries.forEach { id ->
            farben(id).forEach { farbe ->
                assertEquals(
                    "$id: ${hex(farbe)} ist nicht deckend",
                    0xFFL, (farbe shr 24) and 0xFF
                )
            }
        }
    }

    @Test
    fun `die Bodenkante bleibt Layout-Anker — auch ohne Boden`() {
        // Die Bodenkante ist der einzige senkrechte Anker der Welt: Dort
        // stehen die Requisiten, dort beginnt der Bodenstreifen, und dort
        // setzt die Tod-Animation auf. Eine Kulisse, die sie verschieben
        // könnte, würde das Spielgefühl ändern — genau das darf sie nicht.
        assertEquals(0.88f, ScenePaint.GROUND_TOP, 1e-6f)

        val h = 2000f
        // Mario-Tod, Werte wie in allen Renderern (DEATH_HOP_SPEED /
        // DEATH_GRAVITY): erst Hüpfer nach oben, dann Sturz.
        fun sturzZeitBisKante(kante: Float, start: Float): Float {
            var t = 0f
            while (t < 10f) {
                val y = start + (-1.6f * t + 0.5f * 6f * t * t) * h
                if (y >= kante) return t
                t += 0.0005f
            }
            return -1f
        }

        val start = h * 0.44f
        val zeiten = SceneId.entries.map { id ->
            // ScenePaint.groundY kennt die Kulisse gar nicht — genau das
            // ist die Zusicherung. Der Aufruf steht hier trotzdem je
            // Kulisse, damit eine kulissenabhängige Kante auffliegt.
            ScenePaint.groundY(h).also { kante ->
                assertEquals("$id verschiebt die Bodenkante", 1760f, kante, 1e-3f)
            }
            sturzZeitBisKante(ScenePaint.groundY(h), start)
        }.toSet()
        assertEquals("Der Sturz endet nicht in jeder Kulisse auf derselben Linie", 1, zeiten.size)
        assertTrue("Der Sturz erreicht die Kante überhaupt", zeiten.first() > 0f)

        // Der WELTRAUM zeichnet keinen Boden — die Linie bleibt trotzdem.
        assertNull("Im Vakuum gibt es keinen Boden", ScenePaint.ground(SceneId.WELTRAUM))
        assertNull("Im Vakuum gibt es keine Wolken", ScenePaint.cloud(SceneId.WELTRAUM))
        SceneId.entries.filter { it != SceneId.WELTRAUM }.forEach {
            assertNotNull("$it braucht einen Boden", ScenePaint.ground(it))
            assertNotNull("$it braucht Wolken", ScenePaint.cloud(it))
        }
    }

    @Test
    fun `die WIESE ist Pixel fuer Pixel der Bestand`() {
        // Die Messlatte des ganzen Umbaus: Wer die Umstellung sieht, hat
        // sie falsch gemacht. Die Werte hier stammen aus dem alten
        // Overlay-Code und TimingGameScreen.kt vor der Einführung der
        // Kulissen. Einzige gewollte Änderung: Stufe 2 ist tiefes Blau
        // statt Lila (Tester hielten den Himmel für die Falle).
        assertEquals(
            listOf(
                0xFF4EC0CAL, 0xFF5B9BD5L, 0xFF3F6FC4L, 0xFFC0616FL,
                0xFFD98A3DL, 0xFF3D4A8CL, 0xFF2A2640L
            ),
            ScenePaint.sky(SceneId.WIESE).toList()
        )
        assertEquals(0xFFE9FCFDL, ScenePaint.cloud(SceneId.WIESE))
        val boden = ScenePaint.ground(SceneId.WIESE)!!
        assertEquals(0xFFDED895L, boden.sand)
        assertEquals(0xFFD3C87EL, boden.sandShade)
        assertEquals(0xFF74BF2EL, boden.turfDark)
        assertEquals(0xFF9DE85AL, boden.turfLight)

        val props = ScenePaint.props(SceneId.WIESE)
        assertEquals(
            listOf(PropShape.BAUM, PropShape.BLUME, PropShape.BAUM, PropShape.STRAUCH),
            props.map { it.shape }
        )
        assertEquals(listOf(0.075f, 0.032f, 0.058f, 0.026f), props.map { it.size })
        assertEquals(listOf(1.0f, 0.8f, -1.0f, 0.4f), props.map { it.sway })
        assertEquals(0xFF9C6B3CL, props[0].stem)
        assertEquals(0xFF7A4E2AL, props[0].stemShade)
        // Die Blütenmitte war immer Gold, nicht Grün.
        assertEquals(0xFFFFD847L, props[1].light)
        assertEquals(listOf(0xFFE53935L, 0xFFE9FCFDL), props[1].accents)
    }

    @Test
    fun `jede Kulisse beschreibt vollstaendig vier Requisiten`() {
        SceneId.entries.forEach { id ->
            val props = ScenePaint.props(id)
            // Der WELTRAUM ist leer bis auf den Sternenhimmel: Vor den
            // Sternen treibt nichts. Wer keine Requisiten hat, braucht
            // deshalb einen Hintergrund — sonst bliebe nur der Himmel.
            if (props.isEmpty()) {
                assertEquals(
                    "$id ohne Requisiten braucht den Sternenhimmel",
                    BackdropKind.STERNENHIMMEL, ScenePaint.of(id).backdrop?.kind
                )
                return@forEach
            }
            assertEquals("$id braucht vier Requisiten-Plätze", ScenePaint.PROP_SLOTS, props.size)
            props.forEach { prop ->
                assertTrue("$id: Requisiten-Größe muss positiv sein", prop.size > 0f)
                assertTrue("$id: Requisite ist größer als das halbe Bild", prop.size < 0.5f)
                assertTrue("$id: Windanteil ist aus dem Ruder", prop.sway in -1.5f..1.5f)
            }
            // Formen, die einen Akzent brauchen, müssen mindestens zwei
            // haben — sonst wechselt nichts und die Reihe wirkt gestempelt.
            props.filter { it.shape == PropShape.BLUME || it.shape == PropShape.HOCHHAUS }
                .forEach {
                    assertTrue("$id: ${it.shape} braucht wechselnde Akzente", it.accents.size >= 2)
                }
        }
    }

    @Test
    fun `ein Fels auf festem Boden steht still`() {
        // Der Fehler, der das nötig gemacht hat: Alle sechs Kulissen sind
        // aus demselben Raster gebaut, und Platz vier ist in der WIESE ein
        // Strauch mit Windanteil 0.4. In den fünf neuen Kulissen rutschte
        // dort ein Fels hinein — der Windanteil wanderte mit. Auf der
        // Straße der STADT wackelte damit ein Kiesel neben Hochhäusern,
        // die per Kommentar ausdrücklich nicht wanken.
        //
        // Der WELTRAUM ist die Ausnahme und erkennt sich selbst daran,
        // dass er keinen Boden hat: Dort treibt absichtlich alles.
        SceneId.entries.forEach { id ->
            if (ScenePaint.ground(id) == null) return@forEach
            ScenePaint.props(id).filter { it.shape == PropShape.FELS }.forEach {
                assertEquals("$id: ein Stein auf dem Boden darf nicht schwanken", 0f, it.sway, 0f)
            }
        }
    }

    @Test
    fun `der Fels ist ein Umriss und kein Stapel`() {
        val parts = ScenePaint.ROCK_PARTS
        assertTrue("Ein Fels aus zwei Rechtecken bleibt ein Klotz", parts.size >= 5)

        // Alle drei Farblagen müssen vorkommen. Fehlt die helle, fehlt die
        // Lichtseite — und ohne Lichtseite liest sich jede Silhouette als
        // Klotz, egal wie fein ihr Umriss ist.
        assertEquals("Der Fels braucht alle drei Farblagen", setOf(0, 1, 2), parts.map { it.tone }.toSet())

        parts.forEach {
            assertTrue("Ein Stück ohne Fläche zeichnet nichts", it.w > 0f && it.h > 0f)
            assertTrue("Kein Stück darf unter den Boden reichen", it.y >= 0f)
        }

        // Der Fuß ist die breiteste Lage — ein Stein steht auf seiner
        // Grundfläche, nicht auf seiner Spitze.
        val fuss = parts.filter { it.y == 0f }.sumOf { it.w.toDouble() }.toFloat()
        assertEquals("Die Grundfläche muss ROCK_WIDTH entsprechen", ScenePaint.ROCK_WIDTH, fuss, 1e-4f)
        parts.groupBy { it.y }.forEach { (y, lage) ->
            val breite = lage.sumOf { it.w.toDouble() }.toFloat()
            assertTrue("Die Lage bei $y ist breiter als der Fuß", breite <= fuss + 1e-4f)
        }

        // Die Höhe der Tabelle und die angegebene Höhe müssen sich decken,
        // sonst schneidet iOS die Textur an der Kuppe ab.
        val hoehe = parts.maxOf { it.y + it.h }
        assertEquals("ROCK_HEIGHT muss die Tabelle abdecken", ScenePaint.ROCK_HEIGHT, hoehe, 1e-4f)

        // Und der eigentliche Punkt: Der Umriss ist unsymmetrisch. Wäre er
        // mittig, hätten wir wieder den Stapel, nur mit mehr Rechtecken.
        val schwerpunkt = parts.sumOf { ((it.x + it.w / 2f) * it.w * it.h).toDouble() } /
            parts.sumOf { (it.w * it.h).toDouble() }
        assertTrue(
            "Der Fels ist mittig aufgebaut und damit wieder ein Stapel (Schwerpunkt $schwerpunkt)",
            schwerpunkt < -0.04
        )
    }

    @Test
    fun `die Laterne ist bis auf den Wert spiegelsymmetrisch`() {
        // Der eigentliche Punkt der Form. Ein Fels darf schief sein, er
        // ist gewachsen; eine Laterne ist gefertigt, und eine Sprosse
        // einen Hauch neben der Mitte liest sich sofort als Fehler.
        //
        // Der Test prüft das Modell, nicht das Bild: Zu jedem Stück muss
        // sein Spiegelstück in der Tabelle stehen — gleiche Höhe, gleiche
        // Breite, gleiche Farblage, an der gespiegelten Stelle. Mittige
        // Stücke sind ihr eigenes Spiegelstück.
        val teile = ScenePaint.LANTERN_PARTS
        teile.forEach { p ->
            val spiegelX = -(p.x + p.w)
            assertTrue(
                "Zu (${p.x}, ${p.y}, ${p.w}, ${p.h}) fehlt das Spiegelstück bei x=$spiegelX",
                teile.any {
                    nah(it.x, spiegelX) && nah(it.w, p.w) &&
                        nah(it.y, p.y) && nah(it.h, p.h) && it.tone == p.tone
                }
            )
        }

        // Und die Gegenprobe zur Hausregel: Die Lichtkanten liegen NICHT
        // links wie bei Baum, Strauch und Fels, sondern mittig. Eine
        // einseitige Lichtkante macht ein symmetrisches Bauwerk wieder
        // schief — sie ist hier bewusst abbestellt, nicht vergessen.
        teile.filter { it.tone == 2 }.forEach {
            assertEquals(
                "Die Lichtkante bei y=${it.y} sitzt nicht mittig",
                0f, it.x + it.w / 2f, 1e-4f
            )
        }
        assertTrue("Ohne Lichtkante bliebe der Mast eine Fläche", teile.any { it.tone == 2 })
    }

    @Test
    fun `die Laterne steht auf ihrer Fussplatte und leuchtet`() {
        val teile = ScenePaint.LANTERN_PARTS

        teile.forEach {
            assertTrue("Ein Stück ohne Fläche zeichnet nichts", it.w > 0f && it.h > 0f)
            assertTrue("Kein Stück darf unter den Boden reichen", it.y >= 0f)
        }

        // Höhe und Breite müssen die Tabelle abdecken, sonst schneidet
        // iOS die Textur ab — dort bestimmt LANTERN_WIDTH/HEIGHT die
        // Texturgröße, und was darüber hinausragt, ist einfach weg.
        val hoehe = teile.maxOf { it.y + it.h }
        assertEquals("LANTERN_HEIGHT muss die Tabelle abdecken", ScenePaint.LANTERN_HEIGHT, hoehe, 1e-4f)
        val breite = teile.maxOf { it.x + it.w } - teile.minOf { it.x }
        assertEquals("LANTERN_WIDTH muss die Tabelle abdecken", ScenePaint.LANTERN_WIDTH, breite, 1e-4f)

        // Sie steht auf ihrer Fußplatte: Die unterste Lage ist zugleich
        // die breiteste. Eine Laterne, die auf ihrer Leuchte balanciert,
        // wäre ein Kronleuchter.
        val fuss = teile.filter { it.y == 0f }.sumOf { it.w.toDouble() }.toFloat()
        assertEquals("Die Fußplatte muss LANTERN_WIDTH entsprechen", ScenePaint.LANTERN_WIDTH, fuss, 1e-4f)

        // Und der Grund, warum sie überhaupt existiert: Sie leuchtet.
        // Genau ein Stück trägt den Akzent — das Glas.
        assertEquals(
            "Die Laterne braucht genau ein leuchtendes Stück",
            1, teile.count { it.tone == 3 }
        )
        assertTrue(
            "Das Glas gehört in die obere Hälfte, nicht an den Mast",
            teile.first { it.tone == 3 }.y > hoehe / 2f
        )
    }

    @Test
    fun `die Laterne loest den Fels nur in der STADT ab`() {
        // Der Stein steht nur noch in der WÜSTE: Im MEER haben ihn Inseln
        // abgelöst, am BERG Tannen, und im WELTRAUM lasen sich die
        // treibenden Felsbrocken als graue Wolken — dort sind jetzt nur
        // Sterne. Die Laterne ist in der STADT deshalb eine Ergänzung,
        // kein Ersatz.
        SceneId.entries.forEach { id ->
            val laternen = ScenePaint.props(id).count { it.shape == PropShape.LATERNE }
            if (id == SceneId.STADT) {
                assertEquals("Die STADT braucht genau eine Laterne", 1, laternen)
            } else {
                assertEquals("$id hat keine Straße und braucht keine Laterne", 0, laternen)
            }
        }

        val fels = SceneId.entries.sumOf { id ->
            ScenePaint.props(id).count { it.shape == PropShape.FELS }
        }
        assertEquals("Der Fels steht nur in der WÜSTE, auf zwei Plätzen", 2, fels)

        val laterne = ScenePaint.props(SceneId.STADT).first { it.shape == PropShape.LATERNE }
        assertEquals("Eine Laterne auf der Straße wankt nicht", 0f, laterne.sway, 0f)
        // Auf einer Straße brennen alle Laternen in derselben Farbe —
        // ein zweiter Akzent würde Abwechslung behaupten, wo keine ist.
        assertEquals(
            "Die Laterne braucht genau eine Akzentfarbe",
            1, laterne.accents.size
        )
        // Und das Glas trägt kein neues Gelb, sondern das der Fenster.
        val fenster = ScenePaint.props(SceneId.STADT)
            .filter { it.shape == PropShape.HOCHHAUS }
            .flatMap { it.accents }
            .toSet()
        assertTrue(
            "Das Glas muss dasselbe Gelb tragen wie die Hochhausfenster",
            laterne.accents.first() in fenster
        )
    }

    @Test
    fun `die WIESE ist offen, alles andere haengt an Leistung`() {
        val leer = SkinStats(0, 0, 0)
        assertTrue(ScenePaint.isUnlocked(SceneId.WIESE, leer))
        SceneId.entries.filter { it != SceneId.WIESE }.forEach {
            assertFalse("$it darf ohne Leistung nicht offen sein", ScenePaint.isUnlocked(it, leer))
        }
        assertEquals(1, ScenePaint.unlockedCount(leer))
    }

    @Test
    fun `jede Kulisse haengt an ihrer eigenen Achse`() {
        // Die Welten-Leiter: je Welt eine Achse, alle früh erreichbar.
        assertTrue(ScenePaint.isUnlocked(SceneId.WUESTE, SkinStats(0, 0, 0, runCount = 100)))
        assertFalse(ScenePaint.isUnlocked(SceneId.WUESTE, SkinStats(0, 0, 0, runCount = 99)))
        assertTrue(ScenePaint.isUnlocked(SceneId.MEER, SkinStats(0, 0, 0, totalScore = 2_500)))
        assertFalse(ScenePaint.isUnlocked(SceneId.MEER, SkinStats(0, 0, 0, totalScore = 2_499)))
        assertTrue(ScenePaint.isUnlocked(SceneId.BERG, SkinStats(0, 0, 1)))
        assertFalse(ScenePaint.isUnlocked(SceneId.BERG, SkinStats(0, 0, 0)))
        assertTrue(ScenePaint.isUnlocked(SceneId.STADT, SkinStats(100, 0, 0)))
        assertFalse(ScenePaint.isUnlocked(SceneId.STADT, SkinStats(99, 0, 0)))
    }

    @Test
    fun `WUESTE und MEER fallen mit TIGER und BASKETBALL zusammen`() {
        // Gewollt (Plan 8.6 #11): Welt und Skin werden gemeinsam gefeiert.
        val laeufe = SkinStats(0, 0, 0, runCount = 100)
        assertTrue(SkinPaint.isUnlocked(SkinId.TIGER, laeufe))
        assertTrue(ScenePaint.isUnlocked(SceneId.WUESTE, laeufe))
        assertFalse(SkinPaint.isUnlocked(SkinId.TIGER, laeufe.copy(runCount = 99)))
        assertFalse(ScenePaint.isUnlocked(SceneId.WUESTE, laeufe.copy(runCount = 99)))
        val punkte = SkinStats(0, 0, 0, totalScore = 2_500)
        assertTrue(SkinPaint.isUnlocked(SkinId.BASKETBALL, punkte))
        assertTrue(ScenePaint.isUnlocked(SceneId.MEER, punkte))
        assertFalse(SkinPaint.isUnlocked(SkinId.BASKETBALL, punkte.copy(totalScore = 2_499)))
        assertFalse(ScenePaint.isUnlocked(SceneId.MEER, punkte.copy(totalScore = 2_499)))
    }

    @Test
    fun `der Weltraum schliesst die Kulissen-Sammlung ab`() {
        val fastAlles = maxStats.copy(bestScore = 99)
        assertFalse(
            "Solange die STADT fehlt, bleibt der WELTRAUM zu",
            ScenePaint.isUnlocked(SceneId.WELTRAUM, fastAlles)
        )
        assertTrue(ScenePaint.isUnlocked(SceneId.WELTRAUM, maxStats))
        assertEquals(SceneId.entries.size, ScenePaint.unlockedCount(maxStats))

        // Und er hängt an keinem Kauf: maxStats hat patronOwned = false.
        assertFalse(maxStats.patronOwned)
    }

    // ===== Bestandsschutz (Besitz-Menge) =====

    @Test
    fun `eine Welt in der Besitz-Menge bleibt offen, auch unter ihrer Schwelle`() {
        val rekord90 = SkinStats(90, 0, 0)
        assertFalse(
            "Rekord 90 reicht nach der neuen Regel nicht",
            ScenePaint.isUnlocked(SceneId.STADT, rekord90)
        )
        val mitBesitz = rekord90.copy(ownedScenes = setOf(SceneId.STADT.name))
        assertTrue(ScenePaint.isUnlocked(SceneId.STADT, mitBesitz))
        assertFalse("Die Regel selbst bleibt streng", ScenePaint.ruleMet(SceneId.STADT, mitBesitz))
        assertEquals(2, ScenePaint.unlockedCount(mitBesitz))
    }

    @Test
    fun `der Weltraum fragt die Besitz-Menge`() {
        // Alle anderen Welten aus dem Bestand, keine davon nach der
        // heutigen Regel: Der WELTRAUM muss trotzdem aufgehen.
        val alleAnderen = SceneId.entries.filter { it != SceneId.WELTRAUM }.map { it.name }.toSet()
        val stand = SkinStats(0, 0, 0, ownedScenes = alleAnderen)
        assertTrue(ScenePaint.isUnlocked(SceneId.WELTRAUM, stand))
        assertEquals(SceneId.entries.size, ScenePaint.unlockedCount(stand))
    }

    @Test
    fun `unbekannte Namen in der Besitz-Menge schaden nicht`() {
        val stand = SkinStats(0, 0, 0, ownedScenes = setOf("WOLKENKUCKUCKSHEIM"))
        assertEquals(1, ScenePaint.unlockedCount(stand))
        assertEquals(listOf(SceneId.WIESE.name), ScenePaint.unlockedNames(stand))
    }

    @Test
    fun `legacyUnlocked kennt die alten Schwellen und ignoriert den Besitz`() {
        assertTrue(ScenePaint.legacyUnlocked(SceneId.WUESTE, SkinStats(0, 0, 0, runCount = 500)))
        assertFalse(ScenePaint.legacyUnlocked(SceneId.WUESTE, SkinStats(0, 0, 0, runCount = 499)))
        assertTrue(ScenePaint.legacyUnlocked(SceneId.MEER, SkinStats(0, 0, 0, totalScore = 10_000)))
        assertFalse(ScenePaint.legacyUnlocked(SceneId.MEER, SkinStats(0, 0, 0, totalScore = 9_999)))
        assertTrue(ScenePaint.legacyUnlocked(SceneId.BERG, SkinStats(0, 0, 30)))
        assertFalse(ScenePaint.legacyUnlocked(SceneId.BERG, SkinStats(0, 0, 29)))
        assertTrue(ScenePaint.legacyUnlocked(SceneId.STADT, SkinStats(85, 0, 0)))
        assertFalse(ScenePaint.legacyUnlocked(SceneId.STADT, SkinStats(84, 0, 0)))
        val alt = SkinStats(85, 0, 30, runCount = 500, totalScore = 10_000)
        assertTrue(ScenePaint.legacyUnlocked(SceneId.WELTRAUM, alt))
        assertFalse(ScenePaint.legacyUnlocked(SceneId.WELTRAUM, alt.copy(bestScore = 84)))
        assertFalse(
            "Die Übernahme fragt allein den alten Stand",
            ScenePaint.legacyUnlocked(SceneId.STADT, SkinStats(0, 0, 0, ownedScenes = setOf("STADT")))
        )
        assertEquals(
            listOf("WIESE", "STADT"),
            ScenePaint.legacyUnlockedNames(SkinStats(90, 0, 0))
        )
    }

    @Test
    fun `gespeicherte Namen finden zurueck, alles andere landet auf der WIESE`() {
        SceneId.entries.forEach { assertEquals(it, ScenePaint.fromName(it.name)) }
        assertEquals(SceneId.WIESE, ScenePaint.fromName(null))
        assertEquals(SceneId.WIESE, ScenePaint.fromName("WOLKENKUCKUCKSHEIM"))
    }

    // ===== Werkzeug =====

    /** Abstand zweier ARGB-Farben im RGB-Raum. */
    private fun abstand(a: Long, b: Long): Float {
        var sum = 0f
        for (shift in intArrayOf(16, 8, 0)) {
            val d = (((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)).toFloat()
            sum += d * d
        }
        return sqrt(sum)
    }

    /**
     * Mindestabstand, den Kugel oder Rand einer Mine zu jedem Himmel
     * halten muss (Plan 8.5, AP-13).
     */
    private val MINE_CONTRAST = 150f

    /** Float-Vergleich für die Spiegelprobe — Tabellenwerte in 1/100. */
    private fun nah(a: Float, b: Float) = kotlin.math.abs(a - b) < 1e-4f

    private fun hex(color: Long): String = "#" + color.toString(16).uppercase().takeLast(6)

    @Test
    fun `Weltraum hat Sterne und Galaxien statt Felsen`() {
        val weltraum = ScenePaint.of(SceneId.WELTRAUM)
        assertTrue("Im WELTRAUM treibt vor den Sternen nichts", weltraum.props.isEmpty())
        val backdrop = weltraum.backdrop
        assertNotNull("Der WELTRAUM braucht einen Sternenhimmel", backdrop)
        assertEquals(BackdropKind.STERNENHIMMEL, backdrop!!.kind)
        assertEquals("Drei Sternfarben, Kern und zwei Arme", 6, backdrop.colors.size)
    }

    @Test
    fun `Berg hat ein Gebirge und Meer hat Inseln`() {
        val berg = ScenePaint.of(SceneId.BERG)
        assertEquals(BackdropKind.GEBIRGE, berg.backdrop?.kind)
        assertEquals("Zwei Ketten mit Schatten, Schnee mit Schatten", 6, berg.backdrop!!.colors.size)
        assertTrue(
            "Am BERG stehen nur Tannen",
            berg.props.all { it.shape == PropShape.NADELBAUM }
        )
        val meer = ScenePaint.props(SceneId.MEER)
        assertEquals("Im MEER treiben zwei Inseln", 2, meer.count { it.shape == PropShape.INSEL })
        assertTrue("Dazwischen Wellen", meer.any { it.shape == PropShape.WELLE })
        // Die übrigen Welten bleiben ohne Hintergrund.
        listOf(SceneId.WIESE, SceneId.WUESTE, SceneId.MEER, SceneId.STADT).forEach {
            assertNull("$it hat keinen Hintergrund", ScenePaint.of(it).backdrop)
        }
    }
}
