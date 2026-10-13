# EquiTransit Backend

Backend for the Cross-Border Racehorse Transport System.

## Branches

- `main`: repository baseline and reviewed releases.
- `dev`: backend implementation and team integration.
- Task branches: create from `dev`, then submit a Pull Request to `dev`.

The initial account-flow implementation is available on `dev`.

## Stack

Java 21, Spring Boot, Maven, SQL Server.

## Contribution rules

- Keep commits focused and use Conventional Commit titles.
- Review code and run `./mvnw verify` before merging implementation changes.
- Never commit passwords, signing keys, local email outbox or private data.
- Add a migration for schema changes; preserve migrations already applied.
- Publish a reviewed release from `dev` to `main` through a Pull Request.
