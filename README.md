# HealthDekho Insurance Portal

Customer portal for HealthDekho: see your policies, get AI-assisted product suggestions, register a
policy, pay premiums, and search policies.

**Stack:** Java 21 · Spring Boot 3.5.7 (Web, Security, Data JPA) · PostgreSQL · Spring AI 1.1.8 · React 18 + Axios

| | |
|---|---|
| Source requirements | [docs/requirements/HealthDekho_Insurance_Management_System.pdf](docs/requirements/HealthDekho_Insurance_Management_System.pdf) (the only source of truth) |
| Requirements | [docs/prd.md](docs/prd.md) (PRD v2: acceptance criteria, decisions where the PDF is incomplete) and [docs/change-analysis-v2.md](docs/change-analysis-v2.md) |
| Design | [docs/architecture.md](docs/architecture.md) (schema, API contract, business formulas, AI design) |
| Delivery | [docs/bmad-workflow.md](docs/bmad-workflow.md), [docs/stories/](docs/stories), [docs/qa/](docs/qa) |
| Deployment | [docs/deployment.md](docs/deployment.md) (AWS Elastic Beanstalk + RDS, environment variables) |

## Prerequisites

- JDK 21 and Maven 3.8+
- Node.js 20+ and npm
- PostgreSQL 14+ (any install). For local work there is a ready container in `dev/`:
  ```bash
  docker compose -f dev/docker-compose.yml up -d
  ```
  It creates `healthdekho` (app) and `healthdekho_test` (integration tests), user/password `healthdekho`.
  Using your own PostgreSQL instead: create both databases and set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

## Run

```bash
# 1. API on http://localhost:8080 (dev profile: creates the schema and loads demo data on first start)
cd backend
mvn spring-boot:run

# 2. Web app on http://localhost:5173 (proxies /api to the backend)
cd frontend
npm install
npm run dev
```

### Demo accounts

`data.sql` loads exactly the sample data from section 5.5 of the requirements (Aarav, Meera, Rohan, three
products, two policies). The dev and test profiles also load `demo-data.sql`: more products, an operations
user and an older policy with an overdue installment, so every feature can be shown. Production loads neither.

| Who | Email | Password | Has |
|---|---|---|---|
| Customer | aarav.sharma@example.com | Demo@1234 | Term policy, one installment part-paid |
| Customer | meera.iyer@example.com | Demo@1234 | A fully paid health policy and (demo data) a savings policy with an **overdue** installment that shows the 10% penalty |
| Customer | rohan.verma@example.com | Demo@1234 | No policies yet: good for discovery, recommendations and registration |
| Operations staff (demo data) | ops.desk@example.com | Ops@1234 | Can look up and take payments on any policy |

## AI configuration

Recommendations go through Spring AI's OpenAI client, which works with any OpenAI-compatible endpoint.
Set these before starting the backend (model names are examples; check the provider's current list):

| Provider | `AI_BASE_URL` | `AI_COMPLETIONS_PATH` | `AI_MODEL` (example) |
|---|---|---|---|
| OpenAI | `https://api.openai.com` | `/v1/chat/completions` | `gpt-4o-mini` |
| Groq | `https://api.groq.com/openai` | `/v1/chat/completions` | `llama-3.3-70b-versatile` |
| Google Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `/chat/completions` | `gemini-2.5-flash` |
| Ollama (local) | `http://localhost:11434` | `/v1/chat/completions` | `llama3.2` |

```bash
export AI_API_KEY=...        # not needed for Ollama (any non-empty value works)
export AI_BASE_URL=https://api.openai.com
export AI_MODEL=gpt-4o-mini
```

Without a key the app still works: recommendations come from a rule-based ranker and the page says so.
Either way, eligibility, prices and totals are always calculated in Java, never by the model.

## Configuration

| Variable | Default (dev) | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/healthdekho` | |
| `DB_USERNAME` / `DB_PASSWORD` | `healthdekho` / `healthdekho` | |
| `JWT_SECRET` | throwaway dev value | **Required** outside dev, 32+ characters |
| `JWT_TTL` | `2h` | |
| `CORS_ORIGINS` | `http://localhost:5173` | |
| `AI_*` | see above | |

Profiles: `dev` (default), `test`, `prod` (`SPRING_PROFILES_ACTIVE=prod`: no defaults for secrets, no demo data).

Every setting is an environment variable; [.env.example](.env.example) lists them all. To set them in a
file, copy it to `.env` in the repo root and uncomment what you need: the API reads `.env` on start-up,
and real environment variables override it. On Elastic Beanstalk, use environment properties instead
(see [docs/deployment.md](docs/deployment.md)).

## Deploy

```bash
./deploy/build-beanstalk.sh   # -> backend/target/healthdekho-beanstalk.zip, web app packaged inside the API jar
```

Upload the zip to an Elastic Beanstalk Java (Corretto 21) environment and set the environment properties.
Step by step: [docs/deployment.md](docs/deployment.md).

## Tests

```bash
cd backend && mvn test      # unit + API tests against the healthdekho_test database
cd frontend && npm test     # Vitest
```

## Project layout

```
backend/    Spring Boot API: controller → service → repository, entity, dto (records), domain, ai, security
frontend/   React app: pages, components, api clients, auth context
docs/       brief, PRD, architecture, stories, QA
dev/        local PostgreSQL container (development and tests only)
deploy/     Elastic Beanstalk bundle script, Procfile and .ebextensions
```

## Not in this release

Claim registration and AI-assisted claim assessment (FE-1 in the requirements) are a planned future
release. No claim tables are created.
