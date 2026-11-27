# QA report v2: HealthDekho against the v2 requirements PDF

*BMAD role: QA (test architect). Review date: 27 Nov 2026. Commit under review: `35f8a61` (main, E7 + E8 merged).*
*Only source of truth: `requirements/HealthDekho_Insurance_Management_System.pdf`. The PRD, architecture and dev notes
were read for context but not trusted. Every behaviour below was checked in the code and, where it is runtime
behaviour, on the running dev server (http://localhost:8080, real clock, today = 27 Nov 2026 IST), with curl,
as Aarav, Meera, Rohan and the ops desk.*

## 1. Verdict

**CONCERNS (non-blocking).** Every PDF capability works end to end. The schema and `data.sql` match §5.2, §5.3 and
§5.5 row by row. I found no cross-customer leak, and no money or date error. Duplicate-payment, over-payment,
late-penalty and fully-paid rules hold, including under parallel requests. There are **no High or Medium defects**.
There are two Low findings:

- **QA2-1:** a Java 21 bullet (FR-5 D, `Optional`) is not in the FR-5 code path.
- **QA2-2:** the PRD redefines a term the PDF defines ("Total Premium Payable").

The gate turns PASS when QA2-1 is fixed or waived, and the product owner signs off A-17 (QA2-2) and A-4 (staff role).

## 2. Test runs

| Suite | Command | Result |
|---|---|---|
| Backend | `cd backend && mvn -q test` | **149 tests, 0 failures, 0 errors, 0 skipped** (16 classes) |
| Front end | `cd frontend && npm test` | **53 tests in 12 files, all passed** |

Backend per class: PremiumPaymentApiTest 22, PolicyRegistrationApiTest 18, InstallmentRulesTest 18, PolicySearchApiTest 16,
RecommendationApiTest 14, PolicySearchServiceTest 12, RecommendationServiceTest 12, RuleBasedRankerTest 7,
AuthApiTest 6, PremiumPaymentStatusTest 6, PolicyScheduleTest 5, CustomerPolicyTest 4, InsuranceAdvisorTest 3,
FormatsTest 3, PremiumTotalsTest 2, PolicyRegistrationServiceTest 1. Both counts match the E7/E8 dev notes.

## 3. Live checks: money, dates and totals

| Check | Expected (by hand) | Observed |
|---|---|---|
| Start window (today 27 Nov 2026) | 27 Nov 2026 to 25 Feb 2027 | 2027-02-25 accepted; 2026-11-26 → 422 `START_DATE_OUT_OF_RANGE` "between 27 Nov 2026 and 25 Feb 2027" |
| Maturity at month end, 2027-01-31 + 240 m − 1 d | 2047-01-30 | 2047-01-30 |
| Totals | 1250×240 = 300000; 18000×1 = 18000; 3000×240 = 720000; 6800×10 = 68000 | all match |
| Age at start, DOB 2000-01-10 | 26 on 2026-11-27, 27 on 2027-01-31 | 26 / 27 |
| Aarav HDP-2026-0001 (seed) | paid 1250 + 500 = 1750; remaining 298250 | search: 300000 / 1750 / 298250, PARTIALLY_PAID |
| Meera HDP-2026-0003 before payment | payable 300000, paid 7500, remaining 292500, OVERDUE; #2 `penaltyIfPaidToday` 750 | as expected |
| Late **partial** payment of ₹1,000 on #2 (grace ended 15 Oct) | penalty 10% × 7500 = 750; installment outstanding 8250 − 1000 = 7250; payable 300750, paid 8500, remaining 292250 | receipt TXN-HD-000005: penalty 750.00, outstanding 7250.00; lookup **and** search 300750 / 8500 / 292250 ✓ reconcile |
| Pay ₹7,250.01 on #2 after that | 422 (penalty applied once, A-8) | 422 "₹7,250.00 outstanding" |
| Pay #2 rest (7250) + #3 (7500) in one request | no second penalty; payable 300750, paid 23250, remaining 277500 (= 37 × 7500) | 300750 / 23250 / 277500 in lookup and search ✓; receipt lines ordered by due date |
| Fully paid (Rohan, new HDP-2026-0004, 1 yearly installment) | 10000 → PARTIALLY_PAID, `fullyPaidOn` null; 8000 → FULLY_PAID, `fullyPaidOn` 2026-11-27, status ACTIVE | exactly that; a third payment → 409 `INSTALLMENT_ALREADY_PAID` |
| Seed 3002 | ACTIVE, FULLY_PAID, fully paid on 2026-11-20 | as expected (search and lookup) |

## 4. PDF traceability

Results: **PASS** (meets the PDF), **CONCERNS** (meets it with a caveat), **FAIL**, **N/A** (the PDF text is missing,
per its own "missing from source screenshots" notes).

### §1 Capabilities and §2 Technology / architecture

| PDF item | Result | Evidence |
|---|---|---|
| §1 Customer authentication and secure access | PASS | Login 200 + JWT; wrong password and unknown email both 401 "Invalid email or password."; tampered token → 401 |
| §1 Dashboard of registered policies | PASS | `GET /api/customers/me/policies` per customer; Rohan `[]` |
| §1 Products not already registered | PASS | Aarav's list excludes HD-TERM-001; inactive HD-HEALTH-000 never listed, `GET /api/products/HD-HEALTH-000` → 404 |
| §1 AI recommendations | PASS | `POST /api/recommendations` (rule-based fallback live, as no key is set); Spring AI path covered by `InsuranceAdvisorTest` |
| §1 Registration from listings and recommendations | PASS | Register with `recommendationItemId` 7003 → 201 and item 7003 `selected_for_registration = t` |
| §1 Premium payment management and history | PASS | §3 above; history newest first |
| §1 Policy search | PASS | FR-5 rows below |
| §2.1 Java 21, Boot 3.5.7, Web, Security, Data JPA, PostgreSQL, Spring AI, Bean Validation | PASS | `pom.xml`: parent 3.5.7, `java.version` 21, `spring-ai-starter-model-openai` (BOM 1.1.8), web / validation / data-jpa / security starters |
| §2.1 Exception handling and HTTP status codes | PASS | `GlobalExceptionHandler`: 400 / 401 / 404 / 409 / 422 / 503 problem+json; malformed JSON → 400 "request body could not be read" |
| §2.2 React, function components + hooks, Axios, forms, tables, loading / success / error | PASS | No class components; `api/client.js` uses Axios 1.21.0; Spinner on each request; success Alert after register and pay |
| §2.3 Layers: Controller / Service / Repository / Entity-DTO / AI | PASS | Packages `controller`, `service`, `repository`, `entity`, `dto`, `ai`; controllers import no entity or repository (grep) |
| §2.3 DTOs; entities not exposed | PASS | All 20 DTOs are records; controllers return DTOs only |
| §2.3 Entity relationships and DB constraints | PASS | §5.2 / §5.3 rows below |
| §2.3 Business logic in services | PASS | Rules in `service` and `domain` (`InstallmentRules`, `PolicySchedule`, `PremiumTotals`) |
| §2.3 Environment-specific configuration | PASS | `application-{dev,prod}.yml`, test yml; prod has no secret defaults and no seed (`optional:classpath:prod-data.sql`) |
| §2.3 Transaction management | PASS | `@Transactional` on `register`, `pay`, `recommend`; payment takes a policy row lock (`PremiumPaymentService.java:62`) |
| §2.3 Persist AI recommendations | PASS | Requests 6003–6005 and their items were saved live with age, model name, prompt version |
| §2.3 AI does not replace validations or calculations | PASS | Candidates are filtered in Java before ranking (`RecommendationService.java:114-126`); output is checked against the candidates (`:155-182`); prices come from the DB |

### FR-1 Customer dashboard, discovery, registration

| PDF item | Result | Evidence |
|---|---|---|
| A. React login page | PASS | `LoginPage.jsx`, `LoginPage.test.jsx` |
| A. Spring Security, configured customer credentials | PASS | `AppUserDetailsService` + BCrypt `{bcrypt}` hashes; inactive account → 401 (`GlobalExceptionHandler.java:59`) |
| A. Redirect to dashboard on success | PASS | `AuthContext.jsx:9-10` `homePathFor` CUSTOMER → `/dashboard` |
| A. Error message on invalid credentials | PASS | 401 "Invalid email or password." for both wrong password and unknown email |
| A. Restrict dashboard data to the authenticated customer | PASS | Aarav on Meera's HDP-2026-0003 detail / installments / payments / pay → 404; paying her installment 4339 through his own policy → 422 `INSTALLMENT_NOT_IN_POLICY`; his search for "Meera" → empty |
| B. Dashboard of the logged-in customer's policies | PASS | `PolicyRegistrationService.java:56-62` |
| B. Ten table fields | PASS | `DashboardPage.jsx:14-29`: Policy Identifier, Policy Name, Policy Type, Policy Term, Policy Start Date, Policy Maturity Date, Premium Frequency, Premium Amount, Sum Insured, Policy Status (PDF order) |
| B. Message when no policies | PASS | `DashboardPage.jsx:93-98` |
| C. View available policies | N/A (body missing in PDF) | Behaviour matches §1; `ProductService.java:30-38` |
| D. Spring AI with a configured model, using customer inputs and products | PASS | `InsuranceAdvisor` (ChatClient + `responseEntity`); not run live (no key); fallback live |
| D. Only DB products referenced | PASS | `validate()` drops codes that are not candidates; items stored with FK `product_id` |
| D. Table: Product Code … Suitability, Action | PASS | `RecommendationsPage.jsx:22-40`, the PDF's ten columns in order, plus an extra leading "Rank" column (see §6) |
| D. Register button on every item; pre-populated registration | PASS | `/register/:code?rec=<itemId>`; the form loads from `registration-preview` |
| D. Save recommendations against the customer | PASS | DB rows 6003–6005 / 7004–7007, `customer_id` 1001 |
| E. Java 21 expectations | PASS | See §4.6 |

### FR-3 Policy registration

| PDF item | Result | Evidence |
|---|---|---|
| A. Customer Name / Date of Birth / Contact Information auto-populated | PASS | Preview `customer` block; `RegisterPolicyPage.jsx` labels in PDF order; email and phone under "Contact Information" |
| A. Policy Type / Name / Term / Premium Payment Frequency / Premium Amount / Sum Insured from the product | PASS | Preview `product` block; read-only DetailList |
| A. Start date defaults to today | PASS | Preview without `startDate` → 2026-11-27 |
| A. Maturity date calculated | PASS | 2026-11-27 → 2027-11-26; 2027-01-31 + 240 m → 2047-01-30 |
| A. Total Premium Payable from premium, frequency, term | PASS | 18000.00, 300000.00, 720000.00, 68000.00 |
| B. Mandatory fields validated | PASS | Missing `startDate` / `productCode` → 400 with per-field `errors`; bad date format → 400 "Enter a date as yyyy-mm-dd." |
| B. DOB not in future | PASS | DB `ck_customer_dob_not_future` (`schema.sql:17`); recommendation DOB 2030 → 400 |
| B. Min / max entry age | PASS | Rohan (26) on HD-RET-001 (30–55) → preview `eligible:false`, register 422 `ENTRY_AGE_NOT_ELIGIBLE` |
| B. Start / maturity consistency | PASS | Window check (`PolicyRegistrationService.java:133-140`); `ck_policy_dates` (`schema.sql:90`) |
| B. Premium, sum insured, term positive | PASS | `schema.sql:52-54`, `:91`, `:112` |
| B. Unique policy identifier | PASS | `HDP-<start year>-<seq>` from `policy_number_seq` + `uq_policy_number`; HDP-2026-0004/0005/0008 issued (gaps come from failed race attempts; uniqueness holds) |
| B. Duplicate registration prevented | PASS | Sequential → 409 `DUPLICATE_POLICY`; 5 parallel POSTs → 1×201, 4×409 |
| B. Total premium by frequency and term | PASS | `PolicySchedule.java:15-34` |
| B. Installment schedule generated | PASS | HDP-2026-0005: 10 yearly rows; seed 3001: 240 rows, due / grace dates correct |
| B. Persist policy + schedule with Spring Data JPA | PASS | One `saveAndFlush` in `@Transactional register` (cascade) |
| B. Show identifier + confirmation | PASS | 201 `{policyNumber, message: "Policy HDP-2026-0004 registered successfully."}`; success view "Policy Identifier" |
| B. Validation / DB / business errors handled | PASS | 400 / 422 / 409 / 503 mapping; inactive or unknown product → 422 `PRODUCT_NOT_AVAILABLE` |
| C. Java 21 expectations | PASS | See §4.6 |

### FR-4 Premium payment management

| PDF item | Result | Evidence |
|---|---|---|
| A. React search by policy identifier | PASS | `PolicyLookupForm.jsx:23` "Policy Identifier"; lookup is case-insensitive (`hdp-2026-0003` resolves) |
| A. Nine fields | PASS | `PolicyDetailsCard.jsx:20-37`, exactly the PDF's nine labels in PDF order; extras in a secondary row |
| A. Total Premium Payable / Paid / Remaining | CONCERNS | Reconcile live (§3). The meaning of "Total Premium Payable" differs from the PDF's definition: **QA2-2** |
| B. Schedule table with six fields | PASS | `InstallmentSchedule.jsx:24-51`: Installment Number, Premium Amount (scheduled), Due Date, Grace Period End Date, Payment Status, Payment Selection, first and in PDF order. The UI pages the table 12 rows at a time; every row can be reached |
| B. Status values PAID / PENDING / PARTIALLY_PAID / OVERDUE | PASS | `InstallmentRules.java:18-27`; all four seen live |
| B. Checkbox for eligible pending installments | PASS | Checkbox only when `payable`; #4 (due 15 Mar 2027) → 422 `INSTALLMENT_NOT_PAYABLE` |
| C. Select one or many; capture amount paid | PASS | Two-line payment (#2 + #3) → 201 |
| C. Status updated automatically | PASS | OVERDUE → PAID, PENDING → PARTIALLY_PAID → PAID live |
| C. Payment date and transaction details recorded | PASS | `paymentDate` today, `TXN-HD-%06d`, method |
| C. 10% penalty after the one-month grace period | PASS | 750.00 on #2 (late); none on #3 (on time) or on time within grace (#1 of HDP-2026-0004). Applied once per installment (PRD A-8, a reasonable reading of the PDF) |
| C. Outstanding recalculated | PASS | Installment `outstandingAmount` and policy `remainingPremium` after each payment |
| C. Accurate payment history | PASS | `GET …/payments` lists every transaction with penalty; `PremiumPayment` is `@Immutable` |
| C. No duplicate payment on a scheduled installment | PASS | Already PAID → 409; same installment twice in one request → 422; 8 parallel full payments of Aarav #2 → 1×201, 7×409, one ledger row |
| C. No payment above outstanding | PASS | 8250.01 on 8250.00 → 422; 1e20 → 400 |
| C. Partial payments | PASS | 1000 of 8250; 10000 of 18000 |
| C. Fully paid only when all paid | PASS | HDP-2026-0004 (§3); `fullyPaidOn` stays null after a non-last payment; cover status stays ACTIVE |
| C. (atomicity) | PASS | Valid #3 + invalid #4 in one request → 422; #3 not paid |
| D. Java 21 expectations | PASS | See §4.6 |

### FR-5 Customer policy search

| PDF item | Result | Evidence |
|---|---|---|
| A. Fields Customer Name, Policy Identifier, Date of Birth | PASS | `PolicySearchPage.jsx:131-139`, PDF order and names |
| A. Identifier / Name / Identifier + DOB | PASS | All three work. DOB alone, name + DOB, name + identifier, empty, blank name, 1-character name → 400 `INVALID_SEARCH_CRITERIA` (server) and the same rules on the client (`searchCriteria.js`) |
| B. Eleven result columns | PASS | `PolicySearchPage.jsx:20-61` and the JSON `PolicySearchResult`, exact PDF order (plus `latePenalties` in JSON only) |
| B. Totals / status values | CONCERNS | Reconcile (§3); same QA2-2 caveat on "Total Premium Payable" |
| C. All matching policies | PASS | Staff "iyer" → 0002 and 0003; customers only see their own |
| C. No-match message | PASS | `emptyMessage="No policies match these details."`; wrong DOB → empty page 200 |
| C. Criteria validated before processing | PASS | `SearchMode.of` runs before any query; bad date → 400 per field |
| C. Efficient JPA queries | PASS | Page query with `join fetch` + count + **one** grouped tally for the page (`PolicySearchRepository.java:62-77`, `PolicySearchService.java:71-78`); no per-row query |
| C. Pagination / bounded results | PASS | Size clamped 1–50 (`size=1000` → 50, `size=0` → 1); `page=-1` → 0; `page=abc` → 400; offset > 100000 → 400; `size=1&page=1` returns the second row |
| D. Java 21 expectations | CONCERNS | `Optional` is missing from the FR-5 path: **QA2-1**. The rest: see §4.6 |

### §5.2 Tables, §5.3 ER diagram

| PDF item | Result | Evidence |
|---|---|---|
| customer_policy (visible part): `total_premium_payable`, `status`, `created_at` | PASS | `schema.sql:69-92` |
| premium_installment: columns as named | PASS | `installment_number`, `grace_period_end_date`, `scheduled_amount`, `paid_amount`, `payment_status` (`schema.sql:98-115`) |
| premium_installment: UQ(policy_id, installment_number) | PASS | `schema.sql:109` |
| premium_installment: paid_amount between 0 and scheduled + penalty | PASS | `schema.sql:113`; live DB shows the CHECK |
| premium_payment: columns, `transaction_reference` UQ, `amount_paid > 0`, indexes on installment_id and payment_date | PASS | `schema.sql:122-137` |
| ai_recommendation_request: columns incl. `preferred_term` text, indexes on customer_id and requested_at | PASS | `schema.sql:139-155`; live rows store "20 years" |
| ai_recommendation_item: columns, UQ(request, product) | PASS | `schema.sql:157-170` |
| ai_recommendation_item: product active at generation time | PASS | Enforced in the service (only `findByActiveTrue…` products are candidates); FK covers "must exist". No DB-level active check (see S-4) |
| §5.1 and first rows of §5.2 | N/A (missing in PDF) | customer / insurance_product tables kept; columns match the §5.5 samples |
| §5.3 relationships (customer 1:N policy, product 1:N policy, customer 1:N rec request, policy 1:N installment, installment 1:N payment, request 1:N item, product 1:N item) and UQ on email, product_code, policy_number | PASS | FKs in `schema.sql`; `@ManyToOne` / `@OneToMany` on all seven links; `uq_customer_email` :16, `uq_product_code` :49, `uq_policy_number` :87 |
| FE-1 claim tables not created | PASS | Absent from `schema.sql` (PDF §5.4 says add them only with FE-1) |

### §5.5 Sample data (`data.sql`, compared row by row)

| Table | Result | Notes |
|---|---|---|
| 5.5.1 customer 1001–1003 | PASS | Email, name, DOB, phone, ACTIVE match; placeholders replaced with BCrypt as the PDF instructs |
| 5.5.2 product 201–203 | PASS | Code, name, type, term, frequency, premium, sum insured, active match; `age_range` 18-60 / 18-65 / 21-55 stored as min/max |
| 5.5.3 policy 3001–3002 | PASS | All values match, including 3002 **ACTIVE** and total 300000.00 / 18000.00 |
| 5.5.4 installments 4001–4004 | PASS | Due, grace, scheduled, paid, status match. 3001's other 237 rows are generated by the same rule (consistent with the sample, not shown in it) |
| 5.5.5 payments 5001–5003 | PASS | Refs, dates, amounts, methods match. Note: 5002 is dated 2026-12-20, after today, as the sample gives it (A-7) |
| 5.5.6 rec requests 6001–6002 | PASS | Goals, "20 years" / "1 year", type, age 36 / 26, model name/version, `rec-v1`, timestamps +05:30 |
| 5.5.7 rec items 7001–7003 | PASS | Product, rank, reason, suitability, selected flags |

The PDF names the columns differently in §5.2 (`installment_number`, `grace_period_end_date`, `total_premium_payable`) and in
the §5.5 headers (`installment_no`, `grace_end_date`, `total_premium`). The code follows §5.2. That is the right call and
needs no action.

### 4.6 Java 21 expectations, checked in the relevant code path

| Bullet | Result | Where (in that FR's path) |
|---|---|---|
| FR-1 E records | PASS | `PolicySummaryResponse`, `ProductResponse`, `RecommendationRequest/Response` |
| FR-1 E LocalDate / Java Time | PASS | `Ages.java:16` `Period.between`; `RecommendationService.java:88`; `CustomerPolicy.effectiveStatus` |
| FR-1 E Optional for repository lookups | PASS | `ProductService.java:42-45`; `RecommendationService.java:86` |
| FR-1 E Streams filter available products / exclude held | PASS | `ProductService.java:31-37`; `RecommendationService.java:118-125` |
| FR-1 E Collectors | PASS | `toSet` `ProductService.java:33`, `RecommendationService.java:120`; `toMap` `:160` |
| FR-1 E instanceof pattern matching | PASS | `RecommendationService.java:186` (`instanceof ResourceAccessException io`); `AuthenticatedUserArgumentResolver.java:24` (`instanceof Jwt jwt`, used by every dashboard call) |
| FR-3 C records | PASS | `RegisterPolicyRequest`, `RegistrationPreviewResponse`, `RegistrationResultResponse`, `PolicySchedule` |
| FR-3 C LocalDate | PASS | `PolicySchedule.java:21-31`; `PolicyRegistrationService.java:133-140` |
| FR-3 C BigDecimal | PASS | `PolicySchedule` total / amount; `Money` |
| FR-3 C Optional | PASS | `PolicyRegistrationService.java:95, 126-131, 142-160` |
| FR-3 C Streams | PASS | `PolicySchedule.java:21` `IntStream.rangeClosed` |
| FR-4 D records | PASS | `PaymentRequest`, `PaymentItem`, `PaymentReceiptResponse(.Line)` |
| FR-4 D YearMonth | PASS | `PremiumInstallment.java:83-85` → `InstallmentResponse.premiumPeriod`, `PaymentHistoryResponse.premiumPeriod` |
| FR-4 D BigDecimal | PASS | `InstallmentRules.penaltyIfPaidOn`, `PremiumTotals`, `Money` |
| FR-4 D switch for payment status | PASS | `InstallmentRules.java:31` (called by `PremiumPaymentService.check`), `PremiumPaymentStatus.java:23, 32` (called by `pay`) |
| FR-4 D Streams | PASS | `PremiumPaymentService.java:64-76, 143-152` (`toMap`, `groupingBy` + `counting`) |
| FR-4 D Transactions | PASS | `PremiumPaymentService.java:56` |
| FR-5 D records | PASS | `PolicySearchResult`, `PageResponse`, `InstallmentTally`, sealed `SearchMode` records |
| FR-5 D Streams entity → DTO | PASS | `PageResponse.java:11-14` with `PolicySearchService.toResult` |
| FR-5 D Collectors.groupingBy | PASS | `PolicySearchService.java:77` |
| FR-5 D Optional for single-record lookups | **FAIL (Low)** | Not in `PolicySearchService` or `PolicySearchRepository` (grep: no `Optional`). The E7 table points to `PolicyLookupService.java:105`, which is FR-4 A, not FR-5 → **QA2-1** |
| FR-5 D Pagination | PASS | `PolicySearchService.java:51-56`, `Page` queries |

### 4.7 UI labels vs PDF field names

| Screen | Result | Notes |
|---|---|---|
| FR-1 B dashboard table | PASS | 10/10 names, PDF order |
| FR-1 D recommendations table | PASS | 10/10 names, PDF order; extra "Rank" first |
| FR-3 A registration | PASS | 12/12 names (Customer Name … Total Premium Payable); extras Product Code, Entry Age, Number of Installments, Age at Start Date |
| FR-4 A policy details | PASS | 9/9 names, PDF order |
| FR-4 B schedule | PASS | 6/6 names, PDF order, then the extra columns |
| FR-5 A search form | PASS | 3/3 names, PDF order |
| FR-5 B results | PASS | 11/11 names, PDF order, nothing extra |

Every response field the front end reads exists in the v2 API JSON (grep of `src/` against live responses). No v1
names are left (`installmentNo`, `graceEndDate`, `totalPremium`).

## 5. Defects

| Id | Sev | PDF section | Summary |
|---|---|---|---|
| QA2-1 | Low | FR-5 D "Optional for single-record lookups" | Not used anywhere in the FR-5 code path |
| QA2-2 | Low | FR-3 A / FR-5 B definition of "Total Premium Payable" (PRD A-17 disagrees) | The API returns two different values under the same name for the same policy |

**QA2-1, Low. FR-5 D: `Optional` missing from the search path.**
- Repro: `grep -n Optional backend/src/main/java/com/healthdekho/insurance/service/PolicySearchService.java backend/src/main/java/com/healthdekho/insurance/repository/PolicySearchRepository.java` returns nothing.
- Expected: FR-5 D asks for `Optional` for single-record lookups in the FR-5 search. Search by Policy Identifier (and identifier + DOB) is a lookup on a unique key, so it is the natural place.
- Actual: both modes run a `Page<CustomerPolicy>` query. The E7 dev-notes table cites `PolicyLookupService.java:105`, which serves FR-4 A (`GET /api/policies/{n}`), not FR-5.
- Behaviour is correct. This is a spec-checklist gap, plus an inaccurate dev-notes row.
- Fix: in the identifier modes, look up `Optional<CustomerPolicy>` by policy number (with the owner filter) and wrap it into a one-row page. Then correct the E7 table.

**QA2-2, Low. "Total Premium Payable" redefined (PRD A-17 vs PDF).**
- Repro (state after §3): `GET /api/customers/me/policies` as Meera → HDP-2026-0003 `totalPremiumPayable: 300000.00`. `GET /api/policies/HDP-2026-0003` and `GET /api/policies/search?customerName=iyer` (staff) → `totalPremiumPayable: 300750.00`. DB `customer_policy.total_premium_payable` = 300000.00.
- Expected (PDF): FR-3 A defines Total Premium Payable as "calculated based on premium amount, frequency and policy term", and FR-5 B calls it "Total policy premium". The PDF never adds late penalties to it.
- Actual: FR-4 A and FR-5 B show contract + late penalties. The dashboard and the DB column show the contract figure. So one JSON name carries two values.
- Context: the PDF's own three definitions (payable = policy premium, paid = "cumulative paid amount", remaining = "outstanding premium amount") cannot all hold at once once a penalty exists. The team chose to bend "payable" so the totals reconcile.
- No screen currently shows both numbers side by side; the dashboard UI does not display the field. That is why this is Low.
- Fix: one of the following.
  - (a) Product owner explicitly accepts A-17. Then rename the dashboard / registration field `contractPremium` so the name means one thing.
  - (b) Follow the PDF: payable = contract total, show "Late penalties" as its own line, and use remaining = payable + penalties − paid.

No other defects found. The E7/E8 claims I re-checked all held: v2 field names, seed split, ACTIVE + `fully_paid_on`, the
fully-paid rule, the grouped search query, and the test counts.

## 6. Implemented but not asked for by the PDF

| Addition | Conflicts with the PDF? | Assessment |
|---|---|---|
| Staff role (`staff_user` table, `ops.desk@example.com`, STAFF can look up / pay / search any policy) | No direct conflict. FR-1 A describes customer login only, and the §5.3 ER diagram ("current implementation scope") has no staff table | Unratified scope (PRD A-4 still "product owner to confirm"). Harmless at runtime: STAFF gets 403 on dashboard, products, recommendations and registration |
| `demo-data.sql` (products 204–208, inactive 208, policy 3003 with overdue #2, payment 5004, staff 9001) | No. Loaded in dev/test only, header says it is not the PDF sample, prod loads none | Harmless; needed to demo the penalty and inactive-product rules |
| Policy status MATURED (derived), `fully_paid_on` column, premium payment status FULLY_PAID / OVERDUE / … | No; the PDF lists "Policy Status" and "Premium Payment Status" without values | Harmless |
| Start date may be moved up to today + 90 days (A-10) | No; the PDF says only "default to current date" | Harmless |
| Only installments due within one month are payable (A-9) | No; the PDF says "eligible pending installments" | Harmless; it defines "eligible" |
| Penalty applied once per installment (A-8) | No; the PDF is silent on repeat late payments | Reasonable reading |
| Extra columns: Rank (recommendations); Premium Period, Late Penalty, Amount Paid, Outstanding, Amount to Pay (schedule); secondary row on policy details; Product Code, Entry Age, Installments, Age at Start (registration) | No; all PDF fields are present and first | Harmless |
| `ai_recommendation_request.generation_source`, `prompt_text`; `insurance_product.description`; `premium_installment.penalty_amount`, `version` | No; extra columns beside the PDF's | Harmless (`penalty_amount` is implied by the §5.2 CHECK) |
| Rule-based fallback when the model is unavailable | No; the PDF says AI must not replace deterministic rules, and the fallback is labelled | Harmless |
| Name search needs ≥ 2 characters; page offset cap | No; this is "validate the search criteria" | Harmless |

## 7. Suggestions (optional, not defects)

- **S-1** Premium payment status reads PENDING when everything due so far is paid (Aarav after paying #2, Meera after
  #2 and #3). PENDING then means "future premiums remain". A label such as "Up to date" for that case would read better
  for staff. The PDF does not define the values.
- **S-2** Server messages say "policy number" ("Search by policy number…", "Policy HDP-… was not found.") while the UI and
  PDF say "Policy Identifier". The client usually shows its own text, so this is rarely visible.
- **S-3** The policy term is read from the product master, while premium, frequency and sum insured are frozen on the
  policy (`CustomerPolicy.getTermMonths`). This is consistent with §5.5.3 (no term column). If products ever become
  editable, freeze the term too.
- **S-4** `premium_payment` is immutable only in JPA (`@Immutable`), and "product active at generation time" only in the
  service. A DB trigger or REVOKE UPDATE would make both hold at the database level.
- **S-5** The sample payment 5002 (20 Dec 2026) is in the future relative to the real clock, so it sorts above today's
  payments in Aarav's history. This is expected from the sample. Mention it in the demo script.

## 8. Data created on the dev DB during this review (for the reset)

- Policies HDP-2026-0004 (Rohan, HD-HEALTH-001, fully paid), HDP-2026-0005 (Rohan, HD-TERM-002) and HDP-2026-0008 (Rohan,
  HD-CRIT-001). Numbers 0006–0007 were consumed by the parallel-registration race test.
- Payments TXN-HD-000005 … 000012: Meera 3003 #2 late partial + rest, #3; Rohan 0004 and 0005; Aarav 3001 #2.
- Recommendation requests 6003–6005 / items 7004–7007. Item 7003 marked `selected_for_registration`.


## Resolution (lead developer, 27 Nov 2026)

| Id | Fix |
|---|---|
| QA2-1 | Policy-identifier searches (identifier alone, identifier + DOB) are now single-record lookups: `PolicySearchRepository.findOneByPolicyNumber` / `findOneByPolicyNumberAndDateOfBirth` return `Optional`, turned into a one-row page in `PolicySearchService.asPage`. |
| QA2-2 | Took option (b), following the PDF: Total Premium Payable is the contract total on every screen and in every API; late penalties are a separate figure (`latePenalties`, UI note "plus ₹750.00 late penalties"); remaining = payable + penalties − paid. PRD AC-4A.2 / A-17 and architecture §5 updated; `PremiumTotals` and its tests changed. |
| S-2 | Search validation message now says "Policy Identifier", "Customer Name", "Date of Birth". |

Not taken: S-1 (PENDING keeps the PDF's installment vocabulary at policy level), S-3/S-4 (outside the PDF), S-5 (sample payment date is the PDF's own data).
A-4 (staff role) remains open for the product owner.

Backend 149 tests, front end 53 tests, all green after the fixes. **Gate: PASS** (A-4 pending product-owner confirmation).
