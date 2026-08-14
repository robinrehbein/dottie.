# DOTTIE. — Weg in den Play Store

Fahrplan und Anleitungen für die Veröffentlichung. Stand: v2.20.

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
- [x] AdMob-IDs eingetragen — **Werbung ist aktiv** (Abschnitt unten)
- [ ] In-App-Kauf „remove_ads" in der Play Console anlegen (Abschnitt unten)
- [ ] Data-Safety-Formular **mit** Werbung ausfüllen (Abschnitt unten) —
      „keine Daten erhoben" wäre jetzt falsch
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

- `https://dottie.robinrehbein.de/` → das Spiel (Web-PWA)
- `https://dottie.robinrehbein.de/datenschutz/` → die
  Datenschutzerklärung — **diese URL** im Play-Listing als
  Datenschutz-URL eintragen

Einmalige Voraussetzungen:

1. Repo → **Settings → Pages** → Source „GitHub Actions" (geht nur bei
   öffentlichem Repo oder mit GitHub Pro).
2. Beim DNS-Anbieter von `robinrehbein.de` einen **CNAME-Eintrag**
   anlegen: Name `dottie`, Ziel `robinrehbein.github.io.` (mit Punkt am
   Ende, falls der Anbieter das verlangt).
3. Repo → **Settings → Pages → Custom domain** auf
   `dottie.robinrehbein.de` setzen und **Enforce HTTPS** ankreuzen,
   sobald GitHub das Zertifikat ausgestellt hat (dauert nach dem
   DNS-Eintrag einige Minuten bis Stunden).

Die Datei `_site/CNAME` schreibt der Workflow selbst — sie muss im
veröffentlichten Verzeichnis liegen, nicht im Repo-Wurzelverzeichnis,
weil beim Deployment über Actions ausschließlich das Artefakt
ausgeliefert wird.

Die alte Adresse `robinrehbein.github.io/dottie./` leitet nach der
Umstellung automatisch auf die neue um.

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
> Keine Datensammelei, kein Konto, kein Internet zum Spielen nötig.
> Werbung finanziert die App: gelegentlich nach einem Spielende, und
> freiwillig, wenn du ein gesperrtes Aussehen einen Tag lang testen
> willst. Sie fasst weder deinen Lauf noch deinen Rekord an — und ein
> einmaliger Kauf entfernt sie dauerhaft.

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
> No data collection, no account, no internet needed to play. Ads keep
> the app alive: occasionally after a run ends, and voluntarily when you
> want to try a locked look for a day. They never touch your run or your
> record — and a single purchase removes them for good.

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

Ab v2.17 sind ein **Rewarded-Spot für den Skin-Tagespass**,
gelegentliche **Interstitials** und der Kauf **„Werbung entfernen"**
eingebaut — aber **hart deaktiviert**, solange in
`app/src/main/res/values/ads.xml` die drei IDs leer sind. Ohne echte IDs
wird das Ads-SDK nie initialisiert, es gibt keinen Consent-Dialog, keine
Ad-Requests und keinen BillingClient; die UI sieht aus wie heute (im
Skin-Overlay keine Spot-Zeilen, keine „WERBUNG ENTFERNEN"-Zeile).
Aktivieren ist also eine bewusste Entscheidung in genau zwei Dateien.

Bewusst **nicht** eingebaut: Weiterspielen nach dem Tod. Das Versprechen
des Spiels ist „Perfekt oder vorbei" — Werbung darf weder den Lauf noch
den Rekord anfassen. Der Spot verkauft deshalb nur Kosmetik auf Zeit.

### 1. AdMob-Konto und App anlegen

1. [admob.google.com](https://admob.google.com) → Konto anlegen (kostenlos,
   AdSense-/AdMob-Zahlungsprofil mit Adresse und Steuerdaten hinterlegen).
2. **Apps → App hinzufügen** → Android → „Ja, im Play Store" und DOTTIE.
   auswählen (Paket `de.robinrehbein.pointless`). Ergebnis ist die
   **App-ID** im Format `ca-app-pub-…~…` (Tilde!).
3. **Anzeigenblöcke** anlegen: einen vom Typ **Rewarded** („Skin-Tagespass")
   und einen vom Typ **Interstitial** („Game-Over"). Beide liefern eine
   **Anzeigenblock-ID** im Format `ca-app-pub-…/…` (Schrägstrich!).
4. Frisch angelegte Blöcke liefern erfahrungsgemäß erst nach einigen
   Stunden Anzeigen — bis dahin bleibt es still, das ist kein Fehler.

### 2. IDs eintragen

Alle IDs stehen an genau einer Stelle: `app/src/main/res/values/ads.xml`.
Das AndroidManifest verweist per `@string/admob_app_id` darauf, es gibt
also nichts doppelt zu pflegen.

**Stand:** Die App-ID ist eingetragen
(`ca-app-pub-1786159152036324~8923812059`). Es fehlen noch
`admob_rewarded_id` und `admob_interstitial_id` — bis beide gefüllt
sind, bleibt die App werbefrei, obwohl die App-ID schon steht.

Die App-ID darf nie wieder geleert werden: Das Ads-SDK startet über
einen eigenen ContentProvider und bricht ohne gültige ID beim App-Start
ab. Zum Abschalten reicht es, eine Anzeigenblock-ID zu leeren.

Zum Ausprobieren gibt es Googles Test-IDs (sie stehen als Kommentar in
`ads.xml`): Sie zeigen echte Test-Anzeigen, dürfen aber **nie** in ein
Store-Release — Klicks auf echte Anzeigen im Eigentest ebenso wenig.

### 3. app-ads.txt

Die Datei beweist Anzeigen-Käufern, dass unser Werbeplatz echt ist —
ohne sie fällt ein Teil der Nachfrage weg, weil manche Käufer
ausschließlich auf verifiziertes Inventar bieten.

Sie liegt als `web/app-ads.txt` im Repo und wird damit unter
`https://dottie.robinrehbein.de/app-ads.txt` ausgeliefert. Inhalt:

```
google.com, pub-1786159152036324, DIRECT, f08c47fec0942fa0
```

Damit Googles Crawler sie findet, muss im Play-Listing als
**Entwickler-Website** genau `https://dottie.robinrehbein.de/`
eingetragen sein: Gesucht wird immer im Wurzelverzeichnis dieses Hosts.
Mit dem früheren Projekt-Pages-Pfad ging das nicht — deshalb war die
Datei bis zur eigenen Domain nicht möglich.

Nach dem Eintragen prüft AdMob unter **Apps → app-ads.txt**, ob die
Datei erkannt wurde; das dauert bis zu ein paar Tage.

### 4. In-App-Produkt „remove_ads" anlegen

Play Console → **Monetarisieren → Produkte → In-App-Produkte → Produkt
erstellen**:

| Feld | Wert |
|---|---|
| Produkt-ID | `remove_ads` (genau so, steht im Code) |
| Typ | Einmaliger Kauf, **nicht** verbrauchbar |
| Name | „Werbung entfernen" / „Remove ads" |
| Beschreibung | Entfernt dauerhaft alle Anzeigen. Skins werden weiter durch Spielen freigeschaltet. |
| Preis | **1,99 €** (Play rechnet die anderen Währungen um) |

Danach **aktivieren** — inaktive Produkte liefern im Kaufdialog nichts.

Warum 1,99 € und nicht 0,99 €: Bei den meisten Spielen ist „Werbung
entfernen" ein Genervt-Kauf. Hier stört die Werbung kaum — frühestens ab
dem sechsten Tod, dann drei Minuten Ruhe, in der Daily gar nicht. Wer
trotzdem kauft, tut das eher aus Zuneigung zum Spiel als aus Not, und
für den ist der Unterschied zwischen einem und zwei Euro belanglos. Nach
Mehrwertsteuer und Googles 15 % bleiben 1,42 € statt 0,71 € — also
doppelt so viel, ohne dass nennenswert weniger Leute kaufen. Weiter nach
oben (2,99 €) wäre unangemessen: So aufdringlich ist die Werbung nicht,
und das Store-Versprechen „ehrlich und schlank" soll eins bleiben.

Der Preis steht **nirgends im Code**: Die App zeigt den Wert an, den
Google für das Land der Spielerin ausliefert (`formattedPrice`), samt
Währung und Steuersatz. Eine feste Zeichenkette stünde in der Hälfte der
Welt falsch da. Solange Google kein kaufbares Produkt liefert — Produkt
nicht angelegt, nicht aktiviert, App nicht über Play installiert oder
kein Play-Dienst vorhanden — **erscheint die Kauf-Zeile gar nicht erst**,
statt als toter Knopf dazustehen.
Der Kauf hängt am Google-Konto: Nach einer Neuinstallation stellt die
App ihn beim Start selbst wieder her (`queryPurchases`), ein
„Kauf wiederherstellen"-Knopf ist deshalb nicht nötig. Testen geht
kostenlos über **Einstellungen → Lizenztests** (Lizenz-Tester kaufen
zum Preis 0) — der Kauf funktioniert erst, wenn die App über einen
Play-Track installiert wurde, nicht per `adb install`.

### 4b. Versteckte Diagnose-Zeile beim Gerätetest

Ein **langer Druck auf den Titel „DOTTIE."** im Startbildschirm blendet
den Klartext-Zustand von Werbung und Kauf ein; nochmal drücken blendet
ihn wieder aus. Nötig, weil von außen alle Fehlerbilder identisch
aussehen — nämlich nach gar nichts:

| Anzeige | Bedeutung |
|---|---|
| `WERBUNG: aus — keine IDs` | `ads.xml` ist leer |
| `WERBUNG: keine Einwilligung — SDK nicht gestartet` | In AdMob fehlt eine **veröffentlichte** DSGVO-Mitteilung (Datenschutz & Mitteilungen → Europäische Verordnungen). Häufigster Fall, und der Grund, warum dann auch das Anzeigenprüftool nicht aufs Schütteln reagiert. |
| `WERBUNG: Spot: … (Code 3)` | „No fill" — alles richtig eingebaut, Google hat nur keine Anzeige. Bei frischen Anzeigenblöcken stundenlang normal. |
| `KAUF: keine Play-Verbindung (Code 3)` | App nicht über Play installiert (seitlich installierte APK) |
| `KAUF: Produkt nicht gefunden (Code …)` | Produkt fehlt, ist inaktiv oder wurde gerade erst angelegt — die Abfrage findet es erst nach einigen Stunden |
| `KAUF: kaufbar für 1,99 €` | alles in Ordnung |

Die Zeile nennt außerdem Versionsname und -code, damit beim Test nie
unklar ist, welcher Build gerade läuft. Normale Spieler finden sie nicht:
Niemand drückt lange auf eine Überschrift.

### 4c. In-App-Produkt „patron_pack" anlegen (Gönner-Paket, ab v2.20)

Dasselbe Formular wie bei `remove_ads`, zweites Produkt:

| Feld | Wert |
|---|---|
| Produkt-ID | `patron_pack` (genau so, steht im Code) |
| Typ | Einmaliger Kauf, **nicht** verbrauchbar |
| Name | „Gönner-Paket" / „Patron pack" |
| Beschreibung | Drei exklusive Punkt-Skins (Diamant, Phönix, Onyx) und dauerhaft keine Werbung. Alle anderen Skins bleiben durch Spielen freischaltbar. |
| Preis | **4,99 €** |

Danach **aktivieren** — sonst liefert die Abfrage nichts, und die Zeile
im SKINS-Overlay erscheint gar nicht erst.

**Warum 4,99 €:** Das Paket ist kein Nutzen-Kauf, sondern ein
Zuneigungs-Kauf — man bekommt drei Skins, die niemand erspielen kann,
und nimmt die Werbung gleich mit. Es muss deutlich über `remove_ads`
(1,99 €) liegen, sonst frisst es das kleinere Produkt; und es darf nicht
so weit nach oben, dass es nach Abzocke aussieht. 4,99 € ist die
klassische Unterstützer-Stufe: Nach Steuer und Googles 15 % bleiben rund
3,56 €.

**Der eine unangenehme Fall — bitte bewusst entscheiden:** Wer schon
`remove_ads` gekauft hat und danach das Gönner-Paket kauft, bezahlt die
Werbefreiheit ein zweites Mit. Google kennt für Einmalprodukte keinen
Upgrade-Pfad, das lässt sich im Store nicht lösen. Drei Wege:

1. **So lassen** und in der Beschreibung klar sagen, dass Werbefreiheit
   enthalten ist. Wer sie schon hat, zahlt effektiv 4,99 € für drei
   Skins. Ehrlich, aber ärgerlich für die treuesten Käufer.
2. **Werbefreiheit aus dem Paket nehmen** und es als reines Skin-Paket
   für 3,99 € verkaufen. Sauber trennbar, aber ein „Gönner-Paket", das
   die Werbung stehen lässt, wirkt geizig.
3. **Empfehlung:** Paket so lassen, aber in der App die Zeile für
   Besitzer von `remove_ads` anders beschriften („GOENNER-PAKET — DREI
   SKINS" statt „… UND WERBEFREI"), damit niemand doppelt für dasselbe
   Versprechen zahlt, ohne es zu merken. Das ist eine App-Änderung, kein
   Store-Eintrag — sie steht auf der Liste in `docs/TODO.md`.

**Voraussetzung, die leicht übersehen wird:** Der BillingClient wird
insgesamt erst gestartet, wenn in `res/values/ads.xml` echte AdMob-IDs
stehen (`AdsManager.configured`). Ohne diese IDs gibt es also auch kein
Gönner-Paket — die drei Skins bleiben unerreichbar, ohne dass eine
Fehlermeldung erscheint. Wer das Paket unabhängig von Werbung verkaufen
will, muss die Kopplung auftrennen; auch das steht auf der Liste.

Getestet wird wie bei `remove_ads` über **Einstellungen → Lizenztests**
(Kaufpreis 0) und eine über einen Play-Track installierte App. Die
versteckte Diagnose-Zeile (Abschnitt 4b) nennt beide Produkte getrennt.

**Andere Geräte, andere Plattformen:** Der Kauf hängt am Google-Konto,
nicht am Gerät. Auf einem Tablet mit demselben Konto stellt ihn dieselbe
App beim Start selbst wieder her; auf der Uhr gilt dasselbe, sobald die
Wear-App Play Billing selbst abfragt. **Nicht** übertragbar ist er auf
iOS: Apple und Google führen getrennte Kassen, ein Kauf im Play Store
schaltet in der iOS-App nichts frei. Wer beides will, kauft zweimal —
das ist keine Entscheidung dieses Projekts, sondern die Bauart der
beiden Stores.

### 5. Data-Safety und Anzeigen-Label in der Play Console

Die Datenschutzerklärung (`docs/index.html`) und die Store-Texte oben
sind bereits auf aktive Werbung umgeschrieben. Offen bleibt der Teil,
der nur in der Play Console geht:

- **Data-Safety-Formular** (Play Console → App-Inhalte → Datensicherheit):
  „Gerätekennungen oder andere IDs" → **Werbe-ID (AAID)** wird erhoben
  **und geteilt**, Zweck **Werbung/Marketing**. Als Verarbeiter tritt
  Google auf. „Es werden keine Daten erhoben" wäre jetzt falsch.
- **Anzeigen-Label**: unter App-Inhalte → **Anzeigen** „Ja, die App
  enthält Werbung" ankreuzen. Das setzt das „Enthält Anzeigen"-Label im
  Listing.
- **IARC-Fragebogen**: beim Ausfüllen angeben, dass die App Werbung
  enthält und digitale Käufe anbietet — sonst weicht das Rating später
  vom tatsächlichen Inhalt ab.

### Wie sich das Spiel dann verhält

- **Skin-Tagespass (Rewarded)**: Im SKINS-Overlay werden gesperrte
  Skins antippbar, sobald ein Spot geladen ist. Nach dem gesehenen Spot
  ist genau **dieser eine** Skin bis Mitternacht spielbar und direkt
  ausgewählt; ein Spot für einen anderen Skin ersetzt den Pass. Der Lauf
  bleibt unberührt — der Tod ist endgültig, Rekorde entstehen weiter nur
  durch Spielen. Auch die dauerhafte Freischaltung bleibt exklusiv an
  den Medaillen-Zielen hängen: Ein Pass zählt nicht als „freigeschaltet"
  und löst die Freischalt-Feier nicht aus. Beim Tageswechsel fällt eine
  nur geliehene Auswahl automatisch auf KLASSIK zurück.
- **Interstitials**: frühestens ab dem **6. Tod einer Sitzung** und
  mindestens **180 Sekunden** nach dem letzten Spot (`InterstitialGate`,
  per Unit-Test abgesichert) — nur im Klassik-Modus, die Daily Challenge
  bleibt werbefrei. Die Werte sind absichtlich zurückhaltend: Das Spiel
  lebt vom sofortigen nächsten Versuch.

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
(`app/build.gradle.kts`). Aktuell: `versionCode 29` / `versionName
"2.18"`. Vor jedem Store-Upload beides anheben und committen.

Achtung: `versionCode 28` wurde bereits zweimal gebaut — einmal mit
Skins und Himmel-Umlauf (Build 101), einmal zusätzlich mit dem
Tagespass (Build 108). Zwei Zweige hatten dieselbe Nummer vergeben.
Wer ein AAB weitergibt, sollte deshalb immer vom jeweils neuesten
Build ausgehen und den versionCode vorher anheben.

Die Wear-App zählt in einem eigenen Bereich **ab 100001**
(`wear/build.gradle.kts`), damit sich die beiden Zähler nie in die
Quere kommen — Play verlangt eindeutige versionCodes über alle
Artefakte einer Paket-ID hinweg. Auch hier gilt: vor jedem Upload
anheben.
