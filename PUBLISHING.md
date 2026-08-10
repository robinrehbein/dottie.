# PUNKT. — Weg in den Play Store

Fahrplan und Anleitungen für die Veröffentlichung. Stand: v2.9.

## Checkliste

- [ ] Keystore rotieren (Anleitung unten) — **vor dem ersten Store-Upload Pflicht**
- [ ] Play-Console-Konto anlegen (25 $ einmalig, [play.google.com/console](https://play.google.com/console))
- [ ] GitHub Pages aktivieren → Datenschutz-URL (Anleitung unten)
- [ ] Store-Eintrag anlegen (Texte unten, Icons liegen im Repo)
- [x] Feature-Grafik 1024×500 px (`store/feature-graphic.png`, Generator daneben)
- [ ] Mind. 2 Screenshots hochladen
- [ ] Optional: Play Games Services einrichten → Bestenlisten (Anleitung unten)
- [ ] Data-Safety-Formular: „Es werden keine Daten erhoben"
- [ ] IARC-Fragebogen (Content-Rating) ausfüllen
- [ ] `app-release.aab` in den **geschlossenen Test** hochladen
- [ ] 12 Tester einladen, 14 Tage testen lassen (Pflicht bei neuen Privat-Konten)
- [ ] Production-Freigabe beantragen

## Keystore-Rotation

Der eingecheckte `punkt-release-key.keystore` ist ein reiner
**Test-Keystore**: Sein Passwort (`punktapp123`) steht im Repo-Verlauf
und ist damit öffentlich. Für Test-APKs aus den GitHub-Releases ist das
egal — für den Play Store ist er tabu.

So rotierst du (einmalig, ~5 Minuten, auf deinem Rechner):

```bash
# 1. Neuen Keystore erzeugen — Passwort gut wählen und im
#    Passwort-Manager ablegen. Die Datei NIE ins Repo committen!
keytool -genkeypair -v \
  -keystore punkt-upload.keystore \
  -alias punkt -keyalg RSA -keysize 2048 -validity 10000

# 2. Als Base64 kopieren (macOS: pbcopy, Linux: xclip/wl-copy)
base64 -w0 punkt-upload.keystore | pbcopy
```

Dann im Repo unter **Settings → Secrets and variables → Actions** vier
Secrets anlegen:

| Secret | Wert |
|---|---|
| `PUNKT_KEYSTORE_BASE64` | der Base64-String aus Schritt 2 |
| `PUNKT_KEYSTORE_PASSWORD` | das gewählte Keystore-Passwort |
| `PUNKT_KEY_ALIAS` | `punkt` |
| `PUNKT_KEY_PASSWORD` | das gewählte Key-Passwort |

Ab dem nächsten CI-Lauf signiert der Build automatisch mit dem neuen
Keystore (der Workflow-Log meldet „Signiere mit rotiertem Keystore aus
Secret."). Danach:

- `punkt-release-key.keystore` aus dem Repo löschen und den
  Test-Fallback in `app/build.gradle.kts` entfernen
- ⚠️ Auf Testgeräten muss die App danach einmal **deinstalliert und neu
  installiert** werden — die Signatur ändert sich, ein Update über die
  alte Installation lehnt Android ab.

In der Play Console beim ersten Upload **Play App Signing** aktivieren
(Standard): Google verwahrt dann den eigentlichen App-Signing-Key, unser
Keystore ist nur der Upload-Key und ließe sich notfalls erneut tauschen.

## Datenschutzerklärung (Pflicht fürs Listing)

Liegt als statische Seite unter `docs/index.html`. Veröffentlichen:

1. Repo → **Settings → Pages**
2. Source: „Deploy from a branch", Branch `main`, Ordner `/docs`
3. Die URL (`https://robinrehbein.github.io/punkt./`) im Play-Listing
   als Datenschutz-URL eintragen

Vorher die Kontakt-E-Mail in `docs/index.html` prüfen/anpassen — sie
wird öffentlich sichtbar.

## Store-Eintrag (Entwurf)

**App-Name** (max. 30 Zeichen):

> PUNKT. — Timing-Arcade

**Kurzbeschreibung** (max. 80 Zeichen):

> Ein Punkt kreist. Ein Tap in der grünen Zone zählt. Wie weit kommst du?

**Vollständige Beschreibung** (max. 4000 Zeichen):

> **Ein Punkt. Ein Daumen. Kein Erbarmen.**
>
> PUNKT. ist pures Timing: Ein Punkt kreist auf seiner Bahn — tippe
> genau dann, wenn er die grüne Zone durchquert. Triffst du, geht es
> weiter. Verpasst du, ist der Run sofort vorbei. Kein Tutorial-Kram,
> keine Wartezeiten: Der nächste Versuch ist einen Tap entfernt.
>
> **PERFEKT gibt's obendrauf**
> Die helle Zonen-Mitte zählt als PERFEKT und startet eine Serie:
> +2, +3, +4 bis +5 Punkte pro Treffer. Die Schwierigkeit wächst mit
> deinen Treffern, nicht mit deinem Score — perfekte Serien sind reiner
> Bonus, kein Risiko.
>
> **Mit dem Score kommen die Twists**
> ▪ PULS — die Zone atmet, wird größer und kleiner
> ▪ DRIFT — die Zone wandert über die Bahn
> ▪ GEIST — der Punkt blinkt weg, behalte die Bahn im Kopf
> ▪ FALLE — violette Köder-Zone: bloß nicht hineintippen
> ▪ KETTE — zwei Zonen direkt nacheinander
> Maximal zwei Twists gleichzeitig, fair gemischt.
>
> **Retro, wie es sein soll**
> Pixel-Look, Chiptune-Sounds im NES-Stil — komplett im Code
> synthetisiert. Der Himmel färbt sich mit jeder Stufe Richtung Nacht,
> Medaillen belohnen deine Bestleistung, und wer seinen Rekord knackt,
> wird sofort gefeiert.
>
> **Ehrlich & schlank**
> Keine Werbung, keine In-App-Käufe, keine Datensammelei, kein
> Internet nötig. Nur du, der Punkt und dein Highscore.

**Kategorie:** Spiele → Arcade · **Tags:** Casual, Arcade, One-Tap

**Assets:**

- Feature-Grafik 1024×500: liegt fertig unter `store/feature-graphic.png`
  (Generator: `python3 store/generate_feature_graphic.py`)
- Noch offen: mind. 2 Screenshots (16:9 oder 9:16, am besten direkt vom
  Gerät: Startscreen, Lauf mit Twist, Game-Over mit Medaille)

## Play Games Services: Bestenlisten aktivieren

Der Code für die Bestenlisten (Rekord + Daily) ist ab v2.9 eingebaut,
aber **hart deaktiviert**, solange in `app/src/main/res/values/games.xml`
die Platzhalter stehen — ohne echte IDs wird das SDK nie initialisiert
und die App bleibt komplett offline. So wird es scharf geschaltet:

1. Play Console → **Grow → Play Games Services → Einrichtung** →
   Projekt anlegen (nutzt ein Google-Cloud-Projekt, die Console führt
   durch OAuth-Consent + Anmeldedaten; als Signatur zählt der
   App-Signing-Key aus Play App Signing).
2. Zwei **Bestenlisten** anlegen: „REKORD" und „DAILY" — die IDs
   (Format `CgkI…`) notieren.
3. In `games.xml` eintragen: `games_app_id` (die numerische Projekt-ID
   aus der Games-Einrichtung), `leaderboard_rekord_id`,
   `leaderboard_daily_id`. Committen, bauen, fertig — der
   RANGLISTE-Button erscheint automatisch nach erfolgreichem Sign-in.
4. ⚠️ **Datenschutz**: Mit Play Games meldet sich die App bei Google an —
   die Aussage „keine Datenerhebung" in `docs/index.html` und im
   Data-Safety-Formular muss dann angepasst werden (Google-Play-Games-
   Profil, Scores an Google). Ohne Aktivierung ändert sich nichts.

## Versionierung für Store-Uploads

Jeder Play-Upload braucht einen höheren `versionCode`
(`app/build.gradle.kts`). Aktuell: `versionCode 15` / `versionName
"2.9"`. Vor jedem Store-Upload beides anheben und committen.
