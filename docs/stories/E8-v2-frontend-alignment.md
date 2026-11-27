# E8 Front-end alignment with requirements v2

**Status:** Done · **Owner:** Dev agent "frontend-v2" · **Source:** PDF v2 · **PRD:** AC-1B.*, AC-1D.6, AC-3A.4, AC-4A.*, AC-4B.1, AC-5B.*, NFR-9
**Why:** [change-analysis-v2.md](../change-analysis-v2.md) D-5, D-6, D-7, D-8.

## Tasks
1. Follow the renamed JSON fields in architecture §6 v2 everywhere (`installmentNumber`, `gracePeriodEndDate`,
   `totalPremiumPayable`, `totalPremiumPaid`, `remainingPremium`, `latePenalties`, `premiumPaymentStatus`,
   `fullyPaidOn`, `status` ACTIVE/MATURED).
2. **Payments page, policy details (FR-4 A):** show exactly these labels, in this order: Policy Identifier,
   Customer Name, Policy Name, Policy Term, Policy Start Date, Policy Maturity Date, Total Premium Payable,
   Total Premium Paid, Total Remaining Premium Amount. Under Total Premium Payable, when `latePenalties > 0`,
   a small note "includes ₹750.00 late penalties". Show the premium payment status as a badge next to the title
   and "Fully paid on 20 Nov 2026" when set. Other existing fields may stay in a secondary row only if they
   don't crowd the required ones.
3. **Schedule table (FR-4 B):** column headings use the PDF names: Installment Number, Premium Amount, Due Date,
   Grace Period End Date, Payment Status, Payment Selection. The extra columns we show (period, penalty, paid,
   outstanding, amount to pay) may stay, after the required ones or where they read naturally.
4. **Search results (FR-5 B):** exactly these columns in this order: Policy Identifier, Customer Name, Date of
   Birth, Policy Name, Policy Type, Policy Start Date, Policy Maturity Date, Total Premium Payable, Premium Payment
   Status (badge), Total Premium Paid, Remaining Premium. Remove premium amount, frequency and policy status.
5. **Dashboard (FR-1 B):** column headings use PDF names (Policy Identifier … Policy Status). Status badge for
   ACTIVE / MATURED.
6. **Registration (FR-3 A):** label "Total Premium Payable"; reads `totalPremiumPayable`.
7. **Receipt:** premium payment status and remaining premium.
8. Badge styles for premium payment statuses (FULLY_PAID green, OVERDUE red, PARTIALLY_PAID amber, PENDING grey)
   reusing the existing badge tokens.

## Files
`frontend/**` and this story file only. Update tests for renamed fields; add a test that the search table
renders the v2 columns in order and one that the policy details card shows the three FR-4 A totals with the
penalty note.

## Dev notes
**What changed**
- v2 JSON names used everywhere (architecture §6): `installmentNumber`, `gracePeriodEndDate`,
  `totalPremiumPayable`, `latePenalties`, `totalPremiumPaid`, `remainingPremium`, `premiumPaymentStatus`,
  `fullyPaidOn`; receipt reads `premiumPaymentStatus` / `remainingPremium`; lines and history read `installmentNumber`.
- Payments, policy details: one list with exactly the nine FR-4 A labels in PDF order; "includes ₹750.00 late
  penalties" under Total Premium Payable when `latePenalties > 0`; premium payment status badge next to the title,
  plus "Fully paid on 20 Nov 2026" when `fullyPaidOn` is set. Type, frequency, premium, sum insured, installments
  paid and Policy Status (ACTIVE/MATURED badge) moved to a lighter secondary row under a divider.
- Schedule: the six FR-4 B headings first, in PDF order (checkbox column is "Payment Selection"); then Amount to
  Pay (next to the checkbox), Premium Period, Late Penalty, Amount Paid, Outstanding Amount.
- Search: exactly the eleven FR-5 B columns in order; premium amount, frequency and policy status removed.
  Total Premium Payable carries the same penalty note when `latePenalties > 0` (AC-4A.2 "penalties shown separately").
- Dashboard (FR-1 B), available products and recommendations (AC-1D.6) use PDF column names; "Why it fits" is now
  "Recommendation Reason"; the Action column header is visible. Rank column kept on recommendations.
- Registration: "Total Premium Payable" from `totalPremiumPayable` (preview and success); neighbouring labels in
  the same lists title-cased to match. Receipt and history labels title-cased for the same reason.
- Search form (FR-5 A): fields in PDF order, labelled Customer Name, Policy Identifier, Date of Birth; the intro
  text, hint and combinations message use the same names. Payments lookup field (FR-4 A): "Policy Identifier".
- Registration (FR-3 A): Customer Name, Date of Birth, Contact Information (email and phone stacked under one
  label); Policy Type, Policy Name, Policy Term, Premium Payment Frequency, Premium Amount, Sum Insured; Policy
  Start Date (the date field), Policy Maturity Date, Total Premium Payable. Extras kept with clear labels:
  Product Code, Entry Age, Number of Installments, Age at Start Date, and the eligibility message. The success
  view says "Policy Identifier".
- Badges: `PENDING` mapped explicitly to neutral; FULLY_PAID green, PARTIALLY_PAID amber, OVERDUE red, existing tokens.
  ACTIVE and MATURED stay neutral so "fully paid" (green) is never confused with cover status.

**Files**
`src/components/{StatusBadge,DetailList,LatePenaltyNote}.jsx` (LatePenaltyNote new),
`src/pages/{DashboardPage,PolicySearchPage,RecommendationsPage,RegisterPolicyPage}.jsx`,
`src/pages/payments/{PolicyDetailsCard,InstallmentSchedule,PaymentPanel,PaymentReceipt,PaymentHistory}.jsx`,
`src/pages/payments/PolicyLookupForm.jsx`, `src/utils/searchCriteria.js`,
`src/styles/app.css` (`.details--secondary`, `.value-note`, `.value-stack`, `.panel__status`);
tests: `PremiumPaymentsPage.test.jsx`, `PolicySearchPage.test.jsx`, `RegisterPolicyPage.test.jsx`,
new `payments/PolicyDetailsCard.test.jsx`.

**Tests** `npm test`: 12 files, 53 tests (47 before + 6 new: schedule headings in FR-4 B order; search table
columns and cell values in FR-5 B order; details card FR-4 A labels in order with the three totals and penalty
note; no note without penalties; fully paid badge/date with Policy Status kept separate; registration FR-3 A
labels in order with Contact Information holding email and phone). Search tests find fields by the new labels;
the payments test checks the "Policy Identifier" lookup field. `npm run build` OK.

**Contract assumptions**
- `latePenalties` is a number (0 when none) on `PolicyDetail` and `PolicySearchResult`; the note is hidden for 0/null.
- `fullyPaidOn` is an ISO date or null. `PaymentReceipt.totalPaid` and `totalPenalty` are unchanged in v2.
- Recommendation request keeps `preferredTermYears` (E7 task 7, architecture §6).

## QA results
**Gate: PASS, 27 Nov 2026.** Full report: [qa-report-v2.md](../qa/qa-report-v2.md).
- `npm test`: 53 tests in 12 files, all passed.
- UI labels match the PDF names and order for FR-1 B (10), FR-1 D (10, plus Rank), FR-3 A (12), FR-4 A (9), FR-4 B (6), FR-5 A (3)
  and FR-5 B (11). Every field the UI reads exists in the v2 API; no v1 names remain.
- The only open point, QA2-2 ("Total Premium Payable" meaning), sits in the API contract, not in this story.
