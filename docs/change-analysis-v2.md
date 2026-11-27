# Change analysis: requirements v2

*BMAD phase: Analysis. Owner: Analyst. Date: 27 Nov 2026.*

**Source of truth from now on:** [`requirements/HealthDekho_Insurance_Management_System.pdf`](requirements/HealthDekho_Insurance_Management_System.pdf)
(12 pages). It replaces the earlier "Insurance App Requirements – Full Notes". Where the two disagree,
v2 wins. Where v2 is silent because a part is missing from its screenshots, the earlier understanding is
kept and marked as such.

## 1. What v2 itself says is missing

The document carries three "missing from source screenshots" notes:

| Gap in v2 | How we handle it |
|---|---|
| FR-1 C "View Available Insurance Policies" body, and the start of FR-1 D | Kept the behaviour already built (active products not yet held, Register button). v2 §1 confirms it: "Display of available insurance products that the customer has not already registered for." |
| FR-2 body | v2 moved the AI recommendation output into FR-1 D. The recommendation inputs (DOB, goals, preferred term, type) are kept from v1. |
| Section 5.1 and the first rows of 5.2 (customer, insurance_product, start of customer_policy) | Kept the existing tables; aligned every column v2 does show. |

## 2. Delta, v1 implementation → v2 PDF

| # | Area | v2 says | We had | Change needed |
|---|---|---|---|---|
| D-1 | §1 Business scenario | New section listing seven capabilities | All seven exist | None (traceability only) |
| D-2 | §2.1 | Adds "Bean Validation" and "appropriate exception handling and HTTP status codes" | Both in place | None |
| D-3 | FR-1 structure | Recommendations are now FR-1 D, with a new FR-1 E (Java 21: records, Java Time, Optional, Stream API for filtering available products and excluding existing policies, Collectors, **pattern matching with instanceof**) | Recommendations built as FR-2; instanceof only used in security/error handling | Re-map docs; verify every FR-1 E item in FR-1 code, add instanceof where it is natural |
| D-4 | FR-1 B | "Display an appropriate message when the customer has not registered any insurance policy" | Empty state exists | None |
| D-5 | FR-4 A | Policy search shows exactly: identifier, customer name, policy name, term, start date, maturity date, **Total Premium Payable, Total Premium Paid, Total Remaining Premium Amount** | Showed these plus extras, labelled "Total premium / Total paid / Outstanding" | Use the v2 names in API and UI. Make the three totals add up when late penalties exist |
| D-6 | FR-5 B | Result columns: identifier, customer name, DOB, policy name, type, start, maturity, **Total Premium Payable, Premium Payment Status, Total Premium Paid, Remaining Premium** | Had premium amount, frequency, policy status, remaining premium | New column set. New concept: premium payment status of a policy |
| D-7 | §5.5.3 sample | Policy 3002 (one yearly installment, paid) has status **ACTIVE** | We seeded FULLY_PAID (old decision A-6) | Policy status and premium payment status are two different things: status stays ACTIVE (cover in force), and "fully paid" is a separate marking. Seed exactly as the sample |
| D-8 | §5.2 column names | `installment_number`, `grace_period_end_date`, `total_premium_payable`, `preferred_term` | `installment_no`, `grace_end_date`, `total_premium`, `preferred_term_years` | Rename columns (and the matching Java fields and JSON names). `preferred_term` stores text like "20 years", as in sample 5.5.6 |
| D-9 | §5.2 premium_installment | "paid_amount between 0 and scheduled amount plus applicable penalty" | Same rule as two CHECKs | Express as one CHECK that reads like the spec |
| D-10 | §5.5 sample data | Exactly 3 customers, 3 products, 2 policies, 4 installments shown, 3 payments, 2 recommendation requests, 3 items | Sample + our extras (5 products, policy 3003, staff user) mixed in `data.sql` | `data.sql` = the v2 sample only. Our extras move to `demo-data.sql`, loaded in dev/test only, clearly labelled |
| D-11 | FE-1 | Adds "Policy Validity" and "Document Verification" rules | Not built (out of scope) | None. Still out of scope |
| D-12 | Staff role | v2 mentions only customer authentication; FR-4/FR-5 say "users" | We added a `staff_user` table and STAFF role | Kept, flagged to the product owner (see PRD v2 A-4). Without it, "search by customer name" can only ever return the caller's own policies |

## 3. Things v2 confirms we already do

FR-1 A login/redirect/error; FR-1 B table; FR-1 D Register from recommendations, saved recommendations,
model limited to DB products; FR-3 every rule (DOB not future, entry age, date consistency, positive amounts,
unique identifier, duplicate prevention, total, schedule, persistence, confirmation, error handling);
FR-4 B/C every rule (10% penalty after one-month grace, partial payments, no over-payment, no duplicate
payments, history, fully paid only when all paid); FR-5 search combinations, validation, empty message,
pagination; all Java 21 expectations listed for FR-3, FR-4, FR-5.
