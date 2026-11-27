# E10 Deployment to AWS Elastic Beanstalk

**Status:** Done · **Owner:** Dev agent "platform" · **PRD:** NFR-6 (profiles, secrets from the environment)
**Why:** the product owner deploys on Elastic Beanstalk and needs every setting to be easy to enter.

## Scope
Packaging and configuration only. No business rule, API contract or screen changes.

## Decisions
- **One process, one origin.** The React build is packaged inside the Spring Boot jar
  (`mvn -Pbundle-frontend package`) and served by the API. The browser calls `/api` on the same host, so there is
  no CORS setup and no second deployment. Client-side routes (`/dashboard`, `/payments`) fall back to
  `index.html`; unknown `/api` paths never do.
- **Settings are environment variables, nothing else.** `.env.example` is the one list of them. Locally the API
  also reads an optional `.env` (`spring.config.import`); real environment variables beat it. On Beanstalk they
  are environment properties; secrets can come from Secrets Manager or Parameter Store as environment secrets.
- **Non-secret Beanstalk defaults live in the bundle** (`deploy/beanstalk/.ebextensions/01-app.config`:
  `SPRING_PROFILES_ACTIVE=prod`, `PORT=5000`, health check `/`). Console values override them.
- **Database on RDS, created outside Beanstalk**, so terminating an environment cannot delete the data.
- **Demo seed is opt-in in prod** with `SPRING_SQL_INIT_DATA_LOCATIONS`, because prod loads the schema only and
  the portal has no self sign-up.

## Tasks
- [x] Optional `.env` import in `application.yml`
- [x] `bundle-frontend` Maven profile; `WebConfig` static serving with route fallback; GET outside `/api` public
- [x] `deploy/build-beanstalk.sh`, `Procfile`, `.ebextensions`
- [x] `.env.example` rewritten; `docs/deployment.md`; README sections

## QA results
- `BundledWebAppTest`: client routes serve the page without a token; missing files are 404; `/api` still needs a
  token and never falls back to the page; writes outside `/api` are refused. Full suite: 153 tests pass.
- Bundle smoke test: jar started with `SPRING_PROFILES_ACTIVE=prod` and settings from a `.env` only:
  `/`, `/dashboard` → 200 HTML, `/api/auth/me` → 401, demo sign-in → 200.
