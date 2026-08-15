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
| Gönner-Skins auf Web und iOS | Aktuell sichtbar, aber gesperrt, mit Hinweis „nur in der App". Alternative: ganz ausblenden, solange es dort kein Billing gibt. |
| Fallen-Zone: Form | **Entschieden — bleibt wie sie ist.** Hohler Kern, Kreuz und Zacken wurden verworfen: Das Spiel kennt genau ein visuelles Grundelement, den gefüllten Block mit Kontur. Ein hohler Block wäre ein zweites, das sonst nirgends vorkommt. Bekannte Folge: Vor dem lila Himmel (Score 10–14 und 50–54) hebt sich die Falle kaum ab, der FALLE-Twist wirkt dort schwächer. Das ist Textur, keine Unfairness — wer die Falle nicht sieht, tippt auch nicht hinein. Ein Nachziehen des Violetts Richtung Magenta wäre eine Zeile, falls es später doch stören sollte. |
| Bahn: Blöcke drehen | **Entschieden — bleibt achsparallel.** Gemessen, nicht geschätzt: Die gedrehte Variante kostet 1,1× so viele weiche Pixel im Ringband (2.843 → 3.251), und Kantenflimmern gibt es ohnehin nicht, weil der Ring stillsteht — der technische Einwand war also schwächer als zunächst behauptet. Ausschlaggebend war ein anderer: Der gedrehte Ring wäre das einzige gedrehte Element im ganzen Spiel; Vogel, Wolken, Bäume, Boden, Medaillen und alle Overlays stehen im Raster. |
| AURORA und der Grünbereich | AURORA läuft wellenweise durch den Farbbereich der Zielzone, während REGENBOGEN ihn ausdrücklich überspringt. Bleibt vorerst so — sie ist der Preis für 14 Tage Daily-Serie, und eine nachträgliche Umfärbung nimmt jemandem die Farbe seiner verdienten Belohnung. Der Test dokumentiert die Ausnahme; die Änderung wäre eine Zeile. |

## Ideen mit Beschluss „später"

| Punkt | Kurz |
|---|---|
| Schweif als eigene Ebene | Heute hängt der Schweif am Skin (TINTE, PHÖNIX). Als unabhängiger, kombinierbarer Modifikator wird daraus eine zweite Sammlung — 42 Skins × N Schweife, und die Zeichenroutine existiert bereits (`TRAIL_STEPS`, `TRAIL_SPACING`). |
| Ton-Sets | `ChipSynth` erzeugt alles zur Laufzeit, es gibt keine Audio-Assets. Ein zweites Wellenform-Set ist dieselbe Art Arbeit wie ein Skin, nur für die Ohren. |
| Score-Karten-Rahmen und Beinamen | Rahmen bei 10/20/30 gesammelten Skins, Titel aus vorhandenen Zahlen („STEHAUFMAENNCHEN" ab 500 Läufen). Erscheint auf dem, was geteilt wird. |
| Tod-Animationen | Alternativen zum Mario-Hüpfer (Pixel-Explosion, Luft ablassen). Reines Feedback nach dem Lauf, berührt keine Fairness — Dauer und Bodenlinie müssen gleich bleiben, sonst verschiebt sich die Sperre gegen Wut-Taps. |
| Eigene Requisite für die STADT | Der vierte Requisiten-Platz der STADT trägt einen Fels — auf Asphalt, neben Hochhäusern. Der Umriss ist inzwischen als Stein erkennbar, aber ein Findling auf der Straße bleibt er. Vorschlag: eine Laterne, schmal und hoch, die das Fenstergelb `0xFFFFD847` aufnimmt und die Straße abends mitleuchten lässt. Kostet eine neue `PropShape` samt Zeichenroutine in drei Ports. |
| Startbildschirm entrümpeln | Vierzehn Elemente, sechs davon untereinander im unteren Drittel. Sechs Entwürfe stehen im Artefakt „Weniger auf dem Startbildschirm"; Empfehlung ist Entwurf B: Einstellungs-Blatt hinter einem Zahnrad, Rangliste und Versuchszähler in die Statistik, und die vier Zahlenzeilen ersetzt durch den vorhandenen `GoalBar` zum nächsten Skin. |
| Achse in der Ziel-Zeile benennen | `goal_progress` zeigt „MEDAILLE 199/200", ohne zu sagen, was gezählt wird — das liest sich wie Medaillen, sind aber Läufe. Die Wörter existieren bereits als `skin_hint_*` („200 LAEUFE"); die Zeile muss sie nur mitbenutzen statt eine nackte Zahl zu zeigen. |

Beispiele zu den ersten vier stehen im Vorschlags-Artefakt „Perlenkette und Kulisse".
