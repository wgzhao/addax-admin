#!/usr/bin/env bash
# Build addax-admin-<version>-host.tar.gz from an already built jar and frontend dist.
# Why: hosts without a build toolchain need a versioned, self-describing archive, and the
# archive must be assembled from the exact artifacts of one tag rather than rebuilt ad hoc.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-}"
OUT_DIR="${2:-$REPO_ROOT/dist}"
TEMPLATES="$REPO_ROOT/scripts/bundle/host"

die() {
  echo "error: $*" >&2
  exit 1
}

[[ -n $VERSION ]] || die "usage: $0 <version> [out-dir]"
# A suffix is allowed so a pre-release or local SNAPSHOT bundle can be built and tested with
# the same script; published bundles use a plain X.Y.Z tag.
[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$ ]] ||
  die "invalid version '$VERSION' (expected X.Y.Z or X.Y.Z-SUFFIX)"

JAR="$REPO_ROOT/backend/target/addax-admin-$VERSION.jar"
DIST="$REPO_ROOT/frontend/dist"
[[ -f $JAR ]] || die "missing $JAR; run 'mvn clean package -DskipTests' in backend/"
[[ -f $DIST/index.html ]] || die "missing $DIST/index.html; run 'yarn build-only' in frontend/"

# Why: VITE_APP_VERSION is baked from frontend/package.json, so a mismatch would ship a UI
# that reports the wrong version even though the jar is correct.
pkg_version="$(sed -n 's/^ *"version": *"\([^"]*\)".*/\1/p' "$REPO_ROOT/frontend/package.json" | head -n 1)"
[[ $pkg_version == "$VERSION" ]] || die "frontend/package.json version '$pkg_version' != '$VERSION'"
pom_version="$(sed -n 's|.*<version>\([^<]*\)</version>.*|\1|p' "$REPO_ROOT/pom.xml" | head -n 1)"
[[ $pom_version == "$VERSION" ]] || die "pom.xml version '$pom_version' != '$VERSION'"

while IFS= read -r stale; do
  [[ -n $stale ]] || continue
  echo "warning: ignoring stale jar $(basename "$stale")" >&2
done < <(find "$REPO_ROOT/backend/target" -maxdepth 1 -name 'addax-admin-*.jar' \
  ! -name "addax-admin-$VERSION.jar")

STAGE_ROOT="$(mktemp -d)"
trap 'rm -rf "$STAGE_ROOT"' EXIT
PKG="$STAGE_ROOT/addax-admin-$VERSION"

mkdir -p "$PKG"/backend/bin "$PKG"/backend/lib "$PKG"/backend/config \
  "$PKG"/backend/drivers "$PKG"/backend/job "$PKG"/backend/logs \
  "$PKG"/frontend "$PKG"/deploy/sql

install -m 644 "$JAR" "$PKG/backend/lib/"
install -m 755 "$REPO_ROOT/scripts/service.sh" "$PKG/backend/bin/service.sh"
install -m 644 "$REPO_ROOT/backend/src/main/resources/application.properties" \
  "$PKG/backend/config/application.properties"
install -m 644 "$REPO_ROOT/backend/config/env.template.sh" "$PKG/backend/config/env.sh"
install -m 644 "$REPO_ROOT/backend/drivers/README.md" "$PKG/backend/drivers/README.md"
: >"$PKG/backend/job/.keep"
: >"$PKG/backend/logs/.keep"

cp -a "$DIST/." "$PKG/frontend/"

install -m 644 "$TEMPLATES/README.md" "$PKG/deploy/README.md"
install -m 755 "$TEMPLATES/install.sh" "$PKG/deploy/install.sh"
install -m 644 "$TEMPLATES/nginx.conf.example" "$PKG/deploy/nginx.conf.example"
install -m 644 "$TEMPLATES/addax-admin.service" "$PKG/deploy/addax-admin.service"
install -m 644 "$REPO_ROOT/scripts/schema.sql" "$PKG/deploy/sql/schema.sql"
install -m 644 "$REPO_ROOT/scripts/data.sql" "$PKG/deploy/sql/data.sql"
install -m 644 "$REPO_ROOT/LICENSE" "$PKG/LICENSE"

{
  echo "name=addax-admin"
  echo "version=$VERSION"
  echo "git_commit=$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  echo "build_time=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "java=21"
} >"$PKG/BUILD-INFO"

# Checksums are relative to the package root so users can run sha256sum -c after extraction.
(
  cd "$PKG"
  sha256sum "backend/lib/addax-admin-$VERSION.jar" frontend/index.html >deploy/CHECKSUMS.sha256
)

mkdir -p "$OUT_DIR"
TARBALL="$OUT_DIR/addax-admin-$VERSION-host.tar.gz"
tar -C "$STAGE_ROOT" -czf "$TARBALL" "addax-admin-$VERSION"
(
  cd "$OUT_DIR"
  sha256sum "$(basename "$TARBALL")" >"$(basename "$TARBALL").sha256"
)

echo "built $TARBALL ($(du -h "$TARBALL" | cut -f 1))"
echo "sha256 $(cut -d ' ' -f 1 "$TARBALL.sha256")"
