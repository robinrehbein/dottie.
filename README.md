# PUNKT.

Ein Hyper-Casual-Android-Spiel im Retro-Pixel-Look (Jetpack Compose,
alles im Code gezeichnet — keine Assets). Ein Punkt, ein Daumen, sofort
wieder ein Versuch: kurze Runs, hoher Rage-Faktor, Highscore-Jagd.

## Das Spiel: STOPP

Der Punkt kreist von allein auf einer Bahn. Ein Tap, während er in der
grünen Zone ist, zählt — daneben getippt oder die Zone überfahren ist
sofort das Ende. Die helle Zonen-Mitte zählt als PERFEKT: +2 Punkte,
und in Serie steigt der Bonus auf +3, +4 bis maximal +5 pro Treffer
(ein normaler Treffer setzt die Serie zurück, ohne Strafe).

Die physische Schwierigkeit (Tempo, Zonenbreite) wächst mit der Anzahl
der Treffer, nicht mit dem Score — perfekte Serien sind reiner Bonus
und beschleunigen das Spiel nicht doppelt. Twist-Freischaltungen und
Himmelsstufen bleiben Score-basiert, sie sind Belohnung.

Mit steigendem Score schalten sich Twists frei, die pro Zone zufällig
gemischt werden (maximal zwei gleichzeitig):

| Twist | Ab Score | Effekt |
|---|---|---|
| PULS | 5 | Die Zone atmet — wird größer und kleiner |
| DRIFT | 10 | Die Zone wandert langsam über die Bahn |
| GEIST | 15 | Der Punkt blinkt weg — Bahn im Kopf behalten |
| FALLE | 20 | Violette Köder-Zone: nie hineintippen |
| KETTE | 25 | Zwei Zonen nacheinander in gleicher Richtung |

Kuratierte Ausnahme: GEIST + FALLE erscheinen nie gleichzeitig —
unsichtbarer Punkt plus tödliche Köder-Zone wäre Zufalls-Tod statt
Skill. Alle anderen Kombinationen bleiben erlaubt.

Dazu: Himmel färbt sich pro 5er-Stufe Richtung Nacht, Medaillen ab
10/20/30/40 Punkten, Spott-Texte beim Tod, Haptik-Feedback, „?"-Button
mit Spielerklärung. Wer den eigenen Rekord im Lauf überholt, bekommt
das sofort gefeiert („REKORD GEKNACKT!") — nicht erst beim Tod.

## Sound (ab v2.7)

Chiptune-Soundeffekte im NES-Stil, komplett zur Laufzeit im Code
synthetisiert (Rechteckwellen + Rauschen, `ChipSynth`) — es gibt keine
Audio-Assets im Repo. Die WAVs werden einmalig in den App-Cache
geschrieben und laufen latenzarm über einen `SoundPool`:

- Treffer-Blip klettert innerhalb jeder 5er-Stufe eine Pentatonik hoch —
  jeder Lauf spielt seine eigene kleine Melodie
- PERFEKT ist ein Münz-Sound, der pro Serien-Stufe höher klingt
- Tod = fallender Sweep + Rausch-Burst, Twists/Stufen = Fanfare,
  neuer Rekord = eigener Jingle
- Ton-Schalter auf dem Startscreen („TON: AN/AUS"), Einstellung bleibt
  gespeichert

## Ehemaliger zweiter Modus: FLIP (entfernt in v2.6)

Bis v2.5 hatte die App einen zweiten Modus **FLIP** — einen
Gravity-Flip-Runner (Tap kippt die Schwerkraft, orangene Blocksäulen
ausweichen). Wir haben uns entschieden, uns erstmal voll auf STOPP zu
konzentrieren, und FLIP in v2.6 komplett entfernt.

**Falls FLIP zurückkommen soll:** Der letzte funktionierende Stand mit
beiden Modi ist als Tag [`v2.5-mit-flip`](../../tree/v2.5-mit-flip)
archiviert (Commit `defaab2`) — inklusive Modus-Umschalter,
Fairness-Tuning der Blocksäulen (v2.3/v2.4) und Hilfe-Overlay-Texten.
Die letzte APK mit FLIP ist das Release `apk-build-13` (v2.5).
Der gespeicherte FLIP-Highscore wird beim Update nicht gelöscht, nur
nicht mehr angezeigt.

Festgehaltene Design-Ideen für ein FLIP-Comeback („FLIP-Münzen"):

- Münzen als Sammel-Köder auf dem Scheitelpunkt echter Flip-Flugbahnen
  (Spawner kennt die Physik → Erreichbarkeit garantiert)
- Nur Sammeln, kein Schutz — Gier als Kern: riskante Münzen nahe an
  Säulen, „aus Gier gestorben" füttert den Rage-Faktor
- Münzwert +1 auf den normalen Score, keine zweite Währung, kein Shop
- Münzen ab Score 5, etwa jede 3.–4. Säule, Pixel-Explosion + Haptik
  beim Einsammeln, eigene Gier-Spott-Texte beim Tod

## Build

```
./gradlew assembleDebug assembleRelease bundleRelease
```

CI (GitHub Actions) baut bei jedem Push auf `main` beide APKs sowie das
App Bundle (`.aab`) für den Play Store, führt die Unit-Tests aus und
veröffentlicht alles als Pre-Release (`apk-build-N`-Tags) unter
Releases.

## Veröffentlichung

Der komplette Fahrplan Richtung Play Store — Keystore-Rotation,
Play-Console-Schritte, Datenschutz-URL (GitHub Pages aus `docs/`) und
die Store-Texte — steht in [PUBLISHING.md](PUBLISHING.md).

Der eingecheckte `punkt-release-key.keystore` ist ein reiner
Test-Keystore (Passwort öffentlich in der Git-Historie). Sobald das
Secret `PUNKT_KEYSTORE_BASE64` gesetzt ist, signiert die CI stattdessen
mit dem rotierten Keystore; Details in PUBLISHING.md.
