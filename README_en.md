<p align="center">
  <img src="docs/assets/social-preview.png" alt="Addax Admin — Enterprise T+1 ODS console for the Addax offline ETL engine" width="100%">
</p>

<p align="center">
  <strong>Make data collection as reliable as utilities</strong><br>
  An enterprise T+1 ODS console for the <a href="https://github.com/wgzhao/addax">Addax</a> offline ETL engine
</p>

<p align="center">
  <a href="README.md">简体中文</a> · English
</p>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <a href="https://github.com/wgzhao/addax-admin/releases"><img src="https://img.shields.io/github/v/release/wgzhao/addax-admin?sort=semver" alt="Release"></a>
  <a href="https://hub.docker.com/r/wgzhao/addax-admin"><img src="https://img.shields.io/docker/pulls/wgzhao/addax-admin?label=docker%20pulls" alt="Docker Pulls"></a>
  <img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1.2-green.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue-3.x-4FC08D.svg" alt="Vue 3">
</p>

<p align="center">
  <a href="#quick-start">Quick start</a> ·
  <a href="docs/manual/README.md">Docs</a> ·
  <a href="#screenshots">Screenshots</a> ·
  <a href="docs/roadmap.md">Roadmap</a> ·
  <a href="https://github.com/wgzhao/addax-admin/releases">Releases</a> ·
  <a href="https://hub.docker.com/r/wgzhao/addax-admin">Docker Hub</a>
</p>

---

## What it is

Addax Admin is an enterprise-grade management and operations console for the Addax offline ETL engine, focused on **landing data in the T+1 ODS layer**. A single UI closes the loop from collection definition to scheduling and execution, backfill, reconciliation, alerting and audit, so data teams can answer three questions: is today's collection healthy, did the data land in the right place, and when something breaks, can we trace and pinpoint it fast.

| Great fit | Deliberately out of scope |
| --- | --- |
| T+1 batch collection and ODS landing | Real-time CDC / streaming sync |
| Hive or relational databases as targets | General-purpose ETL transforms (leave the T to downstream Hive / dbt / Spark) |
| Heterogeneous sources such as MongoDB and Elasticsearch | General-purpose DAG orchestration platforms |
| Teams that need multi-node HA, audit and source-level authorization | General-purpose data quality platforms |

---

## Screenshots

![Dashboard overview](docs/assets/screenshots/dashboard-overview.webp)

*Dashboard overview: today's collection health, KPIs, risks and to-dos, and collection volume trends.*

![Today's runs](docs/assets/screenshots/dashboard-today.webp)

*Today's runs: daily cut heartbeats by source, with drill-down into risks.*

![Collection table detail](docs/assets/screenshots/table-detail.webp)

*Collection table detail: field mapping, execution snapshots, reconciliation and failure records in one place.*

<details>
<summary>More screenshots</summary>

![Home - light and dark](docs/assets/screenshots/dashboard-overview-split.webp)
![Health](docs/assets/screenshots/dashboard-health.webp)
![Data Quality](docs/assets/screenshots/dashboard-quality.webp)
![SLA](docs/assets/screenshots/dashboard-sla.webp)
![Source Management](docs/assets/screenshots/source.webp)
![Target Management](docs/assets/screenshots/target.webp)
![Collection Tables](docs/assets/screenshots/ods-etl.webp)
![Field Mapping](docs/assets/screenshots/table-field.webp)
![Execution Snapshots](docs/assets/screenshots/table-execution.webp)
![Collection Result](docs/assets/screenshots/addax-result.webp)
![Addax Job](docs/assets/screenshots/addax-json.webp)
![Collection Log](docs/assets/screenshots/etl-log.webp)
![Security Audit](docs/assets/screenshots/audit.webp)
![Collection Calendar](docs/assets/screenshots/etl-calendar.webp)
![Type Mappings](docs/assets/screenshots/etl-type-mappings.webp)
![System Settings](docs/assets/screenshots/sys-setting.webp)

See [docs/assets/screenshots/](docs/assets/screenshots/) for the full set.
</details>

---

## Core capabilities

### Collection and modeling

- **Bulk table creation**: create hundreds to thousands of collection tables in a few clicks, then create the matching tables and columns on the target (Hive or relational database) in one step.
- **Target contracts with explicit field mapping**: target tables support platform-managed mode (automatic DDL and schema evolution) and externally managed mode (read-only validation, no DDL). Source and target columns may share the same name or differ; reader output, writer columns, DDL, execution snapshots and error messages all use one and the same one-to-one mapping. Bulk creation previews every table and separates blockers from warnings.
- **Schema evolution**: detect source column type changes and added or dropped columns, then apply them to the target table. Partitioned tables get historical-data compatibility strategies to minimize interruption and manual work.
- **Multiple source types**: relational databases, MongoDB (field inference from collection sampling, multi-database access) and Elasticsearch (indices and aliases, mapping and document sampling, Query DSL filters).
- **Dynamic table names**: first-class support for daily and monthly table templates (e.g. `t_order_${biz_date_short}`), resolving the business date and generating collection jobs automatically.

### Scheduling and execution

- **Incremental collection with smart filters**: use the previous result (for example the max ID of the previous day, or a chosen column) as the incremental condition, or supply a custom filter expression.
- **Backfill**: pick historical dates on a calendar (multiple ranges and single days) from the collection table detail page, backfill day by day asynchronously, and watch batch progress live. Business date variables are injected per target date and never disturb the normal schedule.
- **Hybrid concurrency control**: a database-persisted queue plus Redis leader election and dispatch plus database leases, balancing reliability, auditability and failure recovery.
- **Multi-node concurrency and weights**: shared-nothing architecture, Redis leader election and heartbeats, and node weights (`WEIGHT`) provide global and source-level concurrency control, job high availability and load balancing.

### Trust and governance

- **Data reconciliation**: compare the read and written row counts reported by Addax (written = read − error rows); discrepancies become failure records and trigger alerts.
- **Column-level lineage**: search globally by column name to see which source table a column comes from and which ODS table it lands in.
- **Data quality assertions**: use the non-zero mean of the last 7 days as a baseline to detect empty runs and missed collections, routing hits to the alert and error centers.
- **Execution snapshots**: freeze the definition, field mapping and context when a collection starts, so later configuration changes never rewrite historical conclusions.
- **Failure facts and error center**: record the failing phase, an actionable summary and a stack excerpt, group repeated failures by error fingerprint, and keep the summary after logs expire.
- **Readiness signals**: optionally emit a `collection_complete` webhook when a collection finishes, to drive downstream scheduling.

### Enterprise features

- **Alert center**: WeCom, DingTalk, Feishu, webhook and email (SMTP) channels managed in the UI, with test sends, rule routing, delivery retries, quiet windows and encrypted channel configuration.
- **Source-level authorization**: admin, user and guest roles; regular users are granted operation rights per source; role or password changes invalidate existing tokens immediately.
- **Security audit**: logins, configuration changes and permission denials are audited and exportable as CSV.
- **Extensible**: JWT + Spring Security, Spring Data JPA and a modular frontend, plus white-label configuration (product name, logo, support and documentation links).

---

## Architecture

| Layer | Stack |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA, Spring Security + JWT, PostgreSQL, Redis |
| Frontend | Vue 3, TypeScript, Vite, Vuetify, Pinia |
| Deployment | Docker Compose (app + PostgreSQL + Redis), or Java 21 + Nginx on a host |

```text
addax-admin/
├── backend/                 # Spring Boot service (API, scheduling, persistence, queue and leases)
├── frontend/                # Vue 3 + Vite + TypeScript + Vuetify admin UI
├── docs/                    # manuals, ADRs, testing and security docs, and README screenshots (assets/screenshots/)
├── scripts/                 # deployment and ops scripts (DB init, systemd service template, etc.)
└── screenshots/             # local E2E artifacts (not committed)
```

The schema is managed by a numbered migration chain packaged into the backend jar ([db/migration](backend/src/main/resources/db/migration)): an empty database replays it on first startup, and an existing database is upgraded by the versions recorded in `schema_migration`.

---

## Quick start

Addax Admin only manages and schedules; it does not run the actual collection. All three deployment modes require a separately installed **[Addax engine](https://github.com/wgzhao/addax/releases)** (download the package from the upstream releases).

### Docker (recommended)

Prerequisites: Docker 20.10+, Docker Compose 2.0+, and a reachable Addax installation directory.

```bash
# 1. Create a deployment directory and download the deployment files
mkdir addax-admin && cd addax-admin
RELEASE_TAG=v4.7.0   # replace with the latest release tag
wget "https://raw.githubusercontent.com/wgzhao/addax-admin/${RELEASE_TAG}/docker-compose.yml"
wget "https://raw.githubusercontent.com/wgzhao/addax-admin/${RELEASE_TAG}/.env.example"
cp .env.example .env

# 2. Pull the image and start
export VERSION="${RELEASE_TAG#v}"
docker compose -f docker-compose.yml pull
docker compose -f docker-compose.yml up -d
```

Open <http://localhost:50080>, sign in with `admin` / `admin123`, and change the password immediately.

> Images are built only on release, and `latest` points to the newest release. The `master` branch moves fast and is not meant for production. In mainland China you can switch to the Quay mirror: `echo "DOCKER_REGISTRY=quay.io/wgzhao" >> .env`.

### Host deployment (release bundle)

For hosts that already have Java 21, PostgreSQL, Redis and Nginx but no build toolchain. The bundle ships a prebuilt backend jar, frontend static files and deployment scripts, built by the same release pipeline as the Docker image.

```bash
# 1. Download the bundle from GitHub Releases and verify it (VERSION without the leading v)
VERSION=4.7.0
wget "https://github.com/wgzhao/addax-admin/releases/download/v${VERSION}/addax-admin-${VERSION}-host.tar.gz"
wget "https://github.com/wgzhao/addax-admin/releases/download/v${VERSION}/addax-admin-${VERSION}-host.tar.gz.sha256"
sha256sum -c "addax-admin-${VERSION}-host.tar.gz.sha256"
tar xzf "addax-admin-${VERSION}-host.tar.gz"

# 2. Install to /opt/app/addax-admin, frontend to /var/www/html/addax-admin-ui
#    --user sets the account used by the service and Addax jobs; use --user hive when HDFS only allows hive writes
cd "addax-admin-${VERSION}"
sudo sh deploy/install.sh

# 3. Edit config/env.sh as prompted, confirm the service user in the output, then configure Nginx and start
```

The installer prints the `service user`. That account also determines the privileges Addax jobs have when writing to targets; if it exists it is reused, otherwise it is created with a hint.

`deploy/README.md` inside the bundle is the full deployment guide; the repository version is [Host deployment (bundle)](docs/manual/install/02-host.md) (Chinese).

### From source

For secondary development or following `master`, with extra Maven, Node.js and Yarn requirements. See [Build from source](docs/manual/install/03-source.md#从源码构建部署) (Chinese).

---

## Documentation

The manuals are currently written in Chinese.

- [Installation and usage manual](docs/manual/README.md)
- [Docker deployment](docs/manual/install/01-docker.md)
- [Host deployment (bundle)](docs/manual/install/02-host.md)
- [Build from source](docs/manual/install/03-source.md)
- [Quick start](docs/manual/usage/01-quick-start.md)
- [Target table structure and field mapping](docs/manual/usage/05-target-contract.md)
- [Target capabilities and table properties](docs/manual/usage/06-target-engines.md)
- [MongoDB collection](docs/manual/usage/10-mongodb.md)
- [Elasticsearch collection](docs/manual/usage/11-elasticsearch.md)
- [Scheduling and concurrency](docs/manual/usage/09-scheduling.md)
- [Backfill](docs/manual/usage/12-fillback.md)
- [Troubleshooting](docs/manual/operations/02-troubleshooting.md)
- [Architecture decision records](docs/adr/)

---

## Contributing

Issues and pull requests are welcome. Please read the [contributing guide](CONTRIBUTING.md) first; report security issues privately as described in [SECURITY.md](SECURITY.md).

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).
