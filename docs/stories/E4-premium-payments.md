# E4 Policy lookup and premium payments

**Status:** Done · **Owner:** Dev agent "payments" · **PRD:** FR-4 (AC-4.1 – AC-4.14)

## Stories
- **E4.1** As a customer or staff user, I open a policy by number and see its details and schedule.
- **E4.2** As a customer or staff user, I pay one or more installments, fully or partly, and get a receipt.

## Tasks
1. `domain/InstallmentRules` (pure): `resolveStatus(installment-ish values, today)`,
   `isPayable(...)`, `penaltyIfPaidOn(...)`. Use a **switch expression** on `PaymentStatus` somewhere
   meaningful (e.g. payability / what a payment does to each status).
2. `dto/payment/*` records: `PolicyDetailResponse`, `InstallmentResponse` (with `YearMonth premiumPeriod`),
   `PaymentRequest` + `PaymentItem`, `PaymentReceiptResponse` + line record, `PaymentHistoryResponse`.
3. `service/PolicyLookupService`: load by number with ownership check (404 for others), refresh
   statuses (persist changes), compute totals with streams.
4. `service/PremiumPaymentService.pay(...)`, one `@Transactional` method:
   validate request (no duplicate ids, all belong to the policy), then per installment in due-date
   order: payable? apply penalty if late and not yet applied; amount ≤ outstanding; record payment
   with `TXN-HD-%06d` from `nextTransactionSequence()`; update status. Finally mark policy FULLY_PAID if
   all installments are PAID. Optimistic-lock failures surface as 409 via the global handler (make sure
   the flush happens inside the method, e.g. `saveAndFlush` or `repository.flush()`).
5. Controller `PolicyPaymentController`: `GET /api/policies/{policyNumber}`, `.../installments`,
   `POST .../payments` (201), `GET .../payments`.

## Files you own
`domain/InstallmentRules.java`, `dto/payment/**`, `service/PolicyLookupService.java`,
`service/PremiumPaymentService.java`, `controller/PolicyPaymentController.java`, tests. You may **add**
methods to `PremiumInstallmentRepository` and `PremiumPaymentRepository`.
**Also own E5 (search)**, see that story.

## Tests required
- `InstallmentRulesTest`: every status transition, payable window edges, penalty rounding
  (e.g. 10% of 750.00 = 75.00; of 1234.55 = 123.46), penalty applied once only.
- `PremiumPaymentApiTest` (extends `IntegrationTest`, clock = 2026-11-25):
  - Meera's HDP-2026-0003 #2 (due 2026-09-15, grace 2026-10-15) is OVERDUE, `penaltyIfPaidToday` 750.00;
    paying 8250.00 → PAID, payment row penalty 750.00.
  - Paying 8250.01 → 422 `AMOUNT_EXCEEDS_OUTSTANDING`.
  - Aarav HDP-2026-0001 #2 (paid 500 of 1250) partial 250 → still PARTIALLY_PAID, outstanding 500.
  - Already-paid #1 → 409; same installment twice in one request → 422; installment #3 (due
    2027-01-20, beyond the one-month window) → 422 `INSTALLMENT_NOT_PAYABLE`.
  - Another customer's policy → 404; staff can pay any.
  - Policy becomes FULLY_PAID only when the last installment is paid (use a 1-installment policy:
    register Rohan for HD-HEALTH-001 inside the test, or construct via repositories).

## Dev notes
**Built**
- `domain/InstallmentRules`: `resolveStatus`, `isPayable` (exhaustive switch expression on
  `PaymentStatus`), `penaltyIfPaidOn` (10% via `Money.percentOf`, once only).
- `dto/payment/*`: `PolicyDetailResponse`, `InstallmentResponse` (`YearMonth premiumPeriod`),
  `PaymentRequest` + `PaymentItem` (bean validation: method required, 1..24 items, amount > 0 with at
  most 2 decimals), `PaymentReceiptResponse` + nested `Line`, `PaymentHistoryResponse`.
- `service/PolicyLookupService`: ownership via `Optional.filter` (others' policies → 404); statuses
  recomputed for today and changed rows flushed (ADR-5); totals with streams. Exposes
  `findAccessiblePolicy` / `currentInstallments` so the payment service uses the same rules.
- `service/PremiumPaymentService.pay`: one `@Transactional` method. Lines are resolved
  (`toMap` by id, `INSTALLMENT_NOT_IN_POLICY`), checked for repeats (`groupingBy`), sorted by due date,
  then **all** lines are validated (409 already paid, 422 not payable / amount above outstanding incl.
  penalty) before any is applied. Then penalty + payment recorded, status updated, one immutable
  `PremiumPayment` per line (`TXN-HD-%06d`), policy `FULLY_PAID` when every installment is PAID, and an
  explicit `flush()` so version clashes surface as `CONCURRENT_UPDATE` 409.
- `controller/PolicyPaymentController` (`/api/policies/{policyNumber}`, `/installments`,
  `POST /payments` 201, `GET /payments`).
- Added `PremiumPaymentRepository.findHistoryForPolicy` (join fetch installment, newest first by
  payment date then id). No change needed to `PremiumInstallmentRepository`.

**Deviations / decisions**
- Validate-all-then-apply: stricter than the task's per-line order, so a rejected request never leaves
  half-applied state, even in memory. Same outcome for the client.
- `INSTALLMENT_NOT_IN_POLICY` is checked before the duplicate check (the duplicate message can then name
  the installment number instead of an internal id).
- GET policy/installments run in a read-write transaction because the status refresh persists.
- FULLY_PAID test builds a 1-installment policy for Rohan through the repositories (E2's registration
  isn't on this branch); policy number `HDP-TEST-0001`, rolled back.
- No shared files changed.

**Tests:** `InstallmentRulesTest` 18, `PremiumPaymentApiTest` 16 (all required cases plus not-in-policy,
validation 400, whole-request rollback, unknown policy 404, simulated concurrent update → 409).

## QA results
**Gate: CONCERNS** (QA, 25 Nov 2026). All AC-4.x rejections verified live; penalty, grace and remaining maths match §5;
8 parallel payments on one installment → 1×201, 7×409 CONCURRENT_UPDATE with a consistent ledger; mixed valid/invalid
request applies nothing; FULLY_PAID set only on the last installment; other customers' policies → 404.
Open (Low): QA-3 (FULLY_PAID can be missed when the last two installments are paid in parallel requests),
QA-5 (wrong message for too-large amount), QA-4 (unknown payment method → generic 400).
