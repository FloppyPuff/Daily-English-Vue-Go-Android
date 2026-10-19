#!/usr/bin/env bash
# Build the deployable release tarball: the Vue bundle plus a linux/amd64 Go
# binary, laid out exactly the way deploy/remote-deploy.sh expects.
#
# Usage:
#   deploy/build-release.sh [--out DIR] [--revision REV] [--skip-web] [--skip-backend]
#
# Requires Node.js 22+, Go 1.27+ and tar. Works on Linux, macOS and Git Bash.
# Paths are resolved relative to this file, so it can be run from anywhere.

set -euo pipefail

here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
root=$(cd -- "$here/.." && pwd)

usage() {
  echo "usage: build-release.sh [--out DIR] [--revision REV] [--skip-web] [--skip-backend]"
}

out="$root/dist"
revision=${REVISION:-$(git -C "$root" rev-parse --short HEAD 2>/dev/null || echo dev)}
build_web=1
build_backend=1

while [ $# -gt 0 ]; do
  case "$1" in
    --out) out=${2:?--out needs a directory}; shift 2 ;;
    --revision) revision=${2:?--revision needs a value}; shift 2 ;;
    --skip-web) build_web=0; shift ;;
    --skip-backend) build_backend=0; shift ;;
    -h | --help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

stage=$(mktemp -d "${TMPDIR:-/tmp}/daily-english-release.XXXXXX")
trap 'rm -rf "$stage"' EXIT

echo "== revision $revision"
printf '%s\n' "$revision" >"$stage/REVISION"

if [ "$build_web" = 1 ]; then
  echo "== building frontend"
  (
    cd "$root/frontend"
    npm ci
    npm run build
  )
  [ -f "$root/frontend/dist/index.html" ] || {
    echo "frontend build produced no dist/index.html" >&2
    exit 1
  }
  mkdir -p "$stage/web"
  cp -a "$root/frontend/dist/." "$stage/web/"
fi

if [ "$build_backend" = 1 ]; then
  echo "== building backend (linux/amd64)"
  mkdir -p "$stage/bin"
  (
    cd "$root/backend"
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
      go build -trimpath -ldflags="-s -w" -o "$stage/bin/daily-english-go" .
  )
fi

# The unit ships as a template only. deploy/remote-deploy.sh ignores it unless
# it is started with UPDATE_UNIT=1, because the live unit is hand-maintained.
mkdir -p "$stage/systemd"
cp "$root/deploy/daily-english-go.service" "$stage/systemd/"

mkdir -p "$out"
tarball="$out/daily-english-$revision.tar.gz"
rm -f "$tarball"
tar -czf "$tarball" -C "$stage" .

echo "== wrote $tarball"
ls -lh "$tarball" | awk '{print "   size:", $5}'
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$tarball" | awk '{print "   sha256:", $1}'
elif command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$tarball" | awk '{print "   sha256:", $1}'
fi
echo "== deploy with:"
echo "   scp \"$tarball\" user@host:/tmp/ && \\"
echo "   ssh user@host 'sudo bash -s -- /tmp/$(basename "$tarball")' < deploy/remote-deploy.sh"
