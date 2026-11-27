# E1 Foundation: schema, entities, security, auth

**Status:** Done · **Owner:** Architect · **PRD:** AC-1.1 – AC-1.3, NFR-1..6

## Story
As the team, we need the database, entity model, error format and authentication in place, so the
feature agents can work in parallel without touching shared files.

## Delivered
- `schema.sql` (all current-scope tables, constraints, indexes, sequences) and `data.sql` (spec §5.5
  sample + PRD A-12 additions), both idempotent, verified twice against PostgreSQL 16.
- All JPA entities, base repositories, domain enums, `Money`, `Ages`.
- JWT login (`POST /api/auth/login`, `GET /api/auth/me`), role rules, problem+json errors.
- Test harness `support.IntegrationTest` (real Postgres test DB, fixed clock, rollback per test).

## Tests
`AuthApiTest`: 6 tests passing (valid login customer/staff, wrong password, unknown email, no token,
wrong role).

## Shared files (architect-owned, do not rewrite)
`entity/*`, `domain/*`, `security/*`, `exception/*`, `config/*`, `schema.sql`, `data.sql`,
`application*.yml`, `pom.xml`, `test/.../support/*`.
If a story needs a change to one of these, make the smallest edit possible and record it under
"Dev notes" in your story.
