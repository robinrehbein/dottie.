#!/usr/bin/env bash
# Richtet das Android SDK ein, falls es fehlt.
#
# Hintergrund: In frischen Cloud-Sitzungen (Claude Code on the web, CI-nahe
# Container) ist kein SDK vorhanden. Ohne SDK schlaegt schon das Konfigurieren
# des Gradle-Projekts fehl, und zwar mit einer Meldung ueber ein fehlendes
# Plugin — man sucht dann am falschen Ende.
#
# Das Skript ist absichtlich idempotent und still: Ist alles da, kostet es
# einen Wimpernschlag. Es schreibt KEINE local.properties (die ist ignoriert
# und wuerde auf anderen Rechnern stoeren) — Gradle findet das SDK ueber
# ANDROID_HOME.
set -euo pipefail

cd "$(dirname "$0")/.."

# Wer schon ein SDK hat, wird in Ruhe gelassen — auf einem Entwickler-Mac
# liegt es unter ~/Library/Android/sdk und wird ueber local.properties oder
# Android Studio gefunden. Ein zweites SDK daneben waere nur Ballast.
if [ -f local.properties ] && grep -q '^sdk.dir=' local.properties; then
  echo "SDK-Pfad steht in local.properties — nichts zu tun."
  exit 0
fi
for existing in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk"; do
  if [ -n "$existing" ] && [ -d "$existing/platforms" ]; then
    echo "Android SDK gefunden: $existing"
    exit 0
  fi
done

SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
PLATFORM="platforms;android-36"
BUILD_TOOLS="build-tools;36.0.0"
TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

have_package() { [ -d "$SDK_DIR/${1//;//}" ]; }

write_local_properties() {
  # Gradle findet das SDK hierueber ohne Umgebungsvariable. Die Datei ist
  # gitignored, der Pfad wandert also nicht auf fremde Rechner.
  if ! { [ -f local.properties ] && grep -q '^sdk.dir=' local.properties; }; then
    echo "sdk.dir=$SDK_DIR" >> local.properties
  fi
}

if have_package "$PLATFORM" && have_package "$BUILD_TOOLS"; then
  write_local_properties
  echo "Android SDK vollstaendig: $SDK_DIR"
  exit 0
fi

echo "Android SDK unvollstaendig — richte es unter $SDK_DIR ein ..."
mkdir -p "$SDK_DIR/cmdline-tools"

if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp="$(mktemp -d)"
  curl -fsSL "$TOOLS_URL" -o "$tmp/tools.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$tmp"
fi

export ANDROID_HOME="$SDK_DIR"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
"$SDKMANAGER" --install "platform-tools" "$PLATFORM" "$BUILD_TOOLS" > /dev/null

write_local_properties
echo "Android SDK bereit: $SDK_DIR"
