# Test strategy

*BMAD role: QA (test architect).*

## Levels

| Level | Tool | What | Where |
|---|---|---|---|
| Domain unit | JUnit 5 | Pure rules: schedule maths, status, penalty, age | `backend/src/test/.../domain` |
| Service unit | JUnit 5 + Mockito | Edge cases that are awkward through HTTP: AI fallback, hallucinated codes, races | `.../service`, `.../ai` |
| API integration | MockMvc + real PostgreSQL (`healthdekho_test`) | Every endpoint: happy path, validation, business errors, authorization | `backend/src/test/...` |
| Front end | Vitest + Testing Library | Form validation, formatting, payment selection logic | `frontend/src/**/*.test.jsx` |
| Manual demo | Browser | The end-to-end script below | |

Integration tests extend `support.IntegrationTest`: seeded data, clock fixed at **2026-11-25 10:00 IST**,
each test rolled back. The AI model is never called in tests (`InsuranceAdvisor` is mocked).

## Seed facts tests rely on

| Fact | Value |
|---|---|
| Aarav (1001), DOB 1990-04-15, age 36 | holds HDP-2026-0001 = HD-TERM-001, monthly ₹1,250, 240 installments; #1 PAID, #2 PARTIALLY_PAID (500), rest PENDING |
| Meera (1002), DOB 1985-09-22, age 41 | HDP-2026-0002 = HD-HEALTH-001, status ACTIVE, premium payment status FULLY_PAID (PDF sample); HDP-2026-0003 = HD-SAV-001 quarterly ₹7,500, #1 PAID, #2 OVERDUE (grace ended 2026-10-15) — from `demo-data.sql` |
| Rohan (1003), DOB 2000-01-10, age 26 | no policies |
| Staff | ops.desk@example.com (`demo-data.sql`) |
| Products | 201–203 from the PDF sample (`data.sql`); 204–208 demo extras incl. inactive HD-HEALTH-000 (`demo-data.sql`) |
| Passwords | customers `Demo@1234`, staff `Ops@1234` |

## Demo script (manual, end to end)

1. Log in as Rohan → empty policy table, 7 available products.
2. Get recommendations with goal "health cover for my parents", type HEALTH → table with reasons.
3. Register the top one → policy number shown → appears on dashboard, gone from available products.
4. Log in as Meera → Payments → HDP-2026-0003 → #2 shows OVERDUE with ₹750 penalty preview → pay →
   receipt with TXN reference → history updated.
5. Log in as staff → Search "meera" → 2 rows with remaining premium → open HDP-2026-0001 in Payments.
6. Wrong password → error message. Try `/dashboard` while logged out → login page.

## Gate
A story is **Done** when its required tests pass, `mvn test` and `npm test` are green on `main`, and QA
has recorded PASS for each AC (or CONCERNS with an accepted reason).
