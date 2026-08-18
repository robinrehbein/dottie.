# Offene Punkte

Bewusst zurückgestellt, nicht vergessen. Reihenfolge ist keine Priorität —
die steht in der letzten Spalte.

## Vor dem nächsten Play-Release

| Punkt | Was genau | Wann |
|---|---|---|
| `versionCode` erhöhen | Steht aktuell auf 34 / „2.23" (`app/build.gradle.kts`) und 100008 / „0.2.6-wear" (`wear/build.gradle.kts`). Vor jedem Upload weiterzählen — Play lehnt einen bereits benutzten Code ab. | beim Release |
| iOS einmal spielen | Gebaut wird **automatisch**: `build-ios.yml` läuft auf einem macOS-Runner bei jedem Push auf `main` oder `claude/**`, der `ios/`, `core/`, `ui/` oder `parity/` anfasst (kein PR-Trigger — das Repo braucht keinen zweiten, siehe Kommentar im Workflow) — seit #54 dieselbe `:ui`, die Android zeichnet, nicht mehr ein separater Port. Was fehlt, ist kein Build, sondern ein Blick: einmal auf einem Gerät spielen und die 42 Skins, das Menü und die Kulissen ansehen. | vor dem iOS-Release |
| `patron_pack` in der Play Console | Produkt anlegen und aktivieren, siehe PUBLISHING.md Abschnitt 4c. Ohne das sind Diamant, Phönix und Onyx unerreichbar. | beim Release |

## Entscheidungen, die noch offen sind

| Punkt | Worum es geht |
|---|---|
| Gönner-Skins auf iOS | Aktuell sichtbar, aber gesperrt, mit Hinweis „nur in der App". Alternative: ganz ausblenden, solange es dort kein Billing gibt. Betraf bis v2.22 auch die PWA — die ist mit der Konzentration auf die nativen Apps entfallen. |
| Fallen-Zone: Form | **Entschieden — bleibt wie sie ist.** Hohler Kern, Kreuz und Zacken wurden verworfen: Das Spiel kennt genau ein visuelles Grundelement, den gefüllten Block mit Kontur. Ein hohler Block wäre ein zweites, das sonst nirgends vorkommt. Bekannte Folge: Vor dem lila Himmel (Score 10–14 und 50–54) hebt sich die Falle kaum ab, der FALLE-Twist wirkt dort schwächer. Das ist Textur, keine Unfairness — wer die Falle nicht sieht, tippt auch nicht hinein. Ein Nachziehen des Violetts Richtung Magenta wäre eine Zeile, falls es später doch stören sollte. |
| Bahn: Blöcke drehen | **Entschieden — bleibt achsparallel.** Gemessen, nicht geschätzt: Die gedrehte Variante kostet 1,1× so viele weiche Pixel im Ringband (2.843 → 3.251), und Kantenflimmern gibt es ohnehin nicht, weil der Ring stillsteht — der technische Einwand war also schwächer als zunächst behauptet. Ausschlaggebend war ein anderer: Der gedrehte Ring wäre das einzige gedrehte Element im ganzen Spiel; Vogel, Wolken, Bäume, Boden, Medaillen und alle Overlays stehen im Raster. |
| AURORA und der Grünbereich | AURORA läuft wellenweise durch den Farbbereich der Zielzone, während REGENBOGEN ihn ausdrücklich überspringt. Bleibt vorerst so — sie ist der Preis für 14 Tage Daily-Serie, und eine nachträgliche Umfärbung nimmt jemandem die Farbe seiner verdienten Belohnung. Der Test dokumentiert die Ausnahme; die Änderung wäre eine Zeile. |
| Ziel-Zeilen: wo die Achse steht und wo nicht | **Entschieden — zwei Zeilen, zwei Längen.** Der Startbildschirm zeigt `goalHeadline` mit ausgeschriebener Achse („NAECHSTER SKIN: MEDAILLE — 199/200 LAEUFE", `GameOverlays.kt`): Das Ziel steht dort allein, ohne Überschrift, und muss deshalb selbst sagen, was gezählt wird. Game-Over und Statistik zeigen dagegen weiterhin `goalLabel`, die kurze Fassung ohne Achse („MEDAILLE 199/200", `stringResource(Res.string.goal_progress, …)` in `StatsOverlay.kt`, genutzt sowohl unter der Statistik-Überschrift „NAECHSTE ZIELE" als auch im Game-Over direkt über dem Balken) — bewusst kurz belassen, nicht übersehen: Die lange Fassung passt in beiden Fällen schlechter, weil daneben schon ein Balken steht, der den Fortschritt zeigt, und im Game-Over zusätzlich, weil wer gerade gestorben ist, neu starten will, nicht eine zweite Zeile lesen. |

## Gebaut, seit diese Liste zuletzt stimmte

Fünf Punkte sind von „später" nach „drin" gewandert und stehen deshalb
nicht mehr unten: die **Ton-Sets** (KLASSIK / GLOCKE / AMBOSS, wählbar
mit Hörprobe), **Rahmen und Beinamen** der Score-Karte samt eigener
Auswahl, die **Dreieckwelle**, ohne die die GLOCKE nur ein hoch
gestimmter Bestand gewesen wäre, die **Laterne** auf dem vierten
Platz der STADT, und der **entrümpelte Startbildschirm** (#53, Entwurf B):
Zahnrad statt sechs einzelner Zeilen, Einstellungen in einem eigenen
Overlay, Rangliste und Versuchszähler in die Statistik verschoben, die
Daily-Serie als Abzeichen am DAILY-Knopf statt als eigene Zeile, und die
vier Zahlenzeilen ersetzt durch eine Ziel-Zeile mit `GoalBar` zum
nächsten Skin — inklusive Achse, siehe „Ziel-Zeilen: wo die Achse steht
und wo nicht" unten.

Zur Laterne zwei Dinge, die beim Bauen anders lagen als gedacht. Erstens
kostet eine neue `PropShape` **zwei** Renderer, nicht drei: Die Uhr
zeichnet nur den Himmel, keine Requisiten. Zweitens löst die Laterne den
Fels nur an dieser einen Stelle ab — er bleibt an neun weiteren, und im
WELTRAUM sind alle vier Requisiten Felsen. Ein `ScenePaintTest` nagelt
beide Zahlen fest.

Damit stehen vier Sammlungen nebeneinander — Skins, Kulissen, Töne,
Rahmen — und in allen vieren gilt dieselbe Regel: Der Bestand ist die
erste Stufe und bleibt unangetastet (WIESE, KLASSIK, SCHLICHT).

## Ideen mit Beschluss „später"

| Punkt | Kurz |
|---|---|
| Schweif als eigene Ebene | Heute hängt der Schweif am Skin (TINTE, PHÖNIX). Als unabhängiger, kombinierbarer Modifikator wird daraus eine zweite Sammlung — 42 Skins × N Schweife, und die Zeichenroutine existiert bereits (`TRAIL_STEPS`, `TRAIL_SPACING`). |
| Rahmen im Paritäts-Vertrag | `CardStyle` liegt seit #54 in `:core` wie alles andere — die alte Begründung „nur in Kotlin" ist hinfällig. Was bleibt: Die **gerenderte** Score-Karte (`ScoreCard.kt`, `android.graphics`) ist weiterhin Android-allein, iOS teilt sie nicht — der Rahmen ist zwar wählbar (die Auswahl-UI liegt in `:ui` und läuft auf beiden Plattformen), aber nirgends sichtbar, solange iOS nichts zum Teilen hat. Deshalb stehen Rahmen und Beinamen bewusst **nicht** in `parity/golden-vectors.txt` — der Vertrag deckt, was auf allen Plattformen sichtbar wird. Sobald iOS eine Score-Karte teilt, gehören sie hinein, sonst driften die Stufen auseinander. |
| Rahmen auf der Uhr | Der Rahmen ist die einzige der vier Sammlungen, die nicht mit der Uhr abgeglichen wird — sie hat keine Score-Karte (siehe `GameStore.selectedCardFrame` in `:ui`, vormals `ScoreStore`). Bekäme sie eine, fehlte der Abgleich. |
| Eine dritte Wellenform | `ChipSynth` kann Rechteck und Dreieck. Eine Säge fehlt bewusst: Der Chip, dem das Spiel seinen Klang schuldet, hatte keine. Wer sie hinzufügt, sollte erst sagen können, welches Set sie braucht. |
| Tod-Animationen | Alternativen zum Mario-Hüpfer (Pixel-Explosion, Luft ablassen). Reines Feedback nach dem Lauf, berührt keine Fairness — Dauer und Bodenlinie müssen gleich bleiben, sonst verschiebt sich die Sperre gegen Wut-Taps. |

Beispiele zu den ersten vier stehen im Vorschlags-Artefakt „Perlenkette und Kulisse".
