# Contributing

## Branches

- `main`: reviewed releases; initial repository baseline before the first release.
- `dev`: team integration.
- `feature/<issue>-<name>`: new functionality from `dev`.
- `fix/<issue>-<name>`: bug fixes from `dev`.
- `db/<issue>-<name>`: database changes from `dev`.

Create a task branch from the latest `dev`, commit focused changes, and open a
Pull Request targeting `dev`. Request a review from another member. Publish a
reviewed release to `main` through a separate Pull Request.

## Commits

Use Conventional Commit titles, for example:

```text
feat(auth): add email verification
fix(auth): reject expired recovery tokens
docs(api): explain customer profile response
```

## Before submitting

Run `./mvnw verify` with JDK 21 (Windows: `.\mvnw.cmd verify`). Tests use an
isolated H2 database by default; SQL Server integration testing is described
in README. Never configure tests against the production EquiTransit database.

Follow the project Checkstyle rules and manually review the remaining Oracle
Java convention requirements described in README.

Add a migration for schema changes. Do not rewrite migrations already applied
by the team. Explain migration order and compatibility with existing data.

Do not commit `.local/`, `.env` credentials, signing keys, real personal data,
logs, IDE settings or generated builds. `.env.example` contains placeholders.
