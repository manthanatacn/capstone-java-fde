# How this project uses BMAD

BMAD ("Breakthrough Method for Agile AI-Driven Development",
[bmad-code-org/BMAD-METHOD](https://github.com/bmad-code-org/BMAD-METHOD)) runs a project through a
small agile team of AI agents. Each agent has one role and hands a document to the next. The rule that
matters most: **no code is written until the story it belongs to is written down**, so the developer
agents build from documents, not from guesses.

## Roles and the documents they own

| Role | Output in this repo |
|---|---|
| Analyst | [`project-brief.md`](project-brief.md) |
| Product Manager | [`prd.md`](prd.md): requirements, acceptance criteria, gap decisions |
| Architect | [`architecture.md`](architecture.md): stack, schema, API contract, business formulas |
| Scrum Master | [`stories/`](stories): one file per story, with tasks and dev notes |
| Developer agents | Code under `backend/` and `frontend/`, one agent per epic |
| QA | [`qa/test-strategy.md`](qa/test-strategy.md) and the QA results section in each story |

## Flow we followed

```
PDF requirements
   └─ Analyst ──> project brief
        └─ PM ──> PRD (+ decisions for the parts the PDF is missing)
             └─ Architect ──> architecture + foundation code (schema, entities, security)
                  └─ Scrum Master ──> stories E2..E6
                       ├─ Dev: policy & registration (E2)   ┐
                       ├─ Dev: AI recommendations (E3)      │ in parallel, each on its own branch,
                       ├─ Dev: payments & search (E4, E5)   │ each owns separate files
                       └─ Dev: React front end (E6)         ┘
                            └─ QA ──> review against PRD, tests, gate per story
```

## Working agreements

- A story moves `Draft → Ready → In Progress → Review → Done`. Status lives at the top of each story file.
- Developer agents may only touch the files their story lists. Shared files (entities, schema, security)
  belong to the architect; changes go through a note in the story.
- Every story ends with its tests passing and a short "Dev notes" entry: what was built, any deviation.
- QA checks each acceptance criterion and records PASS / CONCERNS / FAIL in the story.
