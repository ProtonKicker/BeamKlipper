#!/usr/bin/env bash
set -euo pipefail

FONT_NAME="Roboto-Regular.ttf"
OUT="/home/tovi/Documents/GitHub/BeamKlipper/app/src/main/assets/${FONT_NAME}"
if [[ -f "${OUT}" ]]; then
  echo "already have ${FONT_NAME}" >&2
  exit 0
fi

URL="https://github.com/google/fonts/raw/main/apache/roboto/static/Roboto-Regular.ttf"
if command -v curl >/dev/null 2>&1; then
  curl -L --fail --retry 3 --retry-delay 1 -o "${OUT}" "${URL}"
elif command -v wget >/dev/null 2>&1; then
  wget -O "${OUT}" "${URL}"
else
  echo "need curl or wget" >&2
  exit 1
fi
ls -la "${OUT}"
