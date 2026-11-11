#!/usr/bin/env bash
# Builds Motions Client on Replit. Output: build/libs/motions-client-1.0.0.jar
set -e
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo ">> Generating Gradle wrapper (9.2.1)..."
  HERE="$(pwd)"
  TMP="$(mktemp -d)"
  ( cd "$TMP" && touch settings.gradle && gradle wrapper --gradle-version 9.2.1 --no-daemon )
  cp -r "$TMP/gradlew" "$TMP/gradlew.bat" "$TMP/gradle" "$HERE/"
fi
chmod +x gradlew
./gradlew build --no-daemon
echo
echo ">> DONE. Download: build/libs/motions-client-1.0.0.jar"
ls -la build/libs/
