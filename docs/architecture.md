# Architecture: HealthDekho Insurance Portal

*BMAD phase: Solutioning. Owner: Architect. Implements [prd.md](prd.md) (v2, 27 Nov 2026).*

> **v2 changes** (from [change-analysis-v2.md](change-analysis-v2.md)): §5.2 column names; policy status vs premium
> payment status split (`fully_paid_on`); FR-4 A and FR-5 B totals; spec-only `data.sql` plus `demo-data.sql`.

## 1. Shape of the system

```
React SPA (Vite, :5173)  --/api (Vite proxy)-->  Spring Boot API (:8080)  -->  PostgreSQL 16
        Axios + JWT bearer                         |
                                                   └── Spring AI ChatClient --> OpenAI-compatible model
```

One deployable backend, one static front end. No message brokers, caches or extra services: the load
is a handful of users and the spec doesn't ask for them.

## 2. Stack (fixed by the spec)

| Concern | Choice |
|---|---|
| Language | Java 21 (records, switch expressions, pattern matching) |
| Framework | Spring Boot 3.5.7: Web, Validation, Data JPA, Security (+ OAuth2 resource server module for JWT) |
| AI | Spring AI 1.1.8, `spring-ai-starter-model-openai` (latest 1.x line, which supports Boot 3.5; 2.x needs Boot 4) |
| Database | PostgreSQL 16. Schema in `schema.sql`, seed in `data.sql`, run by Spring Boot SQL init. Both scripts are idempotent. Hibernate runs with `ddl-auto=validate`. |
| Front end | React 18, function components + hooks, Axios, React Router, Vite. Plain CSS, no UI kit. |
| Tests | JUnit 5, Mockito, Spring MockMvc against a real PostgreSQL test database; Vitest + Testing Library on the front end. |

## 3. Backend layers and packages

Base package `com.healthdekho.insurance`.

| Package | Holds | Rule |
|---|---|---|
| `controller` | `@RestController`s | Thin. Validate input (`@Valid`), call one service method, return a DTO. |
| `service` | Business logic, `@Transactional` boundaries | Only layer that touches repositories and entities. |
| `repository` | Spring Data interfaces | Derived queries or JPQL. No business rules. |
| `entity` | JPA entities | Never returned from a controller. |
| `dto.<feature>` | Request/response **records** | Bean Validation annotations on requests. |
| `domain` | Enums and pure calculators (`Money`, `Ages`, schedule maths, status rules) | No Spring, no I/O. Unit-tested directly. |
| `ai` | Spring AI integration (prompt building, model call, output parsing, fallback) | Called only by `RecommendationService`. |
| `security` | JWT issue/verify, user lookup, `AuthenticatedUser` | |
| `exception` | Exceptions + `GlobalExceptionHandler` | |
| `config` | `Clock`, MVC config | |

The current caller is injected into controller methods as `AuthenticatedUser user` (id, email,
fullName, role). Services receive it and enforce ownership.

## 4. Data model

Physical schema: [`backend/src/main/resources/schema.sql`](../backend/src/main/resources/schema.sql).

```
customer 1──* customer_policy *──1 insurance_product
   │                 │                    │
   │                 1                    │
   │                 *                    │
   │         premium_installment          │
   │                 1                    │
   │                 *                    │
   │          premium_payment             │
   1                                      │
   *                                      │
ai_recommendation_request 1──* ai_recommendation_item *──1
staff_user (stand-alone, login only)
```

Column names follow PDF §5.2: `premium_installment.installment_number`, `grace_period_end_date`;
`customer_policy.total_premium_payable`; `ai_recommendation_request.preferred_term` (text, e.g. "20 years").
Java fields and JSON names match (`installmentNumber`, `gracePeriodEndDate`, `totalPremiumPayable`).

Notes:
- `customer_policy.status` is the state of cover: `ACTIVE`, shown as `MATURED` once the maturity date has
  passed. "Fully paid" is not a policy status: it is recorded in `customer_policy.fully_paid_on` (set when the
  last installment is paid) and reported as the derived *premium payment status* (§5).
- `customer_policy` copies premium, frequency, sum insured and total premium payable from the product at
  registration. These are the contracted values. The term is **not** copied; it is read from the
  product (spec §5.1).
- `premium_installment.version` is a JPA `@Version` column (optimistic locking for payments).
- `premium_payment` is mapped `@Immutable`: insert only.
- Policy numbers and transaction references come from sequences `policy_number_seq` and
  `transaction_ref_seq` (`CustomerPolicyRepository.nextPolicyNumberSequence()`,
  `PremiumPaymentRepository.nextTransactionSequence()`).

## 5. Business rules (the formulas)

All in Java, `BigDecimal` scale 2 `HALF_UP` (`domain.Money`), `LocalDate` arithmetic.

| Rule | Formula |
|---|---|
| Age | `Ages.ageOn(dob, date)` = `Period.between(dob, date).getYears()` |
| Months per installment | MONTHLY 1, QUARTERLY 3, HALF_YEARLY 6, YEARLY 12 (`PremiumFrequency`) |
| Installment count | `termMonths / monthsPerInstallment` (DB CHECK guarantees it divides) |
| Maturity date | `start.plusMonths(termMonths).minusDays(1)` |
| Total premium | `premiumAmount × installmentCount` |
| Due date of installment *n* (1-based) | `start.plusMonths((n − 1) × monthsPerInstallment)`, always computed from `start`, never chained |
| Grace end | `dueDate.plusMonths(1)` |
| Outstanding (installment) | `scheduled + penalty − paid` |
| Status | outstanding = 0 → PAID; today > graceEnd → OVERDUE; paid > 0 → PARTIALLY_PAID; else PENDING |
| Payable | status ≠ PAID and `dueDate ≤ today.plusMonths(1)` |
| Late penalty | if `paymentDate > graceEnd` and `penalty == 0`: `penalty = 10% × outstanding` (before this payment), added to installment and stored on the payment row |
| Remaining premium (policy) | Σ outstanding over installments with status ≠ PAID |
| Policy fully paid | every installment PAID → set `fully_paid_on = today` (never before) |
| Premium payment status (policy) | all PAID → FULLY_PAID; any OVERDUE → OVERDUE; any PARTIALLY_PAID → PARTIALLY_PAID; else PENDING |
| Policy status shown | `ACTIVE`, or `MATURED` when today > maturity date |
| Total premium payable (FR-3 A, FR-4 A, FR-5 B) | contract total premium (premium × installments); never includes penalties |
| Late penalties | Σ penalty applied, shown as its own figure |
| Total premium paid | Σ paid (penalties included) |
| Total remaining premium | Σ outstanding = payable + penalties − paid |
| Entry age check | `min ≤ age at start date ≤ max` |
| Start date window | `today ≤ start ≤ today + 90 days` |

## 6. REST API contract

Base path `/api`. JSON. Dates ISO `yyyy-MM-dd`, money as JSON numbers with 2 decimals, enums as their
names. Errors are problem+json:

```json
{ "status": 422, "title": "Unprocessable Entity", "detail": "Customer age 61 is outside the entry age 18–60 for this product.",
  "code": "ENTRY_AGE_NOT_ELIGIBLE", "errors": { "startDate": "..." } }
```

| HTTP | When |
|---|---|
| 400 | Bean validation / malformed input (`errors` map) |
| 401 | Not signed in, bad credentials, expired token |
| 403 | Signed in with the wrong role for the endpoint |
| 404 | Not found, **or owned by another customer** (we don't confirm existence) |
| 409 | Duplicate registration, concurrent update, duplicate payment |
| 422 | Business rule broken (`code` set) |

### Auth (done)
| Method | Path | Role | Body / Params | Response |
|---|---|---|---|---|
| POST | `/auth/login` | public | `{email, password}` | `{token, expiresAt, user: UserProfile}` |
| GET | `/auth/me` | any | | `UserProfile {id, fullName, email, role, dateOfBirth, phone}` |

### Dashboard, discovery, registration (E2)
| Method | Path | Role | Body / Params | Response |
|---|---|---|---|---|
| GET | `/customers/me/policies` | CUSTOMER | | `PolicySummary[]` |
| GET | `/products/available` | CUSTOMER | | `Product[]` (active, not held) |
| GET | `/products/{productCode}` | CUSTOMER | | `Product` (404 if inactive/unknown) |
| GET | `/policies/registration-preview` | CUSTOMER | `productCode`, `startDate` (optional, default today) | `RegistrationPreview` |
| POST | `/policies` | CUSTOMER | `{productCode, startDate, recommendationItemId?}` | 201 `RegistrationResult` |

```
PolicySummary      {policyNumber, productCode, policyName, policyType, termMonths, startDate, maturityDate,
                    premiumFrequency, premiumAmount, sumInsured, totalPremiumPayable, status: ACTIVE|MATURED}
Product            {productCode, policyName, policyType, description, termMonths, premiumFrequency,
                    premiumAmount, sumInsured, minEntryAge, maxEntryAge}
RegistrationPreview{customer: {fullName, dateOfBirth, email, phone}, product: Product, startDate,
                    maturityDate, installmentCount, totalPremiumPayable, ageAtStart, eligible, ineligibilityReason}
RegistrationResult {policyNumber, message, policy: PolicySummary}
```
Error codes: `PRODUCT_NOT_AVAILABLE`, `ENTRY_AGE_NOT_ELIGIBLE`, `START_DATE_OUT_OF_RANGE`,
`DUPLICATE_POLICY` (409), `INVALID_RECOMMENDATION_ITEM`.

### Recommendations (E3)
| Method | Path | Role | Body | Response |
|---|---|---|---|---|
| POST | `/recommendations` | CUSTOMER | `{dateOfBirth?, goals?, preferredTermYears?, policyType?}` | `RecommendationResult` |

```
RecommendationResult {requestId, calculatedAge, source: MODEL|RULE_BASED, modelName, generatedAt,
                      notice (nullable, e.g. fallback or "no eligible products"),
                      items: [{itemId, rank, productCode, policyName, policyType, termMonths, sumInsured,
                               premiumFrequency, premiumAmount, reason, suitability}]}
```
Validation: at least one input; `dateOfBirth` not in future; `goals` ≤ 500 chars;
`preferredTermYears` 1–40.

### Policy lookup and payments (E4)
| Method | Path | Role | Body | Response |
|---|---|---|---|---|
| GET | `/policies/{policyNumber}` | owner or STAFF | | `PolicyDetail` |
| GET | `/policies/{policyNumber}/installments` | owner or STAFF | | `Installment[]` |
| POST | `/policies/{policyNumber}/payments` | owner or STAFF | `PaymentRequest` | 201 `PaymentReceipt` |
| GET | `/policies/{policyNumber}/payments` | owner or STAFF | | `PaymentHistoryEntry[]` (newest first) |

```
PolicyDetail   {policyNumber, customerName, customerDateOfBirth, productCode, policyName, policyType, termMonths,
                startDate, maturityDate, premiumFrequency, premiumAmount, sumInsured,
                totalPremiumPayable, latePenalties, totalPremiumPaid, remainingPremium,
                paidInstallments, totalInstallments, status: ACTIVE|MATURED,
                premiumPaymentStatus: PENDING|PARTIALLY_PAID|OVERDUE|FULLY_PAID, fullyPaidOn (nullable)}
Installment    {installmentId, installmentNumber, premiumPeriod ("2026-11"), dueDate, gracePeriodEndDate,
                scheduledAmount, penaltyAmount, paidAmount, outstandingAmount, status, payable,
                penaltyIfPaidToday}
PaymentRequest {paymentMethod: UPI|NET_BANKING|CARD|CASH|CHEQUE, items: [{installmentId, amount}] (1..24)}
PaymentReceipt {policyNumber, paymentDate, paymentMethod, totalPaid, totalPenalty, premiumPaymentStatus,
                remainingPremium, lines: [{transactionReference, installmentNumber, amountPaid, penaltyApplied,
                installmentStatus, installmentOutstanding}]}
PaymentHistoryEntry {transactionReference, installmentNumber, premiumPeriod, paymentDate, amountPaid,
                     penaltyAmount, paymentMethod}
```
`penaltyIfPaidToday` is the penalty that *would* be added if paid today (0 when not late or already
applied), so the UI can show it before the user confirms.
Error codes: `INSTALLMENT_NOT_PAYABLE`, `INSTALLMENT_ALREADY_PAID` (409), `AMOUNT_EXCEEDS_OUTSTANDING`,
`DUPLICATE_INSTALLMENT_IN_REQUEST`, `INSTALLMENT_NOT_IN_POLICY`, `CONCURRENT_UPDATE` (409).

### Search (E5)
| Method | Path | Role | Params | Response |
|---|---|---|---|---|
| GET | `/policies/search` | any | `policyNumber?`, `customerName?`, `dateOfBirth?`, `page=0`, `size=10` | `PageResponse<PolicySearchResult>` |

```
PageResponse<T>    {content: T[], page, size, totalElements, totalPages}
PolicySearchResult {policyNumber, customerName, dateOfBirth, policyName, policyType, startDate,
                    maturityDate, totalPremiumPayable, premiumPaymentStatus, totalPremiumPaid,
                    remainingPremium, latePenalties}
```
Error code `INVALID_SEARCH_CRITERIA` (400 via problem detail with code).
Note `/policies/search` must be mapped so it is not captured by `/policies/{policyNumber}`
(Spring prefers the literal path; keep it a literal `@GetMapping("/search")`).

## 7. Security

- `POST /api/auth/login` authenticates with `DaoAuthenticationProvider` + BCrypt
  (`DelegatingPasswordEncoder`, hashes stored with `{bcrypt}` prefix) and issues an HS256 JWT
  (claims: `sub` email, `uid`, `role`, `name`; 2 h TTL). Signed with `JWT_SECRET`.
- All other `/api/**` requests are authenticated by Spring Security's resource-server JWT filter.
  Expiry is checked with the same `Clock` bean that issued the token.
- URL-level role rules live in `SecurityConfig`. Ownership rules live in services:
  `user.isStaff() || policy.customer.id == user.id`, otherwise 404.
- Stateless, CSRF off (no cookies), CORS restricted to `CORS_ORIGINS`.
- The front end keeps the token in `sessionStorage` (gone when the tab closes) and attaches it with an
  Axios interceptor. A 401 clears it and redirects to `/login`.

## 8. AI integration (FR-2)

```
RecommendationService (transactional)
  1. age = Ages.ageOn(dob or profile dob, today)
  2. candidates = active products − held products − age-ineligible − (type filter)
  3. if candidates empty → save request (RULE_BASED, no items), return notice, no model call
  4. try  InsuranceAdvisor.rank(profile, inputs, candidates)        ← ai package, Spring AI ChatClient
     catch any exception / empty or unusable output → RuleBasedRanker.rank(...)
  5. keep only codes present in candidates, de-duplicate, cap at 3, re-number ranks 1..n
  6. save request + items (model name/version, prompt version "rec-v1", prompt text), return DTO
```

- `ChatClient` is built from the auto-configured `ChatClient.Builder`. Output is mapped to a record
  via Spring AI structured output (`.call().responseEntity(...)` so the response metadata gives the
  actual model id for `model_version`).
- The system prompt tells the model it may only choose from the listed product codes, must not invent
  prices, and must answer in the requested JSON shape. User goals are passed as data, inside a
  delimited block, so instructions inside them are not followed. Server-side validation (step 5) is
  the real guarantee.
- `spring.ai.retry.max-attempts=2` and a 30 s read timeout keep the fallback quick.
- Provider is chosen with env vars `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL`, `AI_COMPLETIONS_PATH`.

## 9. Front end

```
frontend/src/
  api/          axios client (baseURL /api, bearer interceptor, 401 handling) + one module per feature
  auth/         AuthContext (token, user, login, logout), RequireAuth route guard
  components/   Shared pieces: DataTable, Money, DateText, StatusBadge, Alert, Spinner, Field, Pager
  pages/        LoginPage, DashboardPage, RecommendationsPage, RegisterPolicyPage,
                PremiumPaymentsPage, PolicySearchPage, NotFoundPage
  utils/        format.js (₹ en-IN, dates), errors.js (problem+json → message/field errors)
  styles/       one global stylesheet with CSS custom properties
```
Routes: `/login`, `/dashboard`, `/recommendations`, `/register/:productCode` (optional
`?rec=<itemId>`), `/payments` (optional `?policy=<number>`), `/search`. Staff land on `/search` and see
only Payments and Search in the nav.

## 10. Configuration

| Profile | Purpose |
|---|---|
| `dev` (default) | Local Postgres, `data.sql` (PDF §5.5 sample) + `demo-data.sql` (extras), dev JWT secret default, debug logging |
| `test` | `healthdekho_test` database, same two seed files, fixed secret, AI pointed at nothing (tests stub it) |
| `prod` | Every secret from env, schema only, no seed |

Env vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_TTL`, `CORS_ORIGINS`, `AI_BASE_URL`,
`AI_API_KEY`, `AI_MODEL`, `AI_COMPLETIONS_PATH`, `PORT`.

## 11. Testing approach

See [qa/test-strategy.md](qa/test-strategy.md). In short: pure rules in `domain` get plain unit tests;
services get Mockito tests for edge cases; each endpoint gets a MockMvc test against the seeded test
database, extending `support.IntegrationTest` (fixed clock 25 Nov 2026, rolled back after each test).

## 12. Decisions (ADR log)

| ADR | Decision | Why |
|---|---|---|
| 1 | JWT via Spring Security's resource-server module, not a third-party JWT library | Stays inside Spring Security; less code to trust. |
| 2 | Separate `staff_user` table instead of a role column on `customer` | Staff aren't customers (no DOB, no policies); keeps the customer table as specified. |
| 3 | Spring Boot SQL init instead of a migration tool | The spec fixes the stack; scripts are idempotent so restarts are safe. A migration tool is the first thing to add if the schema starts evolving. |
| 4 | Ownership failures return 404, not 403 | Doesn't confirm that someone else's policy number exists. |
| 5 | Payment status recomputed on read and on write | OVERDUE depends on today's date; no scheduler needed. |
| 8 | Policy status (cover) separate from premium payment status | The PDF sample keeps a fully paid policy ACTIVE; FR-5 lists "Premium Payment Status" as its own column. |
| 6 | Rule-based fallback for recommendations | Demo and tests work without an API key; the AI path is still the default when configured. |
| 7 | Registration preview computed on the server | One implementation of the date and money maths, used by both the form and the save. |
