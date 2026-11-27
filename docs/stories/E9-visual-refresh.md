# E9 Visual refresh of the web app

**Status:** Done · **Owner:** Dev agent "frontend" · **PRD:** NFR-8, NFR-9 (no functional change)
**Why:** product-owner feedback after the v2 demo: the screens worked but looked dated.

## Scope
Look and feel only. Every label, field, rule and API call from E6 and E8 stays as it is, so the PDF field names
and the existing tests keep holding.

## Design decisions
- **Palette:** midnight indigo (`#1b1f45`) for the brand, dark blocks and primary buttons; a cool grey page
  (`#f4f5f8`) with white working surfaces; rani pink (`#d6246e`) only where something shows progress or
  "you are here" (active nav item, the premium meter, the logo's pulse line). Status colours stay the four
  badge tones from E6. (A first pass used deep green; the product owner turned it down.)
- **Type:** one family, Archivo, bundled through `@fontsource-variable/archivo` (width axis), so the app needs
  no font CDN. Headings, policy numbers and the amount to pay are set wide (122%), table headers slightly
  wide, body text at normal width.
- **Shell:** a fixed white navigation rail on the left with the account at the bottom; under 60rem it turns
  into a top bar with horizontally scrolling links.
- **Sign-in:** two halves: brand and a one-line promise on the left, form on the right. In development builds
  the demo accounts fill the form when picked (they never sign in by themselves).
- **Dashboard:** greets the customer by first name and says how many policies they hold. The recommendations
  prompt is the one dark block on the page.
- **Policy details (FR-4 A):** a midnight header with the policy number, name, premium payment status and a meter
  showing how much of Total Premium Payable has been paid. The required fields follow in the same order as E8.

## Tasks
1. Replace `styles/app.css` with the new token set and components (panels, tables, badges with a status dot,
   inputs with a focus ring, alerts tinted by tone).
2. `AppLayout` → navigation rail with icons; `Wordmark` component used in the rail and on sign-in.
3. Sign-in, dashboard header, policy details header and premium meter as above.
4. Respect `prefers-reduced-motion`; keep visible keyboard focus on every control.

## QA
- `npm test`: all front-end tests pass unchanged.
- Checked in the browser as Meera (dashboard, payments for HDP-2026-0003, recommendations) and on sign-in.
