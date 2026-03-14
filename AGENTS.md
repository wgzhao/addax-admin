# Project Contract

## Build and Test

- Install: `yarn build:all` (backend JAR + frontend build)
- Dev: `yarn run dev`

## Architecture Boundaries

- HTTP handlers live in `backend/src/main/java/com/wgzhao/addax/admin/controller/`
- Domain logic lives in `backend/src/main/java/com/wgzhao/addax/admin/model/` and subpackages
- Do not put persistence logic in handlers

## Coding Conventions

- Prefer pure functions in domain layer
- Do not introduce new global state without explicit justification
- Use Java 21 features where appropriate (e.g. records, sealed classes)  

## Safety Rails

### NEVER

- Modify `.env`, lockfiles, or CI secrets without explicit approval
- Remove feature flags without searching all call sites
- Commit without running tests

### ALWAYS

- Show diff before committing
- Update CHANGELOG for user-facing changes

## Compact Instructions

Preserve:

1. Architecture decisions (NEVER summarize)
2. Modified files and key changes
3. Current verification status (pass/fail commands)
4. Open risks, TODOs, rollback notes
