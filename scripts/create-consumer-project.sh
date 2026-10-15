#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_DIR="$SCRIPT_DIR/../template-consumer-api"

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <new-consumer-directory> [--package-root <java.package>]" >&2
  exit 2
fi

DEST_DIR="$1"
shift
PACKAGE_ROOT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --package-root)
      if [[ $# -lt 2 || -z "$2" ]]; then
        echo "Option --package-root requires a Java package name" >&2
        exit 2
      fi
      PACKAGE_ROOT="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      echo "Usage: $0 <new-consumer-directory> [--package-root <java.package>]" >&2
      exit 2
      ;;
  esac
done

if [[ -n "$PACKAGE_ROOT" && ! "$PACKAGE_ROOT" =~ ^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$ ]]; then
  echo "Invalid Java package name: $PACKAGE_ROOT" >&2
  exit 2
fi

mkdir -p -- "$DEST_DIR"

if [[ -n "$(find "$DEST_DIR" -mindepth 1 -maxdepth 1 ! -name .git -print -quit)" ]]; then
  echo "Destination must be empty: $DEST_DIR" >&2
  exit 1
fi

# Copy dotfiles too, but leave local build output and any template Git metadata behind.
find "$TEMPLATE_DIR" -mindepth 1 -maxdepth 1 ! -name target ! -name .git \
  -exec cp -R {} "$DEST_DIR/" \;

if [[ -n "$PACKAGE_ROOT" ]]; then
  TEMPLATE_PACKAGE="vn.ots.qc.consumer.template"
  TEMPLATE_PACKAGE_DIR="$DEST_DIR/src/test/java/${TEMPLATE_PACKAGE//./\/}"
  PACKAGE_DIR="$DEST_DIR/src/test/java/${PACKAGE_ROOT//./\/}"

  if [[ ! -d "$TEMPLATE_PACKAGE_DIR" ]]; then
    echo "Template Java package not found: $TEMPLATE_PACKAGE_DIR" >&2
    exit 1
  fi

  find "$DEST_DIR/src/test/java" -type f -name '*.java' \
    -exec perl -pi -e "s/\Q$TEMPLATE_PACKAGE\E/$PACKAGE_ROOT/g" {} +

  mkdir -p -- "$(dirname "$PACKAGE_DIR")"
  if [[ -e "$PACKAGE_DIR" ]]; then
    echo "Target Java package already exists: $PACKAGE_DIR" >&2
    exit 1
  fi
  mv -- "$TEMPLATE_PACKAGE_DIR" "$PACKAGE_DIR"
  rmdir -p -- "$DEST_DIR/src/test/java/vn/ots/qc/consumer" 2>/dev/null || true
fi

echo "Consumer template copied to: $DEST_DIR"
