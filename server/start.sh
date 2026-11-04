#!/usr/bin/env bash
# Skybound SMP server start (Linux hosts). Installs NeoForge on first run.
set -e
cd "$(dirname "$0")"
NEO=$(cat neoforge-version.txt)

if [ ! -f "libraries/net/neoforged/neoforge/$NEO/unix_args.txt" ]; then
  echo "Installing NeoForge $NEO..."
  curl -fsSL -o neoforge-installer.jar \
    "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar"
  java -jar neoforge-installer.jar --installServer > neoforge-install.log 2>&1
  rm -f neoforge-installer.jar
fi

exec java @jvm-args.txt @"libraries/net/neoforged/neoforge/$NEO/unix_args.txt" nogui "$@"
