# E5 Customer policy search

**Status:** Done · **Owner:** Dev agent "payments" (same agent as E4) · **PRD:** FR-5 (AC-5.1 – AC-5.7)

## Story
As staff (or a customer, for their own policies), I search by policy number, by customer name, or by
policy number plus date of birth, and get a paged table with the remaining premium.

## Tasks
1. `dto/search/*` records: `PolicySearchCriteria` (or plain params), `PolicySearchResult`.
2. `repository/PolicySearchRepository extends Repository<CustomerPolicy, Long>` (a second repository
   for the same entity, so E2's repository file isn't touched): paged JPQL queries for the three modes,
   each with an optional customer-id restriction, `join fetch`/entity graph for customer + product, and
   a count query. Plus a query returning `(policyId, sum(outstanding))` for unpaid installments of a
   set of policy ids.
3. `service/PolicySearchService`: validate the combination (AC-5.2) → `BusinessRuleException`
   mapped to **400** with code `INVALID_SEARCH_CRITERIA` (add a small `InvalidSearchCriteriaException`
   subclass using the protected status constructor). Run the query, build remaining premium with
   `Collectors.groupingBy` / `toMap`, map with streams to records, return `PageResponse`.
   Size default 10, max 50. Sort by policy number.
4. Controller `PolicySearchController`: `GET /api/policies/search` (literal path, see architecture §6).

## Files you own
`dto/search/**`, `repository/PolicySearchRepository.java`, `service/PolicySearchService.java`,
`controller/PolicySearchController.java`, `exception/InvalidSearchCriteriaException.java`, tests.

## Tests required
- `PolicySearchServiceTest` for the combination rules.
- `PolicySearchApiTest`: staff by name "meera" → 2 policies; by number; number + correct DOB → 1;
  number + wrong DOB → empty with 200; DOB alone → 400; customer Aarav searching "meera" → empty;
  remaining premium for HDP-2026-0001 = 300000 − 1750 = 298250.00; pagination fields present.

## Dev notes
**Built**
- `dto/search/PolicySearchCriteria` (bound from query params, ISO `dateOfBirth`) and `PolicySearchResult`.
- `repository/PolicySearchRepository extends Repository<CustomerPolicy, Long>`: three paged JPQL queries
  (number; number + DOB; name `like`, case-insensitive), each `join fetch` customer + product, explicit
  count query, and `(:customerId is null or c.id = :customerId)` for the customer restriction. Plus
  `findRemainingPremiums(ids)` returning a `RemainingPremium(policyId, amount)` record via a constructor
  expression (unpaid installments only, grouped by policy).
- `service/PolicySearchService`: a small sealed `SearchMode` (three records) built from the criteria;
  invalid combinations → `InvalidSearchCriteriaException` (400, `INVALID_SEARCH_CRITERIA`, message lists
  the valid combinations). The repository call is a pattern-matching switch over `SearchMode`. Remaining
  premium joined with `Collectors.toMap`; policies with nothing unpaid get 0.00. Sorted by policy number.
- `controller/PolicySearchController`: literal `GET /api/policies/search`.
- `exception/InvalidSearchCriteriaException` (protected status constructor of `BusinessRuleException`).

**Decisions**
- Blank fields count as empty; policy number is trimmed and upper-cased; name is trimmed and must be ≥ 2
  characters.
- Page size: default 10, values above 50 are capped at 50 (not rejected), size < 1 becomes 1, negative
  page becomes 0. The response's `size` shows the size actually used.
- `%`/`_` in the name are escaped (lead review), so they match literally.
- Offsets above 100,000 rows are rejected with 400 (QA-2).
- Customers get an empty page (200), not 404, when the policy isn't theirs. This matches "customers only
  ever get their own policies back" and doesn't reveal whether the number exists.

**Tests:** `PolicySearchServiceTest` 12 (Mockito: each valid mode, customer restriction, 6 invalid
combinations, short name, paging cap/sort), `PolicySearchApiTest` 14 (all required cases, wildcard escaping, page-offset limit, plus
malformed DOB 400, default/capped size, no-token 401, and `/search` not captured by
`/{policyNumber}`).

## QA results
**Gate: CONCERNS** (QA, 25 Nov 2026). All AC-5.2 combinations, case-insensitive partial name, literal `%`/`_`,
customer restriction, remaining premium (298050.00 / 285000.00) and size default/cap verified live.
Open (Low): QA-2 (very large `page` → 503 and an ERROR stack trace in the log), QA-4 (malformed DOB returns raw
Spring conversion text). Dev notes are out of date on LIKE escaping and the API test count (now 12).
