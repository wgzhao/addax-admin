#!/bin/sh
# Install or upgrade the addax-admin host bundle.
# Why: first install and upgrades share one idempotent path so that a re-run replaces the jar
# and frontend while never touching config, job files or JDBC drivers.
set -eu

BUNDLE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_HOME=/opt/app/addax-admin
WEB_ROOT=/var/www/html/addax-admin-ui
APP_USER=addax
WITH_SYSTEMD=1
WITH_FRONTEND=1

usage() {
  cat <<'EOF'
Usage: sh deploy/install.sh [options]

  --app-home DIR    application directory (default: /opt/app/addax-admin)
  --web-root DIR    nginx static root (default: /var/www/html/addax-admin-ui)
  --user NAME       OS account that runs the service and owns the files (default: addax).
                    Existing accounts are reused; missing accounts are created (with a warning).
                    Addax jobs inherit this account, so pick one that can write your targets
                    (for HDFS/Hive this is often the hive account).
  --no-systemd      do not install the systemd unit
  --no-frontend     do not deploy the frontend
  -h, --help        show this help
EOF
}

die() {
  echo "error: $*" >&2
  exit 1
}

say() {
  printf '%s\n' "$*"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --app-home)
      [ $# -ge 2 ] || die "missing value for --app-home"
      APP_HOME=$2
      shift 2
      ;;
    --web-root)
      [ $# -ge 2 ] || die "missing value for --web-root"
      WEB_ROOT=$2
      shift 2
      ;;
    --user)
      [ $# -ge 2 ] || die "missing value for --user"
      APP_USER=$2
      shift 2
      ;;
    --no-systemd)
      WITH_SYSTEMD=0
      shift
      ;;
    --no-frontend)
      WITH_FRONTEND=0
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      die "unknown option: $1"
      ;;
  esac
done

[ -n "$APP_USER" ] || die "--user requires a non-empty account name"

# Why: addax-admin launches addax.sh for every collection, so the service account decides which
# filesystem permissions the jobs inherit. HDFS/Hive targets frequently only accept the hive
# account, and a silent default of 'addax' then fails at write time. Make the choice explicit.
APP_USER_STATE=unknown
if [ "$(id -u)" -ne 0 ]; then
  [ "$APP_USER" = "$(id -un)" ] ||
    say "warning: --user $APP_USER ignored: install.sh is not running as root, so the account cannot be created or switched"
  APP_USER="$(id -un)"
  APP_USER_STATE="current user (not root)"
fi

VERSION="$(sed -n 's/^version=//p' "$BUNDLE_DIR/BUILD-INFO" 2>/dev/null | head -n 1)"
[ -n "$VERSION" ] || die "BUILD-INFO not found in $BUNDLE_DIR"

JAR_SRC="$(ls "$BUNDLE_DIR"/backend/lib/addax-admin-*.jar 2>/dev/null | head -n 1)"
[ -n "$JAR_SRC" ] || die "no addax-admin-*.jar under $BUNDLE_DIR/backend/lib"
JAR_NAME="$(basename "$JAR_SRC")"

java_major_of() {
  { "$1" -version 2>&1 || true; } |
    sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1
}

# Why: pick the first candidate that is actually Java 21+. A host can have java 17 on PATH and
# a 21 runtime installed in /usr/lib/jvm; stopping at the first existing binary fails such hosts.
find_java() {
  for candidate in \
    "${JAVA_HOME:+$JAVA_HOME/bin/java}" \
    "$(command -v java 2>/dev/null || true)" \
    /usr/lib/jvm/*21*/bin/java \
    /usr/java/*21*/bin/java; do
    [ -n "$candidate" ] || continue
    [ -x "$candidate" ] || continue
    major="$(java_major_of "$candidate")"
    if [ -n "$major" ] && [ "$major" -ge 21 ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

JAVA_BIN="$(find_java)" ||
  die "no Java 21+ runtime found (PATH has $(command -v java 2>/dev/null || echo none)); install Java 21 or set JAVA_HOME"
JAVA_MAJOR="$(java_major_of "$JAVA_BIN")"
[ -n "$JAVA_MAJOR" ] || die "cannot determine the version of $JAVA_BIN"
JAVA_HOME_DETECTED="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"

command -v nc >/dev/null 2>&1 ||
  say "warning: 'nc' not found; bin/service.sh uses it for the startup readiness check"

# Resolve the account before touching APP_HOME so a rejected choice leaves no half-installed tree.
if [ "$(id -u)" -eq 0 ]; then
  if id -u "$APP_USER" >/dev/null 2>&1; then
    APP_USER_STATE=existing
    say "service user : $APP_USER (existing; override with --user NAME)"
    say "               uid $(id -u "$APP_USER"), primary group $(id -gn "$APP_USER")"
  else
    APP_USER_STATE=created
    say ""
    say "WARNING: OS user $APP_USER does not exist; creating it as a system account."
    say "         addax-admin and every Addax job it launches will run as $APP_USER."
    say "         Pick an account that can write your collection targets - for HDFS/Hive"
    say "         this is usually the hive account or one that owns the HDFS directories."
    say ""
    useradd -r -s /sbin/nologin "$APP_USER" 2>/dev/null ||
      die "failed to create user $APP_USER"
    say "service user : $APP_USER (created as a system account)"
  fi
  if [ "$(id -u "$APP_USER")" -eq 0 ]; then
    die "refusing to run addax-admin as root; choose a non-root account with --user NAME"
  fi
  APP_GROUP="$(id -gn "$APP_USER")"
else
  say "service user : $APP_USER ($APP_USER_STATE; systemd unit is not installed)"
fi

mkdir -p "$APP_HOME/bin" "$APP_HOME/lib" "$APP_HOME/config" \
  "$APP_HOME/drivers" "$APP_HOME/job" "$APP_HOME/logs"

for old in "$APP_HOME"/lib/addax-admin-*.jar; do
  [ -e "$old" ] || continue
  mv "$old" "$old.before-$(date +%Y%m%d%H%M%S)"
done
install -m 644 "$JAR_SRC" "$APP_HOME/lib/$JAR_NAME"
install -m 755 "$BUNDLE_DIR/backend/bin/service.sh" "$APP_HOME/bin/service.sh"

# Why: config drift is worse than a missing default; the new template is written next to the
# live file and the administrator decides whether to merge it.
copy_config() {
  src=$1
  dst=$2
  if [ -e "$dst" ]; then
    if ! cmp -s "$src" "$dst"; then
      cp "$src" "$dst.new"
      say "kept $dst (new template written to $dst.new)"
    fi
  else
    cp "$src" "$dst"
  fi
}

copy_config "$BUNDLE_DIR/backend/config/application.properties" "$APP_HOME/config/application.properties"

gen_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  else
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n'
  fi
}

if [ ! -e "$APP_HOME/config/env.sh" ]; then
  cp "$BUNDLE_DIR/backend/config/env.sh" "$APP_HOME/config/env.sh"
  {
    echo ''
    echo '# Generated by deploy/install.sh on first install.'
    echo '# Changing JWT_SECRET signs everyone out; changing ALERT_SECRET_KEY makes stored alert channel secrets unreadable.'
    echo "export JAVA_HOME=\"$JAVA_HOME_DETECTED\""
    echo 'export PATH="$JAVA_HOME/bin:$PATH"'
    echo "export JWT_SECRET='$(gen_secret)'"
    echo "export ALERT_SECRET_KEY='$(gen_secret)'"
  } >>"$APP_HOME/config/env.sh"
  if grep -q '^export DB_PASSWORD="password"' "$APP_HOME/config/env.sh"; then
    say "warning: $APP_HOME/config/env.sh still has the example DB password; edit it before starting"
  fi
fi

[ -e "$APP_HOME/drivers/README.md" ] ||
  cp "$BUNDLE_DIR/backend/drivers/README.md" "$APP_HOME/drivers/README.md"

if [ "$WITH_FRONTEND" -eq 1 ]; then
  command -v rsync >/dev/null 2>&1 ||
    die "rsync is required to deploy the frontend (or pass --no-frontend)"
  mkdir -p "$WEB_ROOT"
  rsync -a --delete "$BUNDLE_DIR/frontend/" "$WEB_ROOT/"
fi

if [ "$(id -u)" -eq 0 ]; then
  chown -R "$APP_USER:$APP_GROUP" "$APP_HOME"
  if [ "$WITH_FRONTEND" -eq 1 ]; then
    chown -R "$APP_USER:$APP_GROUP" "$WEB_ROOT"
  fi
fi

SERVICE_MODE="bin/service.sh"
if [ "$WITH_SYSTEMD" -eq 1 ] && [ "$(id -u)" -eq 0 ] &&
  command -v systemctl >/dev/null 2>&1; then
  sed -e "s|@APP_HOME@|$APP_HOME|g" \
    -e "s|@JAR_PATH@|$APP_HOME/lib/$JAR_NAME|g" \
    -e "s|@APP_USER@|$APP_USER|g" \
    -e "s|@JAVA_BIN@|$JAVA_BIN|g" \
    "$BUNDLE_DIR/deploy/addax-admin.service" >/etc/systemd/system/addax-admin.service
  systemctl daemon-reload
  systemctl enable addax-admin >/dev/null 2>&1 || true
  SERVICE_MODE="systemd (addax-admin.service, enabled but not started)"
fi

if [ "$WITH_FRONTEND" -eq 1 ]; then
  WEB_ROOT_SUMMARY=$WEB_ROOT
else
  WEB_ROOT_SUMMARY='(skipped)'
fi

if [ "$WITH_SYSTEMD" -eq 1 ] && [ "$(id -u)" -eq 0 ]; then
  START_COMMAND='systemctl start addax-admin'
else
  START_COMMAND="$APP_HOME/bin/service.sh start"
fi

cat <<EOF

addax-admin $VERSION installed

  app home     : $APP_HOME
  web root     : $WEB_ROOT_SUMMARY
  service user : $APP_USER ($APP_USER_STATE)
  config       : $APP_HOME/config/env.sh
  start mode   : $SERVICE_MODE

The service and every Addax job it launches run as $APP_USER.
If HDFS rejects this account's writes, re-run install.sh with --user <account> (for example
hive) before starting the service, or grant $APP_USER access to the target paths.

Next steps:
  1. edit $APP_HOME/config/env.sh and set the PostgreSQL / Redis connection
  2. copy $BUNDLE_DIR/deploy/nginx.conf.example to /etc/nginx/conf.d/addax-admin.conf,
     adjust server_name and root, then run: nginx -t && systemctl reload nginx
  3. start the backend:
       $START_COMMAND
  4. open http://<host>/ and sign in as admin/admin123, then change the password
EOF
