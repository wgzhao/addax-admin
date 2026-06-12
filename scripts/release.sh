# scripts/release.sh
#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?Usage: release.sh <version>}

# Validate version format (why: ensure the version follows semantic X.Y.Z)
if ! [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Invalid version: $VERSION (expected X.Y.Z)"
  exit 1
fi

# Sync update version files (why: keep backend and frontend versions consistent)
mvn -q versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
npm pkg set version="$VERSION"
npm pkg set version="$VERSION" --prefix frontend

# Single commit with repository-scoped conventional commit (why: satisfy commitlint and repo conventions)
git add pom.xml package.json frontend/package.json
git commit -m "chore(repo): release v$VERSION"
git tag "v$VERSION"

echo "✅ Released v$VERSION — remember to: git push && git push --tags"
