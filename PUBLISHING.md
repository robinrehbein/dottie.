# DOTTIE. — Weg in den Play Store

Fahrplan und Anleitungen für die Veröffentlichung. Stand: v2.16.

## Checkliste

- [ ] Keystore rotieren (Anleitung unten) — **vor dem ersten Store-Upload Pflicht**
- [x] Play-Console-Konto anlegen (25 $ einmalig, [play.google.com/console](https://play.google.com/console))
- [ ] Play-API-Service-Konto anlegen → Secret `PLAY_SERVICE_ACCOUNT_JSON` (Anleitung unten)
- [ ] GitHub Pages aktivieren → Datenschutz-URL (Anleitung unten)
- [ ] Store-Eintrag anlegen (Texte unten in Deutsch UND Englisch, Icons liegen im Repo)
- [x] Feature-Grafik 1024×500 px (`store/feature-graphic.png`, Generator daneben)
- [x] Screenshots 1080×1920: je 4 in DE und EN unter `store/screenshots/`
      (Generator: `python3 store/generate_screenshots.py`)
- [ ] Optional: Play Games Services einrichten → Bestenlisten (Anleitung unten)
- [ ] Optional: AdMob + In-App-Kauf „remove_ads" aktivieren (Anleitung
      unten) — solange das nicht passiert, ist die App komplett werbefrei
- [ ] Data-Safety-Formular: „Es werden keine Daten erhoben" (gilt nur
      ohne AdMob — mit Werbung siehe Abschnitt unten)
- [ ] IARC-Fragebogen (Content-Rating) ausfüllen
- [ ] `app-release.aab` in den **geschlossenen Test** hochladen (manuell
      oder per CI-Job `play-internal`, siehe unten)
- [ ] Optional: Wear-App mitverteilen — Formfaktor Wear OS aktivieren und
      `wear-release.aab` mit hochladen (Anleitung unten)
- [ ] 12 Tester einladen, 14 Tage testen lassen (Pflicht bei neuen Privat-Konten)
- [ ] Production-Freigabe beantragen

Die App ist ab v2.11 **zweisprachig**: Englisch ist die Standardsprache,
Deutsch wird auf deutschen Geräten automatisch gewählt. Im Play-Listing
also am besten `en-US` als Standard anlegen und `de-DE` als Übersetzung
hinzufügen — beide Textfassungen stehen unten.

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

Liegt als statische Seite unter `docs/index.html`. Veröffentlicht wird
sie zusammen mit der Web-PWA über den Workflow
`.github/workflows/deploy-pages.yml` (läuft bei jedem Push auf `main`,
der `web/` oder `docs/` ändert):

- `https://robinrehbein.github.io/dottie./` → das Spiel (Web-PWA)
- `https://robinrehbein.github.io/dottie./datenschutz/` → die
  Datenschutzerklärung — **diese URL** im Play-Listing als
  Datenschutz-URL eintragen

Einmalige Voraussetzung: Repo → **Settings → Pages** → Source
„GitHub Actions" (geht nur bei öffentlichem Repo oder mit GitHub Pro).

Vorher die Kontakt-E-Mail in `docs/index.html` prüfen/anpassen — sie
wird öffentlich sichtbar.

## Store-Eintrag (Entwurf)

**App-Name** (max. 30 Zeichen):

> DOTTIE. — Timing-Arcade

**Kurzbeschreibung** (max. 80 Zeichen):

> Ein Punkt kreist. Ein Tap in der grünen Zone zählt. Wie weit kommst du?

**Vollständige Beschreibung** (max. 4000 Zeichen):

> **Ein Punkt. Ein Daumen. Kein Erbarmen.**
>
> DOTTIE. ist pures Timing: Ein Punkt kreist auf seiner Bahn — tippe
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

### Store-Eintrag Englisch (en-US)

**App name** (max. 30 Zeichen):

> DOTTIE. — Timing Arcade

**Short description** (max. 80 Zeichen):

> A dot circles. One tap in the green zone counts. How far can you get?

**Full description** (max. 4000 Zeichen):

> **One dot. One thumb. No mercy.**
>
> DOTTIE. is pure timing: a dot circles its track — tap exactly when it
> crosses the green zone. Hit it and you keep going. Miss it and the run
> is over on the spot. No tutorials, no waiting: the next attempt is one
> tap away.
>
> **PERFECT pays extra**
> The bright center of the zone counts as PERFECT and starts a streak:
> +2, +3, +4 up to +5 points per hit. Difficulty grows with your hits,
> not your score — perfect streaks are pure bonus, zero risk.
>
> **The twists arrive with your score**
> ▪ PULSE — the zone breathes, growing and shrinking
> ▪ DRIFT — the zone wanders along the track
> ▪ GHOST — the dot blinks away, keep the track in your head
> ▪ TRAP — a purple decoy zone: never tap into it
> ▪ CHAIN — two zones back to back
> At most two twists at once, fairly mixed.
>
> **Retro done right**
> Pixel look, NES-style chiptune sounds — synthesized entirely in code.
> The sky shifts towards night with every stage, medals reward your best
> runs, and breaking your record gets celebrated the moment it happens.
>
> **Honest & lean**
> No ads, no in-app purchases, no data collection, no internet needed.
> Just you, the dot, and your high score.

**Assets:**

- Feature-Grafik 1024×500: liegt fertig unter `store/feature-graphic.png`
  (Generator: `python3 store/generate_feature_graphic.py`)
- Screenshots 1080×1920 (9:16): je 4 Motive in Deutsch und Englisch
  unter `store/screenshots/de/` und `store/screenshots/en/`
  (Generator: `python3 store/generate_screenshots.py`) — Gameplay,
  Twists, Daily Challenge, Skins. Gern zusätzlich echte Geräte-
  Screenshots ergänzen.

## Automatischer Upload in den internen Test-Track (CI)

Der Workflow hat einen Job `play-internal`, der das gebaute AAB per
Play Developer API in den **internen Test-Track** lädt (als Entwurf).
Er läuft nur bei manuellem Start: **Actions → Build APK → Run
workflow** (auf `main`). Einmalige Einrichtung:

1. Play Console → **Einstellungen → API-Zugriff** → Google-Cloud-Projekt
   verknüpfen und dort ein **Service-Konto** anlegen (die Console
   verlinkt direkt in die Cloud Console).
2. In der Cloud Console für das Service-Konto einen **JSON-Key**
   erzeugen und herunterladen.
3. Zurück in der Play Console dem Service-Konto Zugriff auf DOTTIE.
   geben — die Berechtigung „Releases in Tests verwalten" reicht.
4. Den **kompletten JSON-Inhalt** als GitHub-Secret
   `PLAY_SERVICE_ACCOUNT_JSON` anlegen (Settings → Secrets and
   variables → Actions). Die Key-Datei danach lokal löschen, nie
   committen, nie in Chats einfügen.

Zwei Dinge kann die API nicht, die bleiben Handarbeit in der Console:
die App selbst anlegen (Name „Dottie.", Paket `de.robinrehbein.pointless`)
und den allerersten Upload prüfen/freigeben. Außerdem gilt: Der Job
verweigert den Start, solange der Keystore nicht rotiert ist
(`PUNKT_KEYSTORE_BASE64` fehlt), und jeder Upload braucht einen noch
nicht verwendeten `versionCode`.

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

## Werbung & Käufe aktivieren (AdMob + Play Billing)

Ab v2.16 sind ein **Rewarded-Spot zum Weiterspielen**, gelegentliche
**Interstitials** und der Kauf **„Werbung entfernen"** eingebaut — aber
**hart deaktiviert**, solange in `app/src/main/res/values/ads.xml` die
drei IDs leer sind. Ohne echte IDs wird das Ads-SDK nie initialisiert,
es gibt keinen Consent-Dialog, keine Ad-Requests und keinen
BillingClient; die UI sieht aus wie heute (kein WEITERSPIELEN-Knopf,
keine „WERBUNG ENTFERNEN"-Zeile). Aktivieren ist also eine bewusste
Entscheidung in genau zwei Dateien.

### 1. AdMob-Konto und App anlegen

1. [admob.google.com](https://admob.google.com) → Konto anlegen (kostenlos,
   AdSense-/AdMob-Zahlungsprofil mit Adresse und Steuerdaten hinterlegen).
2. **Apps → App hinzufügen** → Android → „Ja, im Play Store" und DOTTIE.
   auswählen (Paket `de.robinrehbein.pointless`). Ergebnis ist die
   **App-ID** im Format `ca-app-pub-…~…` (Tilde!).
3. **Anzeigenblöcke** anlegen: einen vom Typ **Rewarded** („Weiterspielen")
   und einen vom Typ **Interstitial** („Game-Over"). Beide liefern eine
   **Anzeigenblock-ID** im Format `ca-app-pub-…/…` (Schrägstrich!).
4. Frisch angelegte Blöcke liefern erfahrungsgemäß erst nach einigen
   Stunden Anzeigen — bis dahin bleibt es still, das ist kein Fehler.

### 2. IDs eintragen (zwei Dateien!)

1. `app/src/main/res/values/ads.xml`: `admob_app_id`,
   `admob_rewarded_id`, `admob_interstitial_id` ausfüllen. Erst wenn
   **alle drei** gefüllt sind, schaltet sich die Integration ein.
2. `app/src/main/AndroidManifest.xml`: die `meta-data`
   `com.google.android.gms.ads.APPLICATION_ID` trägt bis dahin Googles
   **Beispiel-App-ID** als Platzhalter — die **muss** durch die echte
   App-ID aus Schritt 1 ersetzt werden. Bleibt der Platzhalter stehen,
   verdient die App nichts und AdMob kann das Konto sperren.

Zum Ausprobieren gibt es Googles Test-IDs (sie stehen als Kommentar in
`ads.xml`): Sie zeigen echte Test-Anzeigen, dürfen aber **nie** in ein
Store-Release — Klicks auf echte Anzeigen im Eigentest ebenso wenig.

### 3. app-ads.txt — ehrlich betrachtet

AdMob empfiehlt eine `app-ads.txt` auf der Website, die im Play-Listing
als Entwickler-Website steht. Sie beweist Käufern, dass unser Inventar
echt ist. Der Haken bei uns:

- Unsere Store-Website ist **<https://robinrehbein.github.io/dottie./>** —
  das ist ein **Projekt-Pages-Pfad**, kein eigener Host.
- Die Datei muss aber im **Root der Domain** liegen, also unter
  `https://robinrehbein.github.io/app-ads.txt`. Ein
  `…/dottie./app-ads.txt` wird von den Crawlern ignoriert.
- Dafür bräuchte es ein **eigenes Repository namens
  `robinrehbein.github.io`** (User-Pages), in dessen Root die Datei
  liegt. Alternativ eine eigene Domain (z. B. `dottie.app`) als
  Entwickler-Website eintragen und die Datei dort ablegen.

**Ohne `app-ads.txt` läuft AdMob trotzdem.** Es fällt nur ein Teil der
Nachfrage weg (manche Käufer bieten ausschließlich auf verifiziertes
Inventar), der eTPM ist also etwas niedriger. Für den Start ist das
verkraftbar — die Datei lässt sich jederzeit nachreichen, ihr Inhalt
ist eine einzige Zeile, die AdMob unter **Apps → app-ads.txt** anzeigt.

### 4. In-App-Produkt „remove_ads" anlegen

Play Console → **Monetarisieren → Produkte → In-App-Produkte → Produkt
erstellen**:

| Feld | Wert |
|---|---|
| Produkt-ID | `remove_ads` (genau so, steht im Code) |
| Typ | Einmaliger Kauf, **nicht** verbrauchbar |
| Name | „Werbung entfernen" / „Remove ads" |
| Beschreibung | Entfernt dauerhaft alle Anzeigen. Weiterspielen nach dem Tod bleibt möglich. |
| Preis | Vorschlag **2,99 €** (Play rechnet die anderen Währungen um) |

Danach **aktivieren** — inaktive Produkte liefern im Kaufdialog nichts.
Der Kauf hängt am Google-Konto: Nach einer Neuinstallation stellt die
App ihn beim Start selbst wieder her (`queryPurchases`), ein
„Kauf wiederherstellen"-Knopf ist deshalb nicht nötig. Testen geht
kostenlos über **Einstellungen → Lizenztests** (Lizenz-Tester kaufen
zum Preis 0) — der Kauf funktioniert erst, wenn die App über einen
Play-Track installiert wurde, nicht per `adb install`.

### 5. Data-Safety und Datenschutz nachziehen

Mit aktiver Werbung stimmt „Es werden keine Daten erhoben" **nicht
mehr**. Vor dem nächsten Release anpassen:

- **Data-Safety-Formular** (Play Console → App-Inhalte): „Gerätekennungen
  oder andere IDs" → **Werbe-ID (AAID)** wird erhoben und **geteilt**,
  Zweck **Werbung/Marketing**, nicht verschlüsselt übertragbar
  verneinen/bejahen gemäß Googles Vorgaben; zusätzlich unter
  **Anzeigen** „Die App enthält Werbung" ankreuzen (das setzt auch das
  „Enthält Anzeigen"-Label im Listing).
- **Store-Texte**: Der Absatz „Ehrlich & schlank / Honest & lean" oben
  behauptet „keine Werbung, keine In-App-Käufe" — beides muss dann raus
  bzw. umformuliert werden (z. B. „Werbung lässt sich einmalig
  entfernen").
- **`docs/index.html`**: Textbaustein unten einsetzen.

### 6. Textbaustein für docs/index.html (erst beim Aktivieren einfügen)

Deutsch — als neuer Abschnitt vor „Kinder", außerdem den Satz unter
„Kurz gesagt" und den Abschnitt „Internetzugriff" entsprechend
entschärfen:

```html
  <h2>Werbung</h2>
  <p>DOTTIE. zeigt Werbung über <strong>Google AdMob</strong>: einen
  optionalen Video-Spot zum Weiterspielen nach einem Lauf sowie
  gelegentliche Vollbild-Anzeigen zwischen Läufen. Dabei verarbeitet
  Google die <strong>Werbe-ID (AAID)</strong> deines Geräts sowie
  technische Angaben (Gerätetyp, grobe Region, IP-Adresse), um
  Anzeigen auszuliefern und Betrug zu erkennen. Verantwortlich dafür
  ist Google Ireland Ltd.; Details:
  <a href="https://business.safety.google/privacy/">Google-Datenschutz</a>.
  Die Werbe-ID lässt sich in den Android-Einstellungen unter
  „Datenschutz → Werbung" zurücksetzen oder löschen.</p>
  <p>Vor der ersten Anzeige fragt die App über Googles
  <strong>User-Messaging-Platform (UMP)</strong> nach deiner
  Einwilligung; ohne Einwilligung werden keine (bzw. nur nicht
  personalisierte) Anzeigen geladen. Die Auswahl lässt sich jederzeit
  über denselben Dialog ändern.</p>
  <p>Über <strong>Google Play Billing</strong> kann die Werbung
  einmalig dauerhaft entfernt werden („Werbung entfernen"). Den
  Zahlungsvorgang wickelt ausschließlich Google Play ab — wir erhalten
  keine Zahlungsdaten, nur die Information, dass der Kauf besteht.</p>
```

Englisch — für den Footer-Absatz („English summary"):

```html
    <p><em>English summary:</em> DOTTIE. shows ads via
    <strong>Google AdMob</strong> (an optional rewarded video to continue
    a run, plus occasional interstitials between runs). Google processes
    your device's <strong>advertising ID (AAID)</strong> and technical
    data (device type, coarse region, IP address) to serve ads and
    prevent fraud. Before the first ad, the app asks for your consent via
    Google's <strong>User Messaging Platform</strong>; you can change
    that choice at any time. Ads can be removed permanently with a
    one-time purchase handled entirely by <strong>Google Play
    Billing</strong> — we never receive payment data. High scores, daily
    challenge progress, unlocked skins, and settings stay on your device.
    Contact: robin@join-noah.de</p>
```

### Wie sich das Spiel dann verhält

- **Weiterspielen (Rewarded)**: erscheint nur im Klassik-Modus, nur wenn
  ein Spot geladen ist und **einmal pro Lauf**. Score und Treffer
  bleiben, die Perfekt-Serie beginnt neu, und der Punkt startet eine
  knappe halbe Runde vor der frischen Zone — nach einem gesehenen Spot
  darf niemand sofort wieder sterben. Die Daily Challenge bleibt
  bewusst außen vor: gleicher Lauf für alle, keine gekauften Vorteile.
- **Interstitials**: frühestens ab dem **4. Tod einer Sitzung** und
  mindestens **90 Sekunden** nach dem letzten Spot (`InterstitialGate`,
  per Unit-Test abgesichert) — und nie in dem Game-Over, in dem gerade
  Weiterspielen angeboten wird.

## Wear-App im Play Store mitverteilen

Die CI baut neben dem Phone-AAB auch `wear-release.aab` (signiert mit
demselben Upload-Keystore, sobald die Secrets gesetzt sind — davor mit
dem Test-Keystore, der nie in den Store darf). Beide Bundles hängen an
den `apk-build-N`-Releases. Phone- und Wear-App teilen sich die
Paket-ID `de.robinrehbein.pointless` und damit den Store-Eintrag; Play
liefert automatisch das passende Bundle pro Gerätetyp aus.

Einrichtung in der Play Console (einmalig):

1. **Releases → Einrichtung → Erweiterte Einstellungen → Formfaktoren**
   → „Formfaktor hinzufügen" → **Wear OS** aktivieren.
2. Im Test-Release (intern oder geschlossen) zusätzlich zum Phone-AAB
   das `wear-release.aab` hochladen — die Console ordnet es am
   `<uses-feature android.hardware.type.watch>` automatisch Wear zu.
3. Fürs Listing verlangt Play mindestens einen **Wear-Screenshot**
   (rund dargestellt, min. 384×384 px, Format 1:1).
4. Installiert wird auf der Uhr über den **Play Store auf der Uhr**
   (gleiches Google-Konto wie der Tester); die App ist standalone
   (`com.google.android.wearable.standalone`), das Telefon ist egal.

Wear-Releases durchlaufen zusätzlich Googles Wear-OS-Qualitätsprüfung —
für interne Tests ist das egal, für Production muss der Prototyp
vorher auf echter Hardware getestet sein.

## Versionierung für Store-Uploads

Jeder Play-Upload braucht einen höheren `versionCode`
(`app/build.gradle.kts`). Aktuell: `versionCode 27` / `versionName
"2.16"`. Vor jedem Store-Upload beides anheben und committen.

Die Wear-App zählt in einem eigenen Bereich **ab 100001**
(`wear/build.gradle.kts`), damit sich die beiden Zähler nie in die
Quere kommen — Play verlangt eindeutige versionCodes über alle
Artefakte einer Paket-ID hinweg. Auch hier gilt: vor jedem Upload
anheben.
