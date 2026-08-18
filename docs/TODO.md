# Offene Punkte

Bewusst zurückgestellt, nicht vergessen. Reihenfolge ist keine Priorität —
die steht in der letzten Spalte.

## Vor dem nächsten Play-Release

| Punkt | Was genau | Wann |
|---|---|---|
| `versionCode` erhöhen | Steht aktuell auf 33 / „2.22" (`app/build.gradle.kts`) und 100007 / „0.2.5-wear" (`wear/build.gradle.kts`). Vor jedem Upload weiterzählen — Play lehnt einen bereits benutzten Code ab. | beim Release |
| iOS einmal spielen | Kompiliert wird der Swift-Port **automatisch**: `build-ios.yml` läuft auf einem macOS-Runner bei jedem Push auf `main` und bei jedem PR, der `ios/**` anfasst — der Port der 42 Skins ist dort grün durchgelaufen. Was fehlt, ist kein Build, sondern ein Blick: einmal auf einem Gerät spielen und die neuen Skins, das Menü und die Kulissen ansehen. | vor dem iOS-Release |
| `patron_pack` in der Play Console | Produkt anlegen und aktivieren, siehe PUBLISHING.md Abschnitt 4c. Ohne das sind Diamant, Phönix und Onyx unerreichbar. | beim Release |

## Entscheidungen, die noch offen sind

| Punkt | Worum es geht |
|---|---|
| Gönner-Skins auf iOS | Aktuell sichtbar, aber gesperrt, mit Hinweis „nur in der App". Alternative: ganz ausblenden, solange es dort kein Billing gibt. Betraf bis v2.22 auch die PWA — die ist mit der Konzentration auf die nativen Apps entfallen. |
| Fallen-Zone: Form | **Entschieden — bleibt wie sie ist.** Hohler Kern, Kreuz und Zacken wurden verworfen: Das Spiel kennt genau ein visuelles Grundelement, den gefüllten Block mit Kontur. Ein hohler Block wäre ein zweites, das sonst nirgends vorkommt. Bekannte Folge: Vor dem lila Himmel (Score 10–14 und 50–54) hebt sich die Falle kaum ab, der FALLE-Twist wirkt dort schwächer. Das ist Textur, keine Unfairness — wer die Falle nicht sieht, tippt auch nicht hinein. Ein Nachziehen des Violetts Richtung Magenta wäre eine Zeile, falls es später doch stören sollte. |
| Bahn: Blöcke drehen | **Entschieden — bleibt achsparallel.** Gemessen, nicht geschätzt: Die gedrehte Variante kostet 1,1× so viele weiche Pixel im Ringband (2.843 → 3.251), und Kantenflimmern gibt es ohnehin nicht, weil der Ring stillsteht — der technische Einwand war also schwächer als zunächst behauptet. Ausschlaggebend war ein anderer: Der gedrehte Ring wäre das einzige gedrehte Element im ganzen Spiel; Vogel, Wolken, Bäume, Boden, Medaillen und alle Overlays stehen im Raster. |
| AURORA und der Grünbereich | AURORA läuft wellenweise durch den Farbbereich der Zielzone, während REGENBOGEN ihn ausdrücklich überspringt. Bleibt vorerst so — sie ist der Preis für 14 Tage Daily-Serie, und eine nachträgliche Umfärbung nimmt jemandem die Farbe seiner verdienten Belohnung. Der Test dokumentiert die Ausnahme; die Änderung wäre eine Zeile. |

## Gebaut, seit diese Liste zuletzt stimmte

Drei Punkte sind von „später" nach „drin" gewandert und stehen deshalb
nicht mehr unten: die **Ton-Sets** (KLASSIK / GLOCKE / AMBOSS, wählbar
mit Hörprobe), **Rahmen und Beinamen** der Score-Karte samt eigener
Auswahl, und die **Dreieckwelle**, ohne die die GLOCKE nur ein hoch
gestimmter Bestand gewesen wäre.

Damit stehen vier Sammlungen nebeneinander — Skins, Kulissen, Töne,
Rahmen — und in allen vieren gilt dieselbe Regel: Der Bestand ist die
erste Stufe und bleibt unangetastet (WIESE, KLASSIK, SCHLICHT).

## Ideen mit Beschluss „später"

| Punkt | Kurz |
|---|---|
| Schweif als eigene Ebene | Heute hängt der Schweif am Skin (TINTE, PHÖNIX). Als unabhängiger, kombinierbarer Modifikator wird daraus eine zweite Sammlung — 42 Skins × N Schweife, und die Zeichenroutine existiert bereits (`TRAIL_STEPS`, `TRAIL_SPACING`). |
| Rahmen im Paritäts-Vertrag | `CardStyle` gibt es nur in Kotlin: Die Score-Karte ist heute Android-allein, iOS teilt nicht. Deshalb stehen Rahmen und Beinamen bewusst **nicht** in `parity/golden-vectors.txt` — der Vertrag deckt, was beide Seiten haben. Sobald iOS teilt, gehören sie hinein, sonst driften die Stufen auseinander. |
| Rahmen auf der Uhr | Der Rahmen ist die einzige der vier Sammlungen, die nicht mit der Uhr abgeglichen wird — sie hat keine Score-Karte (siehe `ScoreStore.selectedCardFrame`). Bekäme sie eine, fehlte der Abgleich. |
| Eine dritte Wellenform | `ChipSynth` kann Rechteck und Dreieck. Eine Säge fehlt bewusst: Der Chip, dem das Spiel seinen Klang schuldet, hatte keine. Wer sie hinzufügt, sollte erst sagen können, welches Set sie braucht. |
| Tod-Animationen | Alternativen zum Mario-Hüpfer (Pixel-Explosion, Luft ablassen). Reines Feedback nach dem Lauf, berührt keine Fairness — Dauer und Bodenlinie müssen gleich bleiben, sonst verschiebt sich die Sperre gegen Wut-Taps. |
| Eigene Requisite für die STADT | Der vierte Requisiten-Platz der STADT trägt einen Fels — auf Asphalt, neben Hochhäusern. Der Umriss ist inzwischen als Stein erkennbar, aber ein Findling auf der Straße bleibt er. Vorschlag: eine Laterne, schmal und hoch, die das Fenstergelb `0xFFFFD847` aufnimmt und die Straße abends mitleuchten lässt. Kostet eine neue `PropShape` samt Zeichenroutine in drei Ports. |
| Startbildschirm entrümpeln | Vierzehn Elemente, sechs davon untereinander im unteren Drittel. Sechs Entwürfe stehen im Artefakt „Weniger auf dem Startbildschirm"; Empfehlung ist Entwurf B: Einstellungs-Blatt hinter einem Zahnrad, Rangliste und Versuchszähler in die Statistik, und die vier Zahlenzeilen ersetzt durch den vorhandenen `GoalBar` zum nächsten Skin. |
| Achse in der Ziel-Zeile benennen | `goal_progress` zeigt „MEDAILLE 199/200", ohne zu sagen, was gezählt wird — das liest sich wie Medaillen, sind aber Läufe. Die Wörter existieren bereits als `skin_hint_*` („200 LAEUFE"); die Zeile muss sie nur mitbenutzen statt eine nackte Zahl zu zeigen. |

Beispiele zu den ersten vier stehen im Vorschlags-Artefakt „Perlenkette und Kulisse".
