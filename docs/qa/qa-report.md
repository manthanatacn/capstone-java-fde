# QA report: HealthDekho Insurance Portal

*BMAD role: QA (test architect). Review date: 25 Nov 2026. Commit under review: `2243438` on `main`.*
*Method: read every AC against the code, ran both test suites, and exercised the running dev server
(http://localhost:8080, real clock, today = 2026-11-25 IST) with curl as all four demo users.*

## 1. Verdict

**CONCERNS.** No High defects. Every FR works end to end. Money and date maths match architecture §5 by hand.
No cross-customer data leaks were found, and the concurrency guarantees (duplicate registration race, optimistic
lock on payments) hold under real parallel requests. There is one Medium defect (QA-1: recommendations can offer
a product the customer then cannot register) and nine Low ones, all listed in §5. Each Low has a reason it
should not block the demo. The gate turns PASS once QA-1 is fixed or accepted by the PM.

## 2. Test runs

| Suite | Command | Result |
|---|---|---|
| Backend | `cd backend && mvn -q test` | **119 tests, 0 failures, 0 errors, 0 skipped** (12 classes). Re-run on `2243438`, same result. |
| Front end | `cd frontend && npm test` | **45 tests in 10 files, all passed** |

Backend per class: InstallmentRulesTest 18, PolicyRegistrationApiTest 17, PremiumPaymentApiTest 16,
PolicySearchApiTest 12, PolicySearchServiceTest 12, RecommendationServiceTest 12, RecommendationApiTest 10,
RuleBasedRankerTest 7, AuthApiTest 6, PolicyScheduleTest 5, InsuranceAdvisorTest 3,
PolicyRegistrationServiceTest 1.

## 3. Hand-checked maths (architecture §5)

| Check | Expected (by hand) | Observed |
|---|---|---|
| Maturity, 2026-11-20 + 240 m − 1 d | 2046-11-19 | 2046-11-19 (HDP-2026-0001) |
| Maturity, 2027-01-31 + 240 m − 1 d (month end) | 2047-01-30 | 2047-01-30 (preview HD-TERM-001) |
| Maturity, 2027-01-15 + 120 m − 1 d | 2037-01-14 | 2037-01-14 (HDP-2027-0005) |
| Totals | 1250×240 = 300000.00; 6800×10 = 68000.00; 9500×5 = 47500.00; 12000×30 = 360000.00; 7500×40 = 300000.00 | all match |
| Due/grace dates, yearly from 2027-01-15 | due 2027-01-15 … 2036-01-15, grace +1 month | 10 rows as expected (DB) |
| Start window, today + 90 days | 2026-11-25 + 90 = 2027-02-23 | 02-23 accepted, 02-24 → 422, 11-24 → 422 |
| Age at start date, DOB 2000-01-10, start 2027-01-10 | 27 (birthday counts) | 27 |
| Penalty 10% of 7500.00 | 750.00 (receipt 8250.00) | TXN-HD-000005: 8250.00, penalty 750.00 |
| Penalty rounding | 1234.55 → 123.455 → 123.46 HALF_UP (scale-4 divide is exact for 2-dp inputs, so no double rounding) | unit test + code review agree |
| Remaining premium HDP-2026-0001 | 300000 − (1250 + 500 + 100 + 100) = 298050.00 | 298050.00 (search and lookup) |
| Remaining premium HDP-2026-0003 | 300000 − 7500 − 7500 = 285000.00 (paid penalty not counted) | 285000.00 |
| FULLY_PAID | 1-installment policy: 10000 → ACTIVE, then 8000 → FULLY_PAID | ACTIVE, then FULLY_PAID; next payment 409 |

## 4. AC traceability

| AC | Result | Evidence |
|---|---|---|
| AC-1.1 | PASS | Login returns token + profile; `homePathFor` sends STAFF → `/search`, CUSTOMER → `/dashboard` |
| AC-1.2 | PASS | Wrong password and unknown email both 401 "Invalid email or password." |
| AC-1.3 | PASS | All hashes `{bcrypt}$2a$10…`; `DaoAuthenticationProvider` + delegating encoder |
| AC-1.4 | PASS | Aarav on Meera's policy/installments/payments → 404; her installment id in his payment → 422 not-in-policy; search → empty; another customer's rec item → 422 |
| AC-1.5 | CONCERNS | Guard + 401 redirect work; notice missing after a reload with an expired session (QA-8) |
| AC-1.6 | PASS | `/customers/me/policies` and DashboardPage columns match |
| AC-1.7 | PASS | Empty state links to Available products (DashboardPage) |
| AC-1.8 | PASS | Rohan's list excludes held + inactive; columns + Register present |
| AC-1.9 | PASS | HD-HEALTH-000 absent from list; `GET /products/HD-HEALTH-000` 404; register 422 |
| AC-1.10 | PASS | Register links to `/register/:code`, form pre-filled from preview |
| AC-2.1 | PASS | DOB defaults to profile in the UI; `{}` and blank goals → 400 |
| AC-2.2 | PASS | `Ages.ageOn` with app clock; ages 26/41/46/0/126 returned as expected |
| AC-2.3 | CONCERNS | Filtering is deterministic and runs before the model, but uses the DOB typed into the form while registration uses the profile DOB (QA-1) |
| AC-2.4 | PASS | ChatClient + structured output; covered by InsuranceAdvisorTest. Not run live (no key configured) |
| AC-2.5 | PASS | `validate()` drops unknown/duplicate codes, caps at 3; prompt-injection goals returned only real candidates |
| AC-2.6 | PASS | Columns + Register `?rec=<itemId>` (see QA-1 for the dead-end case) |
| AC-2.7 | CONCERNS | Request + items saved with age, source, model name, prompt version. RULE_BASED rows have `model_version` and `prompt_text` null. This is a documented dev decision, not a defect, but the PM needs to sign it off |
| AC-2.8 | PASS | No key → RULE_BASED, notice shown, row saved RULE_BASED, no error to the customer; UI banner |
| AC-2.9 | PASS | DOB today / 1900 → `items: []` + explanatory notice; no advisor call (code + test) |
| AC-2.10 | PASS | Registered HD-CRIT-001 with item 7012 → `selected_for_registration = t` |
| AC-3.1 | PASS | Preview `customer` block; read-only DetailList |
| AC-3.2 | PASS | Preview `product` block; read-only DetailList |
| AC-3.3 | PASS | Start date defaults to today, min/max = today..+90 |
| AC-3.4 | PASS | Server preview; re-fetched on start-date change (useEffect) |
| AC-3.5 | PASS | Client date checks; server `@NotBlank/@NotNull` → 400 with `errors` (QA-4 for unreadable values) |
| AC-3.6 | PASS | DB CHECK; DOB comes only from the profile on registration; future DOB → 400 on recommendations |
| AC-3.7 | PASS | Rohan (26) on HD-RET-001 (30–55) → 422 ENTRY_AGE_NOT_ELIGIBLE; boundary inclusive |
| AC-3.8 | PASS | Yesterday 422, today OK, +90 OK, +91 422 |
| AC-3.9 | PASS | DB CHECKs on product/policy/installment amounts and term |
| AC-3.10 | PASS | Sequential duplicate → 409; 5 parallel POSTs → 1×201, 4×409 DUPLICATE_POLICY |
| AC-3.11 | PASS | `HDP-2027-0005` (start year 2027), `HDP-2026-0010`; from `policy_number_seq` |
| AC-3.12 | PASS | See §3 |
| AC-3.13 | PASS | Policy + installments saved by one `saveAndFlush` in `@Transactional register`; failed race attempts left no rows |
| AC-3.14 | PASS | Success view with number; server message shown; `startDate` error next to field |
| AC-4.1 | PASS | All listed fields in `PolicyDetail` and PolicyDetailsCard |
| AC-4.2 | PASS | Other customer → 404 (same text as unknown); staff opens any |
| AC-4.3 | PASS | InstallmentSchedule columns + checkbox only on `payable` rows |
| AC-4.4 | PASS | `due.plusMonths(1)`; DB rows checked |
| AC-4.5 | PASS | One `InstallmentRules.resolveStatus` used on read and write |
| AC-4.6 | PASS | Aarav #2 (due 12-20) payable; #3 (due 01-20) → 422 INSTALLMENT_NOT_PAYABLE |
| AC-4.7 | PASS | Default amount = outstanding + penalty preview, editable, method select |
| AC-4.8 | PASS | `penaltyIfPaidToday` shown in table and confirm step; 750.00 recorded on payment and installment |
| AC-4.9 | PASS | `TXN-HD-%06d`, payment date = today, `@Immutable` entity |
| AC-4.10 | PASS | 0 / −5 / 3 dp → 400; over outstanding → 422; PAID → 409; not payable → 422; repeated → 422; other policy → 422 |
| AC-4.11 | PASS | 8 parallel payments on one installment → 1×201, 7×409 CONCURRENT_UPDATE; ledger consistent |
| AC-4.12 | PASS | Valid + invalid line → 422 and the valid line was not applied |
| AC-4.13 | CONCERNS | Works sequentially; FULLY_PAID can be missed under a specific race (QA-3) |
| AC-4.14 | PASS | Receipt (refs, amounts, penalties) + history newest first |
| AC-5.1 | PASS | Three fields on PolicySearchPage |
| AC-5.2 | PASS | All valid and invalid combinations checked live (DOB-only, name+DOB, name+number, empty, 1-char name → 400); client blocks them too |
| AC-5.3 | PASS | All columns incl. remaining premium |
| AC-5.4 | PASS | 298050.00 / 285000.00 / 0.00 |
| AC-5.5 | PASS | Empty page 200; UI "No policies match these details." |
| AC-5.6 | CONCERNS | Default 10, cap 50, size 0 → 1 work; very large page → 503 (QA-2) |
| AC-5.7 | PASS | Customer queries carry `customerId`; Aarav gets nothing of Meera's |

| NFR | Result | Evidence |
|---|---|---|
| NFR-1 | PASS | Controllers return records only; entities stay in services |
| NFR-2 | PASS | `Money` scale 2 HALF_UP, NUMERIC(19,2), JSON `1250.00` |
| NFR-3 | PASS | `LocalDate`/`YearMonth`; every `now()` takes the injected Asia/Kolkata `Clock` (grep clean) |
| NFR-4 | PASS | `register`, `pay`, `recommend` are single `@Transactional` methods |
| NFR-5 | CONCERNS | problem+json everywhere in the API; unreadable values give generic or technical text (QA-4, QA-5) |
| NFR-6 | CONCERNS | dev/test/prod profiles exist; prod still inherits DB defaults (QA-7) |
| NFR-7 | PASS | Eligibility, prices and totals are Java; model output validated and saved |
| NFR-8 | PASS | Spinner/disabled buttons on every request; success alert after register and pay |
| NFR-9 | CONCERNS | UI formatting correct (`en-IN` INR, `20 Nov 2026`); server messages shown verbatim use ISO dates and plain numbers (QA-9) |

**Java 21 / spec checklist:** records (all DTOs, `PolicySchedule`, search modes), `LocalDate`, `BigDecimal`,
`Optional` (lookups, ownership filter), Streams, `YearMonth` (`premiumPeriod`), switch expressions
(`InstallmentRules.isPayable`, `AuthService`, pattern-matching switch over a sealed `SearchMode`),
`Collectors.groupingBy` (repeated-installment check), pagination (`PageRequest`/`PageResponse`),
`@Transactional`. All present.

**N+1 check:** none found. Every list endpoint uses an entity graph or `join fetch`: my policies, history,
search. Remaining premium comes from one grouped query per page, and the installment schedule is one query.
This is from reading the code. `dev-server.log` has no SQL logging, so it is not confirmed from SQL.

## 5. Defects

| Id | Sev | Breaks | Description | Reproduction | Expected | Actual |
|---|---|---|---|---|---|---|
| QA-1 | Medium | AC-2.3 / AC-2.6, E3 story ("only from products I'm eligible for") | Recommendation eligibility uses the DOB typed into the form (`RecommendationService.java:85`), registration uses the profile DOB at the start date (`PolicyRegistrationService.java:90`). A customer who edits the DOB is offered products they can't register, and the Register button leads to a form that can't be submitted. (A smaller variant exists too: age today vs age at a start date up to 90 days later.) | As Rohan (profile age 26): `POST /api/recommendations {"dateOfBirth":"1980-05-05"}` → age 46, item for HD-RET-001 (30–55), e.g. itemId 7027. Then `POST /api/policies {"productCode":"HD-RET-001","startDate":"2026-12-01","recommendationItemId":7027}` | Only products the customer can register are recommended, or the DOB field is read-only / the conflict is resolved by the PM | 422 `ENTRY_AGE_NOT_ELIGIBLE` "Customer age 26 is outside the entry age 30–55" |
| QA-2 | Low | AC-5.6, §6 (400 for malformed input) | A page number whose offset exceeds `Integer.MAX_VALUE` becomes `InvalidDataAccessApiUsageException` → 503, and a full stack trace is logged per request (any signed-in user can trigger it). `PolicySearchService.java:45` | `curl -H "Authorization: Bearer $STAFF" 'localhost:8080/api/policies/search?customerName=ar&page=50000000&size=50'` (also `?policyNumber=HDP-2026-0001&page=2147483647` as a customer) | 400 (or empty page) | 503 "The service is temporarily unavailable"; ERROR "Page offset exceeds Integer.MAX_VALUE" in log |
| QA-3 | Low | AC-4.13 | FULLY_PAID can be missed. If two requests at the same time pay the last two *different* unpaid installments, each transaction still sees the other's installment as unpaid. `customer_policy` has no `@Version`, so neither sets FULLY_PAID (`PremiumPaymentService.java:72`), and nothing recomputes policy status later. Not reproduced live: it needs a policy with exactly two payable installments left | Code path: two parallel `POST /payments` on installments A and B of the same policy, where A and B are the only non-PAID rows | Policy becomes FULLY_PAID | Stays ACTIVE with every installment PAID |
| QA-4 | Low | NFR-5, AC-3.14 | Unreadable values return a generic 400 "Failed to read request" with no `errors` map, so neither the UI nor the caller can tell which field is wrong. A bad date on search returns raw Spring conversion text | `POST /api/policies {"productCode":"HD-TERM-002","startDate":"01-12-2026"}`; `POST /api/policies/HDP-2026-0001/payments {"paymentMethod":"BITCOIN",...}`; `POST /api/recommendations {"policyType":"CAR"}`; `GET /api/policies/search?policyNumber=HDP-2026-0001&dateOfBirth=15-04-1990` | 400 with `errors.<field>` and a human message | `detail: "Failed to read request"`, no `errors`; search: `errors.dateOfBirth = "Failed to convert value of type 'java.lang.String' to required type 'java.time.LocalDate'; …"` |
| QA-5 | Low | NFR-5 | `@Digits(integer=17, fraction=2)` uses one message for both limits, so a too-large amount says "at most 2 decimals". `PaymentItem.java:13` | `POST /api/policies/HDP-2026-0001/payments {"paymentMethod":"UPI","items":[{"installmentId":4002,"amount":1e20}]}` (Aarav) | Message about the amount being too large | `errors["items[0].amount"] = "Amount can have at most 2 decimals"` |
| QA-6 | Low | §6 validation (`preferredTermYears` 1–40 integer) | Jackson float-to-int coercion silently truncates a fractional term | `POST /api/recommendations {"preferredTermYears":2.7}` (Rohan) | 400 | 200; request 6011 stored `preferred_term_years = 2` |
| QA-7 | Low | NFR-6 ("prod: no defaults") | `application.yml:8-10` gives `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` defaults (localhost, `healthdekho/healthdekho`), and `application-prod.yml` does not override them, so prod starts against a local DB with default credentials instead of failing fast | From config, not executed: start with `SPRING_PROFILES_ACTIVE=prod JWT_SECRET=x` and no DB vars | Startup fails: DB settings missing | Uses the localhost defaults |
| QA-8 | Low | AC-1.5 | When an expired session is found on page load, `loadSession` drops it silently (`session.js:8`) and `RequireAuth` redirects with only `{from}` (`RequireAuth.jsx:9`), so the login page shows no "session expired" notice. The notice only appears when an API call gets a 401 | From code, not run in a browser: sign in, set `expiresAt` in `sessionStorage['healthdekho.session']` to a past time (or wait 2 h), reload `/dashboard` | Login page with the expiry notice | Login page without a notice |
| QA-9 | Low | NFR-9 | Business messages from the server embed ISO dates and unformatted amounts, and the UI shows them verbatim in ErrorAlert/field errors. Client-side checks hide most of them, so they surface only in edge cases (stale page, race) | e.g. `POST /api/policies` with startDate 2026-11-24 → "Start date must be between 2026-11-25 and 2027-02-23."; pay 750.01 → "Installment 2 has 650.00 outstanding; 750.01 is more than that."; not payable → "…due on 2027-01-20…" | `25 Nov 2026`, `₹650.00` | ISO dates, plain numbers |
| QA-10 | Low | AC-3.3 / NFR-3 (one "today") | The front end takes "today" from the browser's local clock and time zone (`utils/dates.js:9`), the server from Asia/Kolkata. Outside IST near midnight the default start date is a day off: the first preview gets a 422, and the page then shows only the error and a back link (`RegisterPolicyPage.jsx:98`, no date field because `preview` is null) | From code, not run in a browser: browser in a UTC−x zone at 00:30 IST, open `/register/HD-TERM-002` | Defaults to the server's today, or the date can still be changed | 422 START_DATE_OUT_OF_RANGE, no way to pick a date |

## 6. Suggestions (optional, not defects)

1. `GET /api/policies/{number}` is case-sensitive (`hdp-2026-0001` → 404) while search upper-cases. The UI
   upper-cases in the lookup form, so only hand-typed `?policy=` URLs are affected. Upper-case in the service.
2. `PolicyRegistrationService` maps *any* `DataIntegrityViolationException` on save to `DUPLICATE_POLICY`.
   Check the constraint name (`uq_policy_customer_product`) so other violations don't show "You already hold…".
3. GET policy/installments write status changes (ADR-5). A GET that refreshes at the same moment as a payment
   can make the payment fail with 409 CONCURRENT_UPDATE. That is rare (once per row per day) but worth a
   retry-on-409 in the UI or a bulk `update … where status <> new`.
4. The model call runs inside the recommendation transaction (dev already flagged this). Move the call outside
   and save afterwards when a real key is configured.
5. `HDP-%04d` grows to 5 digits after 9999 and the sequence is global, not per year. That is fine, but write it
   down in the PRD.
6. A token stays valid for up to 2 h after a customer is set INACTIVE. Accept this, or check status in the
   argument resolver.
7. An encoded slash in a path (`/api/policies/HDP%2F2026`) is rejected by Tomcat with an HTML 400, not
   problem+json. This is cosmetic.
8. Docs: E5 dev notes still say `%`/`_` are not escaped. Commit `c234ad7` changed that, and the
   repository comment and the new test are correct. E5 test count is now 12 API tests, not 11.

## 7. Data created on the dev DB during this review (for the reset)

- Rohan: policies `HDP-2027-0005` (HD-TERM-002) and `HDP-2026-0010` (HD-CRIT-001, from rec item 7012).
  Policy-number values 6–9 were used up by the race test.
- Payments: HDP-2026-0001 #2 two × ₹100 (TXN-HD-000006/000007); HDP-2026-0004 ₹10,000 + ₹8,000
  (TXN-HD-000008/000009, policy now FULLY_PAID).
- Recommendation requests 6005–6013 with their items.
- Present before this review started (not created by QA): HDP-2026-0004 for Rohan, and Meera's HDP-2026-0003 #2
  payment TXN-HD-000005, so the overdue/penalty demo step is already used up on the dev DB.

## 7. Resolution (lead developer, 25 Nov 2026)

All ten defects fixed on `main`, each with a regression test unless noted. Backend now **130 tests**, front end **47 tests**, all green.

| Id | Fix |
|---|---|
| QA-1 | Candidates must accept both the age from the typed date of birth and the profile age that registration checks (`RecommendationService.candidatesFor`). Test: `productsTheProfileAgeCannotRegisterAreNotSuggestedEvenForAnOlderTypedDateOfBirth`. PRD A-14 added. |
| QA-2 | Search rejects offsets above 100,000 rows with 400 `INVALID_SEARCH_CRITERIA`. Test added. |
| QA-3 | `PremiumPaymentService.pay` takes a row lock on the policy (`CustomerPolicyRepository.lockForPayment`, `SELECT … FOR UPDATE`), so payments on one policy run one at a time. Not covered by an automated test (needs two committed transactions). |
| QA-4 | `GlobalExceptionHandler` maps unreadable JSON values and query-parameter type errors to `errors.<field>` with a readable message (date format, allowed enum values, whole number). Tests for start date, payment method, policy type, search DOB. |
| QA-5 | `@Digits(integer = 12, fraction = 2)` with a message that states both limits. Test added. |
| QA-6 | `spring.jackson.deserialization.accept-float-as-int: false`; 2.7 years is a 400. Test added. |
| QA-7 | Database defaults moved from `application.yml` into `application-dev.yml`; prod requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. |
| QA-8 | An expired session found on reload sets a flag that the login page reads, so the "session expired" notice shows. |
| QA-9 | Server messages format dates as `25 Nov 2026` and money as `₹2,85,000.00` (`domain.Formats`, unit-tested). |
| QA-10 | The browser computes "today" in Asia/Kolkata, matching the server. Unit test with a time just after midnight IST. |

Suggestions taken: policy lookup by number is now case-insensitive and trimmed; only the
`uq_policy_customer_product` violation is reported as `DUPLICATE_POLICY` (other integrity errors keep their generic 409).

AC-2.7 (PM decision): rule-based rows store `prompt_text` and `model_version` as null because no prompt or
model was involved; `model_name = rule-based` and `prompt_version = rules-v1` identify the path. Accepted.

**Gate after fixes: PASS** for E2–E6.
