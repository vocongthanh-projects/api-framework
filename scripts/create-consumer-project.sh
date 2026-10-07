#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_DIR="$SCRIPT_DIR/../template-consumer-api"

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <new-consumer-directory>" >&2
  exit 2
fi

DEST_DIR="$1"
mkdir -p -- "$DEST_DIR"

if [[ -n "$(find "$DEST_DIR" -mindepth 1 -maxdepth 1 ! -name .git -print -quit)" ]]; then
  echo "Destination must be empty: $DEST_DIR" >&2
  exit 1
fi

# Copy dotfiles too, but leave local build output and any template Git metadata behind.
find "$TEMPLATE_DIR" -mindepth 1 -maxdepth 1 ! -name target ! -name .git \
  -exec cp -R {} "$DEST_DIR/" \;
echo "Consumer template copied to: $DEST_DIR"
