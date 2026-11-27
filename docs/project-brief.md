# Project Brief: HealthDekho Insurance Portal

*BMAD phase: Analysis. Owner: Analyst. Source: "Insurance App Requirements – Full Notes" (PDF, 16 pages, 25 Nov 2026).*

## Problem

HealthDekho sells term, health and savings insurance. Customers have no self-service view of what they
hold, no guided way to pick the next product, and premium payments are tracked by hand, so late fees and
"is this policy fully paid?" questions are answered inconsistently.

## What we are building

A web portal with a Spring Boot API and a React front end:

1. **Dashboard and discovery**: a customer signs in, sees their policies, and sees the products they
   don't hold yet.
2. **AI recommendations**: the customer describes their goals and gets a short, explained shortlist
   drawn only from HealthDekho's own catalogue.
3. **Registration**: one form, mostly pre-filled, that issues a policy number and a full premium
   schedule.
4. **Premium payments**: open a policy by number, see every installment, pay one or several (full or
   partial), with the late penalty applied by rule.
5. **Policy search**: find policies by number, customer name, or number plus date of birth.

## Users

| User | Needs |
|---|---|
| Customer | See own policies, get suggestions, register, pay premiums |
| Operations desk (staff) | Look up any customer's policy, record a payment on their behalf |

The spec only describes customer login. Search by *customer name* (FR-5) only makes sense for a staff
user, so we add one staff role. Customers can still use search, limited to their own policies.

## Success looks like

- Every FR-1..FR-5 acceptance criterion in the PRD passes in a demo against the sample data in spec §5.5.
- Money is calculated by Java code with `BigDecimal`, never by the model. The AI only ranks and explains.
- A reviewer can clone, start PostgreSQL, run two commands and log in.

## Out of scope

- FE-1 claim assessment (the spec explicitly excludes it; no claim tables are created)
- Customer self sign-up, password reset, real payment gateway, email/SMS
- Product master maintenance screens (products are seeded)

## Constraints

- Stack is fixed by the spec: Java 21, Spring Boot 3.5.7, Spring Web, Spring Security, Spring Data JPA,
  PostgreSQL, Spring AI, React with Axios. No extra frameworks beyond what these need.
- Docker is used only to run PostgreSQL locally. The app does not depend on it.
