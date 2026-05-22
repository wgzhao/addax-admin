# scripts/release.sh
#!/usr/bin/env bash
set -euo pipefail

VERSION=${1:?Usage: release.sh <version>}

# 校验格式
if ! [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Invalid version: $VERSION (expected X.Y.Z)"
  exit 1
fi

# 同步更新两个版本文件
mvn -q versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
npm pkg set version="$VERSION"

# 单次提交
git add pom.xml package.json
git commit -m "chore: release v$VERSION"
git tag "v$VERSION"

echo "✅ Released v$VERSION — remember to: git push && git push --tags"
