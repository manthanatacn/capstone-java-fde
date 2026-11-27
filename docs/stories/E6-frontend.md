# E6 React front end

**Status:** Done · **Owner:** Dev agent "frontend" · **PRD:** FR-1..FR-5 UI parts, NFR-8, NFR-9

## Stories
- **E6.1** App shell: login, auth context, guarded routes, nav by role, Axios client.
- **E6.2** Dashboard: my policies table, available products table with Register.
- **E6.3** Recommendations page: input form, results table with reason, suitability, Register.
- **E6.4** Registration page: pre-filled read-only details, start date, live preview, submit,
  confirmation with policy number.
- **E6.5** Premium payments page: lookup by number, details card, schedule table with checkboxes and
  editable amounts, method, penalty shown before confirming, receipt, payment history.
- **E6.6** Policy search page: three fields, client-side check of the allowed combinations, paged
  results, empty state.

## Contract
Build strictly against architecture §6 (paths, field names, error shape) and §9 (structure, routes).
Dev server proxies `/api` to `http://localhost:8080` (Vite `server.proxy`).

## Look and feel
This is an insurer's customer portal, not a startup landing page.
- Calm and dense enough for tables. Off-white background, dark ink text, one accent (deep teal), status
  colours only for status badges (paid/green, pending/grey, partial/amber, overdue/red).
- System font stack, `font-variant-numeric: tabular-nums` in tables, right-aligned money.
- ₹ amounts with Indian grouping (`Intl.NumberFormat('en-IN', {style:'currency', currency:'INR'})`),
  dates as `20 Nov 2026`.
- No gradients, glassmorphism, emoji, stock illustrations or marketing copy. Plain, specific labels
  ("Pay selected installments", not "Let's go!").
- Works at 1280px and is usable at 400px (tables scroll horizontally inside their container).
- Accessible: labels on every input, visible focus, errors linked with `aria-describedby`, buttons
  disabled while submitting.

## Tech rules
React 18 function components and hooks, Axios, React Router. No UI kit, no CSS framework, no state
library, no form library. Vitest + @testing-library/react for tests.

## Tests required
At least: login error message shown on 401; search form rejects DOB-only before calling the API;
payment selection total and penalty preview; `format.js` money/date helpers.

## Dev notes
- **Built:** all six stories. Login with role-based landing, guarded routes (customer-only pages send staff to
  `/search`), nav by role, name + Sign out in the header. Dashboard (my policies, recommendations entry point,
  available products with Register), Recommendations (form with the at-least-one rule, RULE_BASED/notice banner,
  Register with `?rec=`), Registration (server preview on load and on date change, start date limited to
  today..today+90, submit disabled while ineligible/loading/submitting, success view with policy number),
  Payments (lookup via `?policy=`, details card, schedule paged 12 rows per page and opening on the first unpaid
  page, checkboxes only on `payable` rows, editable amount defaulting to outstanding + `penaltyIfPaidToday`,
  totals and penalties shown live and again on a confirm step, receipt, history), Search (client-side AC-5.2
  check, paged results, empty state, rows link to Payments).
- **Structure:** as architecture §9. `api/` (Axios client + one module per feature), `auth/` (session storage,
  context, guard), `components/` (DataTable, Field, Alert/ErrorAlert, Spinner, Pager, StatusBadge, Money,
  DateText, DetailList, AppLayout), `pages/` (+ `pages/payments/` for the payment page's parts), `utils/`
  (`format`, `dates`, `errors`, `searchCriteria`, `paymentSelection`), one stylesheet with custom properties.
- **Token handling:** token, expiry and profile in `sessionStorage`; expired tokens are dropped on read. A 401 on
  any call except `POST /auth/login` clears the session and routes to `/login` with a "session expired" notice.
- **Money maths** in the browser (selection totals) is done in paise to avoid float drift; the server stays the
  source of truth for every amount.
- **Contract assumptions:** (1) The registration preview may report a bad start date either as a 422
  (`START_DATE_OUT_OF_RANGE` or `errors.startDate`) or as `eligible=false`; the page handles both. (2) A payment
  item's `amount` includes the penalty (max = outstanding + `penaltyIfPaidToday`), per AC-4.10. (3) Field errors
  the form can't place (e.g. `items[0].amount`) are listed inside the error alert. (4) `recommendationItemId` is
  sent only when `?rec=` is numeric.
- **Versions:** react/react-dom 18.3.1, react-router-dom 7.18.5, axios 1.21.0, vite 6.4.5,
  @vitejs/plugin-react 5.1.4, vitest 4.1.12, jsdom 25.0.1, @testing-library/react 16.1.0 (dom 10.4.0,
  user-event 14.5.2, jest-dom 6.6.3). Router 7 / axios 1.21 / vite 6 / vitest 4 instead of 6.x / 1.7 / 5.4 / 2.1
  because the older lines have open advisories (axios and every react-router 6.x ship to the browser);
  `npm audit` reports 0 vulnerabilities. All versions are more than two weeks old.
- **Tests:** 45 tests in 10 files, all passing (`npm test`). Covers the required four (login 401 message, search
  rejects DOB-only without calling the API, payment selection total + penalty preview, `format.js`) plus the
  API client's 401 handling, error parsing, search combinations, payment maths, registration
  eligibility/submit, and route guarding.

## QA results
**Gate: CONCERNS** (QA, 25 Nov 2026; code review + proxy check, no browser). Routes, role nav, 401 redirect,
loading/disabled states, success messages, field errors, `en-IN` ₹ and `20 Nov 2026` formatting, payment selection
in paise and the AC-5.2 client check all match the ACs. 45/45 tests pass.
Open (Low): QA-8 (no "session expired" notice after a reload), QA-9 (server messages with ISO dates/plain amounts
shown verbatim), QA-10 (browser-local "today" vs server IST can strand the registration page).
