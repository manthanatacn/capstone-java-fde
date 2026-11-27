# E7 Backend alignment with requirements v2

**Status:** Done · **Owner:** Dev agent "backend-v2" · **Source:** PDF v2 · **PRD:** FR-1 E, AC-4A.*, AC-4C.10, AC-5B.*, AC-5C.3, NFR-10, A-6, A-16, A-17
**Why:** [change-analysis-v2.md](../change-analysis-v2.md) D-3, D-5 … D-10.

## Tasks
1. **Schema (`schema.sql`)**, PDF §5.2 names:
   - `premium_installment.installment_no` → `installment_number`, `grace_end_date` → `grace_period_end_date`
     (rename the unique constraint/indexes to match). Replace the two paid-amount CHECKs with one that reads like
     the spec: `paid_amount between 0 and scheduled_amount + penalty_amount`.
   - `customer_policy.total_premium` → `total_premium_payable`; add `fully_paid_on date` (nullable);
     status CHECK becomes `('ACTIVE', 'MATURED')`; add CHECK `fully_paid_on is null or fully_paid_on >= start_date`.
   - `ai_recommendation_request.preferred_term_years` → `preferred_term varchar(20)` (text like "20 years").
2. **Seed split**
   - `data.sql` = exactly PDF §5.5: customers 1001–1003, products 201–203, policies 3001–3002 (3002 **ACTIVE**,
     `fully_paid_on = 2026-11-20`), all their installments (3001: 240 rows, 4001–4003 as in the sample), payments
     5001–5003, recommendation requests 6001–6002 (`preferred_term` '20 years', '1 year'), items 7001–7003.
   - `demo-data.sql` = our extras, with a header comment saying they are not part of the PDF sample: staff
     user 9001, products 204–208, policy 3003 (Meera, HD-SAV-001, start 2026-06-15, #2 overdue) with its 40
     installments and payment 5004. Keep every statement idempotent and sequences only moving forward.
   - `spring.sql.init.data-locations: classpath:data.sql,classpath:demo-data.sql` in `application-dev.yml`
     and `src/test/resources/application-test.yml`. Prod stays schema-only.
3. **Entities and domain**
   - Rename Java fields to match (`installmentNumber`, `gracePeriodEndDate`, `totalPremiumPayable`,
     `preferredTerm` as String). `PolicyStatus` = `ACTIVE, MATURED`. `CustomerPolicy.markFullyPaid(LocalDate)`
     sets `fullyPaidOn`; `effectiveStatus(LocalDate today)` returns MATURED after the maturity date.
   - New `domain/PremiumPaymentStatus` (`PENDING, PARTIALLY_PAID, OVERDUE, FULLY_PAID`) and a pure derivation
     from installment statuses (architecture §5). Use a switch expression or pattern-matching switch.
4. **DTOs and services** exactly as architecture §6 v2: `PolicySummary`, `RegistrationPreview`, `PolicyDetail`,
   `Installment`, `PaymentReceipt`, `PaymentHistoryEntry`, `PolicySearchResult`. Totals per architecture §5
   (`payable = contract + penalties`, `remaining = payable − paid`). Payment service marks `fully_paid_on`
   instead of changing status.
5. **FR-5 search totals:** one grouped query for the whole page returning per policy what is needed for
   paid / penalties / outstanding / premium payment status ("overdue" = outstanding > 0 and grace end before
   today, so stale stored statuses don't matter). Build with `Collectors.groupingBy` or `toMap`. No per-row queries.
6. **FR-1 E audit:** check every FR-1 E item in the FR-1 code (dashboard, available products, recommendations,
   registration entry). `instanceof` pattern matching only where it is genuinely the right tool (for example,
   classifying the model failure in `RecommendationService` for logging). Record a short table in Dev notes:
   expectation → file:line, for FR-1 E, FR-3 C, FR-4 D, FR-5 D.
7. **Recommendation API** keeps `preferredTermYears` (int) in JSON; the service stores `preferred_term` text.

## Files
Backend only (`backend/**`) plus this story file. Update all affected tests; add tests listed below.

## Tests required (in addition to keeping the suite green)
- Seed: 3002 comes back `status ACTIVE`, `premiumPaymentStatus FULLY_PAID`, `fullyPaidOn 2026-11-20`.
- Premium payment status derivation unit test (each case, mixed statuses, empty schedule not possible).
- Paying the last installment sets `fullyPaidOn` and FULLY_PAID; paying any other leaves it null.
- FR-4 A totals reconcile on Meera's 3003 after the late payment (payable 300750.00, paid 15750.00, remaining 285000.00).
- FR-5 result has exactly the v2 fields; Aarav PARTIALLY_PAID, Meera 3003 OVERDUE, 3002 FULLY_PAID.
- `effectiveStatus` returns MATURED after maturity.

## Dev notes

### What changed
- **Schema** (`schema.sql`): §5.2 names `installment_number`, `grace_period_end_date` (constraints renamed to
  `uq_installment_policy_number`, `ck_installment_number_positive`), `total_premium_payable`, `preferred_term
  varchar(20)`. The two paid-amount CHECKs are now one: `ck_installment_paid_amount check (paid_amount between 0
  and scheduled_amount + penalty_amount)`. `customer_policy.fully_paid_on date` added with
  `ck_policy_fully_paid_on`; status CHECK is `('ACTIVE', 'MATURED')`; the `preferred_term_years > 0` CHECK is gone
  (the column is text now; the API still validates 1–40).
- **Seeds**: `data.sql` is exactly §5.5 (3 customers, products 201–203, policies 3001–3002 with 3002 ACTIVE and
  `fully_paid_on = 2026-11-20`, 3001's 240 installments and 3002's one, payments 5001–5003, requests 6001–6002 with
  `'20 years'` / `'1 year'`, items 7001–7003). New `demo-data.sql` holds staff 9001, products 204–208, policy 3003
  with its 40 installments and payment 5004, under a header saying it is not the PDF sample. Sequences only move
  forward (`policy_number_seq` 2 → 3, `transaction_ref_seq` 3 → 4). Dev and test load both; prod loads neither.
- **Domain**: `PolicyStatus` = `ACTIVE, MATURED`. New `PremiumPaymentStatus` with `of(Collection<PaymentStatus>)`
  (switch expressions; the policy takes its most pressing installment status). New `PremiumTotals` record:
  `payable = contract + penalties`, `remaining = payable − paid`, so the three totals reconcile by construction.
  `Formats.years(int)` ("1 year", "20 years") shared by the rule-based ranker and the stored `preferred_term`.
- **Entities**: fields renamed to match the columns. `CustomerPolicy.markFullyPaid(LocalDate)` sets `fullyPaidOn`
  once (first date kept); `effectiveStatus(today)` returns MATURED after the maturity date, otherwise the stored
  status. `AiRecommendationRequest.preferredTerm` is a String.
- **DTOs** match architecture §6 v2 field for field: `PolicySummary` (`totalPremiumPayable`, status
  ACTIVE|MATURED), `RegistrationPreview` (`totalPremiumPayable`), `PolicyDetail` (`totalPremiumPayable,
  latePenalties, totalPremiumPaid, remainingPremium, …, status, premiumPaymentStatus, fullyPaidOn`), `Installment`
  (`installmentNumber`, `gracePeriodEndDate`), `PaymentReceipt` (`premiumPaymentStatus`, `remainingPremium`,
  lines' `installmentNumber`), `PaymentHistoryEntry` (`installmentNumber`), `PolicySearchResult` (the FR-5 B columns
  plus `latePenalties`). Class names kept (`…Response`); only JSON is the contract.
- **Payments**: the service no longer changes the policy status; when every installment is PAID it calls
  `markFullyPaid(today)`. Lock order, optimistic lock, penalty and error codes unchanged.
- **FR-5 search**: `PolicySearchRepository.tallyInstallments(ids, today)` is one JPQL query for the whole page,
  grouped by policy and an installment status worked out from amounts and dates (`paid >= scheduled + penalty` →
  PAID, `grace_period_end_date < today` → OVERDUE, `paid > 0` → PARTIALLY_PAID, else PENDING), so stale stored
  statuses do not matter. The service splits the rows with `Collectors.groupingBy(InstallmentTally::policyId)`.
  Still two queries per page (policies + count) plus this one, none per row. `RemainingPremium` projection removed.
- **FR-1 E audit**: every item was already present except `instanceof` in FR-1 code beyond the JWT resolver.
  `RecommendationService.logModelFailure` now classifies the model failure: `ResourceAccessException io` (logs the
  root cause, which says refused vs timed out), `NonTransientAiException` (bad key/model), `TransientAiException`
  (provider down after retries), anything else. Spring AI 1.1.8's `RetryUtils` retries the first and last of these
  and then rethrows, so these are the types that actually reach the service (checked with `javap`).

### Java 21 expectations → code
| PDF item | Where |
|---|---|
| FR-1 E records for DTOs | `dto/policy/PolicySummaryResponse.java:12`, `dto/policy/ProductResponse.java:9`, `dto/recommendation/RecommendationRequest.java:14` |
| FR-1 E LocalDate / Java Time | `domain/Ages.java:16` (`Period.between`), `service/RecommendationService.java:95,117`, `entity/CustomerPolicy.java:123` (`effectiveStatus`) |
| FR-1 E Optional for repository lookups | `service/RecommendationService.java:86`, `service/ProductService.java:42` |
| FR-1 E Streams filter available products, exclude held policies | `service/ProductService.java:30`, `service/RecommendationService.java:114` |
| FR-1 E Collectors | `service/ProductService.java:33` (`toSet`), `service/RecommendationService.java:120` (`toSet`), `:160` (`toMap`) |
| FR-1 E `instanceof` pattern matching | `security/AuthenticatedUserArgumentResolver.java:24` (`instanceof Jwt jwt`), `service/RecommendationService.java:186` (`instanceof ResourceAccessException io`) |
| FR-3 C records | `dto/policy/RegisterPolicyRequest.java:12`, `dto/policy/RegistrationPreviewResponse.java:7`, `dto/policy/RegistrationResultResponse.java:3`, `domain/PolicySchedule.java:9` |
| FR-3 C LocalDate / Java Time | `domain/PolicySchedule.java:21` (due dates, maturity), `service/PolicyRegistrationService.java:133` (start window), `:75` (age at start) |
| FR-3 C BigDecimal | `domain/PolicySchedule.java:9` (`totalPremiumPayable`), `domain/Money.java` |
| FR-3 C Optional | `service/PolicyRegistrationService.java:122,127,142` |
| FR-3 C Streams | `domain/PolicySchedule.java:21` (`IntStream.rangeClosed`) |
| FR-4 D records | `dto/payment/PaymentRequest.java:12`, `dto/payment/PaymentReceiptResponse.java:11,21` |
| FR-4 D YearMonth | `entity/PremiumInstallment.java:83` (`getPremiumPeriod`) |
| FR-4 D BigDecimal | `domain/InstallmentRules.java:48` (10% penalty), `domain/PremiumTotals.java:9` |
| FR-4 D switch for payment status | `domain/InstallmentRules.java:31`, `domain/PremiumPaymentStatus.java:23,32` |
| FR-4 D Streams / collections | `service/PremiumPaymentService.java:145` (`groupingBy` + `counting`), `service/PolicyLookupService.java:128` |
| FR-4 D transactions | `service/PremiumPaymentService.java:56` (`@Transactional`), `:62` (policy row lock) |
| FR-5 D records | `dto/search/PolicySearchResult.java:10`, `dto/common/PageResponse.java:9`, `repository/InstallmentTally.java` |
| FR-5 D Streams entity → DTO | `service/PolicySearchService.java:67` (`PageResponse.of` mapping) |
| FR-5 D `Collectors.groupingBy` | `service/PolicySearchService.java:77` |
| FR-5 D Optional single-record lookup | `service/PolicyLookupService.java:105` |
| FR-5 D pagination | `service/PolicySearchService.java:56`, `repository/PolicySearchRepository.java` (`Page`/`Pageable`) |

### Deviations and why
- **Dashboard `PolicySummary.totalPremiumPayable` is the contract total**, not contract + penalties. FR-1 B does not
  list the field, A-17 says registration shows the contract total, and architecture §5 scopes "contract +
  penalties" to FR-4 A and FR-5 B. After a late payment the dashboard and the payments page can therefore differ
  by the penalty; `PolicyDetail.latePenalties` explains the gap.
- **`remainingPremium` is `payable − paid`** rather than a separate sum of outstanding amounts. The two are equal
  (every installment's `scheduled` adds up to the contract total), and this way the reconciliation holds by
  construction.
- **No migration for existing databases.** `create table if not exists` cannot rename columns, so an existing
  `healthdekho` dev database must be dropped and recreated before running this branch (ADR-3 still holds; a
  migration tool is the next step if this happens again). I did not touch the dev database.
- **Environment**: when I started, Docker Desktop was stopped and `healthdekho-db` had exited, and nothing was on
  8080. I started Docker Desktop and the container (nothing else) to run the tests; the dev server was not started.

### Verification
- `mvn -q test`: **149 tests, 0 failures, 0 errors** (130 before + 19 new), on a freshly recreated
  `healthdekho_test`, then run again against the same database (SQL init re-applied without errors).
- `schema.sql` + `data.sql` + `demo-data.sql` run twice into a new scratch database: no errors; 3 customers,
  8 products, 3 policies, 281 installments, 4 payments, 2 requests, 3 items, 1 staff; `policy_number_seq` 3,
  `transaction_ref_seq` 4. `schema.sql` + `data.sql` alone, twice: 3/3/2/241/3, no staff. Scratch databases dropped.
- New tests: `PremiumPaymentStatusTest` (6), `PremiumTotalsTest` (2), `CustomerPolicyTest` (4: MATURED after
  maturity, ACTIVE on the day, fully paid keeps ACTIVE, first date kept), `FormatsTest.yearsReadLikeTheSpecSample`,
  `PremiumPaymentApiTest` (`seededFullyPaidPolicyStaysActive`, `policyTotalsReconcileAfterALatePayment`: 300750.00 /
  15750.00 / 285000.00, `payingAnInstallmentOtherThanTheLastDoesNotMarkThePolicyFullyPaid`; the last-installment
  test now checks `fullyPaidOn` null then 2026-11-25), `PolicySearchApiTest` (`resultsCarryExactlyTheV2Fields`,
  `premiumPaymentStatusIsWorkedOutFromDatesNotFromTheStoredStatus`; statuses Aarav PARTIALLY_PAID, 3003 OVERDUE,
  3002 FULLY_PAID asserted), `RecommendationApiTest.preferredTermIsStoredAsTextLikeTheSpecSample`.

## QA results
**Gate: CONCERNS (non-blocking), 27 Nov 2026.** Full report: [qa-report-v2.md](../qa/qa-report-v2.md).
- `mvn -q test`: 149 tests, 0 failures. Live: seed matches PDF §5.5 row by row; 3002 ACTIVE + FULLY_PAID; late partial payment on
  HDP-2026-0003 reconciles in lookup and search (300750 / 8500 / 292250, then 300750 / 23250 / 277500); fully paid and the duplicate,
  over-payment and concurrency rules all hold.
- QA2-1 (Low): FR-5 D `Optional` is not in the FR-5 search path; the Java 21 table's FR-5 row cites FR-4 code.
- QA2-2 (Low): "Total Premium Payable" includes penalties in FR-4 A / FR-5 B but not on the dashboard or in the DB column (A-17 vs PDF
  FR-3 A / FR-5 B). Needs product-owner sign-off or a rename.
