# Prompt für die Umsetzung (neue Session)

In eine neue Claude-Code-Session mit dem Repo `robinrehbein/dottie.` kopieren.

```text
ultracode

Setze den Plan in docs/plan-feedback-ux.md vollständig um. Abschnitt 8
(Arbeitspakete, Wellen, Datei-Besitz, API-Verträge, Merge-Reihenfolge) ist
verbindlich. 8.6 enthält die getroffenen Entscheidungen, 8.7 die Korrekturen
an Abschnitt 3–7. Wo 3–7 und 8 sich widersprechen, gilt 8. Frag nicht nach
Dingen, die dort schon entschieden sind.

Vorbereitung (bevor ein Agent startet):
1. Lies den ganzen Plan, dazu docs/feedback-check.html und
   docs/bedienelemente.html (dort stehen die Sprites HAND und MINE, die Farben
   und das Verhalten der Mockups).
2. Gradle-Cache füllen: einmal online `./gradlew :core:jvmTest :ui:jvmTest
   :app:assembleDebug :wear:assembleDebug`. Bei HTTP 429 von Maven Central
   mit steigenden Pausen (30 s, 60 s, 120 s) wiederholen. Erst danach gilt
   `--offline` aus 8.1.
3. Integrationsbranch aus 8.1 von main anlegen. Gibt die Session einen
   eigenen Branch vor, nimm diesen als Integrationsbranch.

Durchführung:
- Pro Welle (0, 1a, 1b, 2a, 2b, 3) ein eigener Workflow. Jedes Paket läuft
  als eigener Agent mit isolation: 'worktree' auf seinem Paket-Branch und
  ändert nur, was ihm in 8.2/8.4 gehört. Pakete einer Welle laufen parallel.
  Wegen 4 Kernen höchstens 2 Gradle-Läufe gleichzeitig (8.1 Punkt 2).
- Nach jedem Paket prüft ein zweiter, unabhängiger Agent adversarial jedes
  Akzeptanzkriterium aus 8.5 mit echten Befehlen und Screenshots und darf
  nur „bestanden“ melden, wenn alles belegt ist. Nicht bestanden → zurück an
  den Paket-Agenten, bis es grün ist. Tests nie abschwächen oder
  überspringen.
- Merge auf den Integrationsbranch genau in der Reihenfolge aus 8.4. Nach
  AP-02, AP-13, AP-21 und AP-31 die Golden Vectors mit
  `./gradlew --offline :core:jvmTest -Dparity.update=true` neu erzeugen und
  den Diff wie im Paket beschrieben prüfen. Nie von Hand zusammenführen.
- Nach jeder Welle auf dem Integrationsbranch:
  `./gradlew --offline :core:jvmTest testDebugUnitTest :ui:jvmTest
  assembleDebug`, pushen, CI (build-apk.yml, build-ios.yml) abwarten und
  rote Checks beheben, bevor die nächste Welle startet.
- Zwischen den Wellen mir kurz berichten: was fertig ist, was offen ist,
  Screenshot-Pfade. Anhalten nur bei echten Blockern.

Spielregeln: Außer der Startregel, dem Nebel (Twist.GHOST), BLIND! +1 und den
Welten-Schwellen mit Bestandsschutz ändert sich nichts an der Mechanik.
Tempo, Zonenbreite, Punkte und Twist-Auswahl bleiben unverändert.

Abschluss:
- Kein Merge nach main. Öffne einen Draft-PR vom Integrationsbranch nach
  main. Die Beschreibung folgt den Paketen, enthält die wichtigsten
  Screenshots (Startbildschirm mit Hand, NOCH NICHT, alle vier
  Todesursachen, Bomben mit Lauflicht und Explosion, Nebel, Game-Over-Leiste,
  Sammlung mit allen Reitern, Himmel bei Score 10 in allen Welten) und die
  Liste der Prüfungen, die nur ein Mensch am Gerät machen kann (AP-31).
- Commit-Nachrichten und Texte auf Deutsch, Umlaute nach 8.1 Punkt 9.
```
