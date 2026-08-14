# Offene Punkte

Bewusst zurückgestellt, nicht vergessen. Reihenfolge ist keine Priorität —
die steht in der letzten Spalte.

## Vor dem nächsten Play-Release

| Punkt | Was genau | Wann |
|---|---|---|
| `versionCode` erhöhen | `app/build.gradle.kts` 31 → 32, `wear/build.gradle.kts` 100006 → 100007. Play lehnt einen Upload mit bereits benutztem Code ab. | beim Release |
| iOS einmal spielen | Kompiliert wird der Swift-Port **automatisch**: `build-ios.yml` läuft auf einem macOS-Runner bei jedem Push auf `main` und bei jedem PR, der `ios/**` anfasst — der Port der 42 Skins ist dort grün durchgelaufen. Was fehlt, ist kein Build, sondern ein Blick: einmal auf einem Gerät spielen und die neuen Skins, das Menü und die Kulissen ansehen. | vor dem iOS-Release |
| `patron_pack` in der Play Console | Produkt anlegen und aktivieren, siehe PUBLISHING.md Abschnitt 4c. Ohne das sind Diamant, Phönix und Onyx unerreichbar. | beim Release |

## Entscheidungen, die noch offen sind

| Punkt | Worum es geht |
|---|---|
| Gönner-Zeile für Besitzer von `remove_ads` | Wer Werbefreiheit schon gekauft hat, zahlt sie im Gönner-Paket ein zweites Mal. Play kennt keinen Upgrade-Pfad für Einmalprodukte, also muss die App die Zeile für diese Gruppe anders beschriften („GOENNER-PAKET — DREI SKINS"). |
| Billing von Werbung entkoppeln | Der BillingClient startet heute nur, wenn AdMob-IDs gesetzt sind (`AdsManager.configured`). Ohne Werbung also kein Gönner-Paket. Auftrennen, falls das Paket unabhängig verkaufbar sein soll. |
| Gönner-Skins auf Web und iOS | Aktuell sichtbar, aber gesperrt, mit Hinweis „nur in der App". Alternative: ganz ausblenden, solange es dort kein Billing gibt. |
| AURORA und der Grünbereich | AURORA läuft wellenweise durch den Farbbereich der Zielzone, während REGENBOGEN ihn ausdrücklich überspringt. Bleibt vorerst so — sie ist der Preis für 14 Tage Daily-Serie, und eine nachträgliche Umfärbung nimmt jemandem die Farbe seiner verdienten Belohnung. Der Test dokumentiert die Ausnahme; die Änderung wäre eine Zeile. |

## Ideen mit Beschluss „später"

| Punkt | Kurz |
|---|---|
| Schweif als eigene Ebene | Heute hängt der Schweif am Skin (TINTE, PHÖNIX). Als unabhängiger, kombinierbarer Modifikator wird daraus eine zweite Sammlung — 42 Skins × N Schweife, und die Zeichenroutine existiert bereits (`TRAIL_STEPS`, `TRAIL_SPACING`). |
| Ton-Sets | `ChipSynth` erzeugt alles zur Laufzeit, es gibt keine Audio-Assets. Ein zweites Wellenform-Set ist dieselbe Art Arbeit wie ein Skin, nur für die Ohren. |
| Score-Karten-Rahmen und Beinamen | Rahmen bei 10/20/30 gesammelten Skins, Titel aus vorhandenen Zahlen („STEHAUFMAENNCHEN" ab 500 Läufen). Erscheint auf dem, was geteilt wird. |
| Tod-Animationen | Alternativen zum Mario-Hüpfer (Pixel-Explosion, Luft ablassen). Reines Feedback nach dem Lauf, berührt keine Fairness — Dauer und Bodenlinie müssen gleich bleiben, sonst verschiebt sich die Sperre gegen Wut-Taps. |

Beispiele zu allen vier stehen im Vorschlags-Artefakt „Perlenkette und Kulisse".
