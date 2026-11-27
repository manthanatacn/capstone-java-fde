# PRD v2: HealthDekho Insurance Management System

*BMAD phase: Planning. Owner: PM. Version 2, 27 Nov 2026.*
*Source of truth: [`requirements/HealthDekho_Insurance_Management_System.pdf`](requirements/HealthDekho_Insurance_Management_System.pdf).
What changed from v1 and why: [`change-analysis-v2.md`](change-analysis-v2.md).*

Numbering follows the PDF. Acceptance criteria (AC) are numbered so QA can trace them. "Customer" means a
signed-in customer; "staff" means a signed-in operations user (see A-4).

---

## FR-1 Customer dashboard, policy discovery and registration

### A. Customer authentication
- **AC-1A.1** React login page with email and password.
- **AC-1A.2** Authentication by Spring Security against the configured customer credentials (BCrypt hashes).
- **AC-1A.3** Success redirects a customer to the dashboard (staff to policy search).
- **AC-1A.4** Invalid credentials show "Invalid email or password." without revealing which part was wrong.
- **AC-1A.5** Dashboard data is restricted to the authenticated customer. No endpoint returns another
  customer's policies, schedules or recommendations.
- **AC-1A.6** Signed-out users are sent to login; an expired session returns to login with a notice.

### B. Customer dashboard
- **AC-1B.1** Table of all the customer's policies with: Policy Identifier, Policy Name, Policy Type, Policy
  Term, Policy Start Date, Policy Maturity Date, Premium Frequency, Premium Amount, Sum Insured, Policy Status.
- **AC-1B.2** A customer with no policies sees a clear message instead of an empty table.

### C. View available insurance policies
*(Body missing from the PDF; behaviour taken from §1 "products that the customer has not already registered for".)*
- **AC-1C.1** Table of active products the customer has not registered: Product Code, Policy Name, Policy Type,
  Policy Term, Sum Insured, Premium Frequency, Premium Amount, Register button.
- **AC-1C.2** Inactive products never appear.
- **AC-1C.3** Register opens the registration form with the product pre-populated.

### D. AI-based policy recommendations
*(Start of section missing from the PDF; inputs kept from v1.)*
- **AC-1D.1** From the dashboard the customer opens "Get recommendations" and gives one or more of: date of
  birth (defaults to profile), goals and preferences, preferred policy term, insurance type.
- **AC-1D.2** Age is calculated with the Java Time API.
- **AC-1D.3** Candidates are active products the customer doesn't hold and can register (entry age checked
  against both the typed and the profile date of birth, A-14), filtered by type when given. Done in Java,
  before the model is called.
- **AC-1D.4** Spring AI with a configured model ranks up to 3 candidates and writes a reason and suitability.
- **AC-1D.5** Recommendations reference only products retrieved from the database: anything else in the
  model's answer is dropped.
- **AC-1D.6** Results table: Product Code, Policy Name, Policy Type, Policy Term, Sum Insured, Premium
  Frequency, Premium Amount, Recommendation Reason, Suitability, Action (Register).
- **AC-1D.7** Register navigates to the registration form with the product pre-populated; registering marks
  that recommendation item `selected_for_registration`.
- **AC-1D.8** Every request and its items are saved against the customer, with model name/version, prompt
  version and calculated age.
- **AC-1D.9** If the model is unavailable the customer still gets rule-based suggestions, clearly labelled
  (A-11).

### E. Java 21 expectations (FR-1)
- **AC-1E.1** Records for request and response DTOs.
- **AC-1E.2** LocalDate / Java Time for date calculations.
- **AC-1E.3** Optional for repository lookups.
- **AC-1E.4** Stream API filters available products and excludes existing policies.
- **AC-1E.5** Collectors and suitable collection operations.
- **AC-1E.6** Pattern matching with `instanceof` where it fits.

## FR-3 Insurance policy registration

### A. Registration interface
- **AC-3A.1** Read-only from the customer: Customer Name, Date of Birth, Contact Information.
- **AC-3A.2** Read-only from the product: Policy Type, Policy Name, Policy Term, Premium Payment Frequency,
  Premium Amount, Sum Insured.
- **AC-3A.3** Policy Start Date defaults to today (may be moved up to 90 days ahead, A-10).
- **AC-3A.4** Policy Maturity Date and Total Premium Payable are calculated (server preview) and refresh when
  the start date changes.

### B. Business and validation rules
- **AC-3B.1** Mandatory fields validated in the browser and on the server (Bean Validation).
- **AC-3B.2** Date of birth not in the future (also a DB CHECK).
- **AC-3B.3** Age at start date within the product's min/max entry age.
- **AC-3B.4** Start and maturity dates consistent: start not in the past, within 90 days, maturity after start.
- **AC-3B.5** Premium amount, sum insured and term positive (DB CHECKs).
- **AC-3B.6** Unique policy identifier `HDP-<start year>-<4 digits>` from a DB sequence.
- **AC-3B.7** One policy per customer per product (`UNIQUE(customer_id, product_id)`), race-safe → 409.
- **AC-3B.8** `maturity = start + term months − 1 day`; `total premium payable = premium × (term ÷ months per
  installment)`. Spec sample: 20 Nov 2026 + 240 months → 19 Nov 2046; ₹1,250 × 240 = ₹3,00,000.
- **AC-3B.9** Installment schedule generated and saved with the policy in one transaction.
- **AC-3B.10** Success shows the policy identifier and a confirmation message.
- **AC-3B.11** Validation errors (400, per field), business-rule violations (422/409) and database failures (503)
  are each handled with a readable message.

### C. Java 21 expectations (FR-3)
Records, LocalDate, BigDecimal, Optional, Streams (AC-3C.1 … 3C.5).

## FR-4 Premium payment management

### A. Policy search (by identifier)
- **AC-4A.1** Search by policy identifier (case-insensitive) and show: Policy Identifier, Customer Name, Policy
  Name, Policy Term, Policy Start Date, Policy Maturity Date, **Total Premium Payable, Total Premium Paid,
  Total Remaining Premium Amount**.
- **AC-4A.2** Total Premium Payable is the contract total (premium × installments, as FR-3 A defines it).
  Late penalties are shown as their own line when non-zero, so `remaining = payable + penalties − paid`.
- **AC-4A.3** Customers only open their own policies (404 otherwise); staff open any.

### B. Premium payment schedule
- **AC-4B.1** Table of all installments: Installment Number, Premium Amount, Due Date, Grace Period End Date,
  Payment Status (PAID / PENDING / PARTIALLY_PAID / OVERDUE), Payment Selection checkbox.
- **AC-4B.2** Grace period end = due date + 1 month.
- **AC-4B.3** Installment status: fully paid → PAID; after grace end → OVERDUE; something paid →
  PARTIALLY_PAID; else PENDING.
- **AC-4B.4** Checkbox only on eligible installments: not PAID and due within one month (A-9).

### C. Payment processing
- **AC-4C.1** Select one or more installments, capture the amount paid for each (partial allowed), choose a
  method, confirm.
- **AC-4C.2** Payment status updates automatically after payment.
- **AC-4C.3** Payment date (today) and transaction details (`TXN-HD-<6 digits>`, method) recorded automatically.
- **AC-4C.4** After the one-month grace period a 10% penalty on the outstanding amount is applied once (A-8),
  shown before confirming.
- **AC-4C.5** Outstanding amounts recalculated automatically.
- **AC-4C.6** Immutable payment history, viewable per policy.
- **AC-4C.7** No duplicate payment on an installment (already PAID → 409, repeated in one request → 422,
  concurrent → 409).
- **AC-4C.8** No payment above the outstanding amount (422).
- **AC-4C.9** All lines of one payment succeed or fail together.
- **AC-4C.10** A policy is marked fully paid (`fully_paid_on` set, premium payment status FULLY_PAID) only when
  every installment is PAID. Policy status (cover) stays ACTIVE (A-6).

### D. Java 21 expectations (FR-4)
Records, YearMonth premium periods, BigDecimal, switch expressions / pattern-matching switch for payment
status, Streams, transactions (AC-4D.1 … 4D.6).

## FR-5 Customer insurance policy search

- **AC-5A.1** Fields: Customer Name, Policy Identifier, Date of Birth.
- **AC-5A.2** Allowed: identifier alone; customer name alone (partial, case-insensitive, ≥ 2 characters);
  identifier + date of birth. Anything else is rejected with a message listing the allowed combinations.
- **AC-5B.1** Results table: Policy Identifier, Customer Name, Date of Birth, Policy Name, Policy Type, Policy
  Start Date, Policy Maturity Date, **Total Premium Payable, Premium Payment Status, Total Premium Paid,
  Remaining Premium**.
- **AC-5B.2** Premium payment status of a policy: all installments PAID → FULLY_PAID; any OVERDUE → OVERDUE;
  any PARTIALLY_PAID → PARTIALLY_PAID; otherwise PENDING.
- **AC-5C.1** "No policies match these details." when nothing is found.
- **AC-5C.2** Criteria validated before querying.
- **AC-5C.3** Efficient Spring Data JPA queries: one paged query for policies, one grouped query for the totals
  of the whole page (no query per row).
- **AC-5C.4** Paginated (default 10, max 50).
- **AC-5C.5** Staff see all policies; customers only their own.
- **AC-5D** Records, Streams for entity → DTO, `Collectors.groupingBy`, Optional, pagination.

---

## Non-functional requirements

| Id | Requirement |
|---|---|
| NFR-1 | Layers: controller → service → repository; entity/DTO; separate AI integration package. Entities never leave services. DTOs are records. |
| NFR-2 | Money `BigDecimal`, scale 2, HALF_UP, stored `NUMERIC(19,2)`. |
| NFR-3 | `LocalDate`, `YearMonth`; "today" from an injected `Clock` (Asia/Kolkata), same zone in the browser. |
| NFR-4 | Registration and payment are single transactions. |
| NFR-5 | Errors are problem+json with a message, `code` for business errors, `errors` for fields; correct HTTP status. |
| NFR-6 | Profiles `dev`, `test`, `prod`; secrets from environment; prod has no defaults and no seed data. |
| NFR-7 | AI never decides eligibility, prices or totals; its output is validated and saved for audit. |
| NFR-8 | Loading indicator for every request, success message after every write, readable errors. |
| NFR-9 | Amounts in Indian format (₹1,25,000.00), dates as 20 Nov 2026, in the UI and in server messages. |
| NFR-10 | Schema column names follow PDF §5.2. Seed data (`data.sql`) is exactly the PDF §5.5 sample; demo extras live in `demo-data.sql` (dev/test only). |

---

## Decisions where the PDF is silent or incomplete

| # | Topic | Decision |
|---|---|---|
| A-1 | FR-1 C / D text missing | Behaviour kept from v1 (see change analysis). |
| A-2 | FR-2 missing | Recommendation inputs kept from v1. |
| A-3 | §5.1 and first rows of §5.2 missing | Existing customer/product/policy tables kept; all visible columns aligned. |
| A-4 | FR-4/FR-5 say "users"; only customer login is described | A `staff_user` table and STAFF role let operations search and service any policy. Customers keep both features for their own policies. **Product owner to confirm.** |
| A-5 | "Defined duplicate-registration business rule" | §5.2: one policy per customer per product, ever. |
| A-6 | Sample 3002 is fully paid yet ACTIVE | Policy status = state of cover (ACTIVE; MATURED after the maturity date). "Fully paid" is a separate marking (`fully_paid_on`) and a derived premium payment status. Seeded exactly as the sample. |
| A-7 | Sample payment 5002 dated 20 Dec 2026 | Kept as given. |
| A-8 | Penalty base for partial payments | 10% of the outstanding amount at the first late payment, once per installment. |
| A-9 | Which installments are eligible to pay | Not PAID and due within one month from today. |
| A-10 | Can the start date change? | Today to today + 90 days. |
| A-11 | AI provider | Any OpenAI-compatible endpoint via Spring AI; rule-based fallback when unavailable. |
| A-12 | Demo coverage (overdue penalty, more products) | Extra products, a staff user and an older policy with an overdue installment live in `demo-data.sql`, not in the spec sample. |
| A-13 | Product description | Nullable `description` column, used as model context. |
| A-14 | Recommendation DOB vs profile DOB | A product is suggested only if both ages fit its entry band, so every suggestion can be registered. |
| A-15 | Rule-based recommendation audit fields | `model_name = rule-based`, `prompt_version = rules-v1`, no prompt text or model version. |
| A-16 | `preferred_term` type | Text, e.g. "20 years", as in sample 5.5.6. The API still takes whole years. |
| A-17 | "Total Premium Payable" after a late penalty | Always the contract total, as the PDF defines it (FR-3 A, FR-5 B "Total policy premium"), on every screen. Penalties are a separate figure; remaining = payable + penalties − paid. (Changed after QA2-2.) |
