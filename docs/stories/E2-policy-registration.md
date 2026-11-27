# E2 Dashboard, product discovery and policy registration

**Status:** Done · **Owner:** Dev agent "policy" · **PRD:** FR-1 (AC-1.4, 1.6–1.10), FR-3 (AC-3.1–3.14), AC-2.10

## Stories
- **E2.1** As a customer, I see my policies and the products I can still buy.
- **E2.2** As a customer, I register a product through a pre-filled form and get a policy number and
  premium schedule.

## Tasks
1. `domain/PolicySchedule` (pure): maturity date, installment count, total premium, list of
   `(installmentNo, dueDate, graceEndDate, amount)`. Formulas in architecture §5.
2. `dto/policy/*` records: `PolicySummaryResponse`, `ProductResponse`, `RegistrationPreviewResponse`
   (with nested `CustomerDetails`), `RegisterPolicyRequest`, `RegistrationResultResponse`.
3. `service/ProductService` (available products, single product) and
   `service/PolicyRegistrationService` (dashboard list, preview, register).
   - register is one `@Transactional` method: lock-free duplicate pre-check
     (`existsByCustomerIdAndProductId`) **and** catch `DataIntegrityViolationException` on flush for the
     race → `ConflictException("DUPLICATE_POLICY", ...)`.
   - Policy number `HDP-%d-%04d` (start year, `nextPolicyNumberSequence()`).
   - If `recommendationItemId` is given: it must belong to this customer and this product, else 422
     `INVALID_RECOMMENDATION_ITEM`; mark it selected.
4. Controllers: `CustomerPolicyController` (`/api/customers/me/policies`), `ProductController`
   (`/api/products/...`), `PolicyRegistrationController` (`GET /api/policies/registration-preview`,
   `POST /api/policies`).
5. Use `Optional` for lookups, streams for mapping, records for DTOs.

## Files you own
`domain/PolicySchedule.java`, `dto/policy/**`, `service/ProductService.java`,
`service/PolicyRegistrationService.java`, `controller/CustomerPolicyController.java`,
`controller/ProductController.java`, `controller/PolicyRegistrationController.java`,
and the matching tests. You may **add** methods to `CustomerPolicyRepository` and
`InsuranceProductRepository`.

## Do not touch
Anything under payments/search (`PolicyDetail*`, `Installment*`, `Payment*`, `PolicySearch*`) or `ai/`.

## Tests required
- `PolicyScheduleTest`: spec samples (240 monthly from 2026-11-20 → maturity 2046-11-19, total
  300000.00, 240 rows, last due 2046-10-20; 12-month yearly → 1 row; quarterly 120 → 40 rows), month-end
  start date (31 Jan) does not drift.
- `PolicyRegistrationApiTest` (extends `IntegrationTest`): dashboard lists Aarav's policy; available
  products exclude held and inactive; preview numbers; successful registration returns 201 + number +
  schedule saved; duplicate → 409; age out of band → 422 (Rohan is 26 and HD-RET-001 needs 30–55); start date in past → 422; staff → 403; recommendation item
  of another customer → 422.

## Dev notes
**Built**
- `domain/PolicySchedule`: record with `of(start, termMonths, frequency, premium)`; every due date is
  offset from the start date (no month-end drift); maturity, count and total per architecture §5.
- `dto/policy`: `ProductResponse`, `PolicySummaryResponse` (both with a static `from(entity)` called only
  inside services), `RegistrationPreviewResponse` (nested `CustomerDetails`), `RegisterPolicyRequest`,
  `RegistrationResultResponse`.
- `ProductService`: available products = active products minus the codes of the customer's policies
  (filtered with a stream over the two existing repository queries); single product 404s when
  inactive or unknown.
- `PolicyRegistrationService`: dashboard list, preview, and `@Transactional register`. Register checks
  in this order: product active (422 `PRODUCT_NOT_AVAILABLE`), start date window (422
  `START_DATE_OUT_OF_RANGE`), entry age (422 `ENTRY_AGE_NOT_ELIGIBLE`), `existsByCustomerIdAndProductId`
  (409 `DUPLICATE_POLICY`), then the optional recommendation item (must belong to this customer and
  product, else 422 `INVALID_RECOMMENDATION_ITEM`). It saves the policy and all installments with
  `saveAndFlush`; a `DataIntegrityViolationException` there becomes 409 `DUPLICATE_POLICY` (the race).
  The recommendation item is marked selected after the save succeeds. "Today" is `LocalDate.now(clock)`.
- Controllers: `CustomerPolicyController`, `ProductController`, `PolicyRegistrationController`. Role
  rules were already in `SecurityConfig`; no change needed.

**Decisions not spelled out in the docs**
- Preview: an age or duplicate problem comes back as `eligible=false` + `ineligibilityReason` (200),
  so the form can still show the figures. A start date outside today..today+90 is a 422
  `START_DATE_OUT_OF_RANGE` on the preview too (it is an input error, and it avoids computing an age for
  a nonsense date).
- An unknown or inactive product code on preview/register is 422 `PRODUCT_NOT_AVAILABLE`;
  `GET /products/{code}` keeps the documented 404.
- Start date is only `@NotNull` on the request; the window is checked in the service because
  `@FutureOrPresent` would read the system clock instead of the injected one.

**Shared files:** none changed. No repository methods were added (existing queries were enough).

**Tests (all green, full suite 29 tests: 6 existing + 23 new)**
- `domain/PolicyScheduleTest` (5): spec monthly 240 sample, yearly 12 → 1 row, quarterly 120 → 40 rows,
  31 Jan start does not drift, non-dividing term rejected.
- `PolicyRegistrationApiTest` (17): dashboard (Aarav, empty for Rohan), available products (held and
  inactive excluded), single product 404 for inactive/unknown, preview figures and default start date,
  preview ineligibility (age, already held), preview out-of-window 422, successful registration (201,
  number pattern, 40 installments checked in the DB, dashboard/available lists updated),
  recommendation item marked selected, duplicate 409, age 422, start date past / +91 days 422 and
  +90 days accepted, inactive product 422, missing start date 400, another customer's item 422, item for
  a different product 422, staff 403.
- `service/PolicyRegistrationServiceTest` (1, Mockito): the unique-constraint race maps to
  `ConflictException("DUPLICATE_POLICY")`.
- Note: tests consume `policy_number_seq` values (sequences are not rolled back), so the API test
  asserts the number's pattern, not a fixed value.

## QA results
**Gate: PASS** (QA, 25 Nov 2026, commit `2243438`). All E2 ACs verified live and by hand calculation: dashboard, discovery,
preview, start-date edges (yesterday / today / +90 / +91), entry age, 409 duplicate including a 5-way parallel race
(1×201, 4×409), policy number per start year, schedule saved in one transaction, rec item marked selected, another
customer's item → 422. Low only: QA-4 (unreadable `startDate` gives generic 400 with no field error). See `docs/qa/qa-report.md`.
