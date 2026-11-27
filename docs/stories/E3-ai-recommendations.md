# E3 AI insurance recommendations

**Status:** Done · **Owner:** Dev agent "ai" · **PRD:** FR-2 (AC-2.1 – AC-2.9), NFR-7

## Story
As a customer, I describe my goals and get up to three explained suggestions, only from products I'm
eligible for and don't already hold.

## Tasks
1. `dto/recommendation/*` records: `RecommendationRequest` (with a class-level "at least one field"
   check), `RecommendationResponse`, `RecommendedProductResponse`.
2. `ai/` package:
   - `InsuranceAdvisor`: wraps `ChatClient` (built from the auto-configured `ChatClient.Builder`). Builds
     the prompt (system + user), calls the model, maps structured output to a record such as
     `AdvisorAnswer(List<AdvisorPick> picks)` where `AdvisorPick(productCode, reason, suitability)`.
     Returns the picks plus model metadata (configured model name, actual model id from response
     metadata, prompt text).
   - `RuleBasedRanker`: deterministic fallback. Score type match, goal keywords → type
     (e.g. health/medical/hospital → HEALTH, family/income/protect/loan → TERM,
     save/wealth/child/education → SAVINGS, retire/pension → RETIREMENT), closeness of term to the
     preferred term. Top 3, suitability by score, reason generated from what matched.
   - Prompt version constant / property `app.ai.prompt-version` (rec-v1).
3. `service/RecommendationService`: the 6-step flow in architecture §8, one `@Transactional` method.
   Model failures of any kind (exceptions, empty list, all codes invalid) fall back to the ranker and
   set `source = RULE_BASED` with a notice.
4. `controller/RecommendationController`: `POST /api/recommendations`.

## Files you own
`ai/**`, `dto/recommendation/**`, `service/RecommendationService.java`,
`controller/RecommendationController.java`, matching tests. You may **add** methods to
`AiRecommendationRequestRepository` / `AiRecommendationItemRepository`. Read-only use of
`InsuranceProductRepository` and `CustomerPolicyRepository` (existing methods; if you need a new one,
write the query inline in your own repository instead of editing theirs).

## Tests required
- `RuleBasedRankerTest`.
- `RecommendationServiceTest` (Mockito, `InsuranceAdvisor` mocked): hallucinated codes are dropped;
  duplicates dropped; held/ineligible products never returned even if the model returns them; model
  exception → RULE_BASED; empty candidates → no model call.
- `RecommendationApiTest` (extends `IntegrationTest`, with `@MockitoBean InsuranceAdvisor`): Aarav's
  request never contains HD-TERM-001; request + items persisted; validation (no inputs → 400, future
  DOB → 400); staff → 403.

## Dev notes
- **Built:** `ai/` (`InsuranceAdvisor`, `RuleBasedRanker`, records `CustomerNeeds`, `AdvisorPick`, `AdvisorAnswer`,
  `AiProperties` for `app.ai.*`), `dto/recommendation/*` (request with class-level `@AtLeastOneInput`),
  `RecommendationService` (one `@Transactional` method, §8 steps 1-6), `RecommendationController`
  (`POST /api/recommendations`). No repository changes were needed.
- **Spring AI 1.1.8 API used** (checked with `javap` on the 1.1.8 jars): `ChatClient.Builder.build()`, then
  `chatClient.prompt().system(String).user(String).call().responseEntity(AdvisorAnswer.class)`, which returns
  `ResponseEntity<ChatResponse, AdvisorAnswer>`. `model_version` = `response().getMetadata().getModel()`
  (null if the provider sends none); `model_name` = `spring.ai.openai.chat.options.model`. User/system text
  is only template-rendered when params are given, so `{}` in goals is safe.
- **Prompt injection:** goals go inside `<customer_goals>` tags with `<`/`>` stripped so they can't close the
  block; the system prompt says to use only listed codes and ignore instructions in goals. Server-side
  filtering (candidate codes only, de-dup, cap 3, non-blank reason and suitability) is the real check.
- **Fallback:** any `RuntimeException`, a null/empty answer, or no valid picks → `RuleBasedRanker`,
  `source = RULE_BASED`, notice shown. If `AI_API_KEY` is blank or `not-configured`, the advisor fails fast
  without a network call.
- **Decisions to review:**
  - RULE_BASED rows store `model_name = rule-based`, `prompt_version = rules-v1`, `model_version` and
    `prompt_text` null (no prompt was used). MODEL rows store `rec-v1` and the system + user text. The JSON
    format instructions Spring AI adds at call time are not part of the saved `prompt_text`.
  - No candidates: a RULE_BASED request with no items is saved and the notice explains why.
  - `@PastOrPresent` checks against the system clock, so the service checks the DOB against the app `Clock`
    too (422 `DATE_OF_BIRTH_IN_FUTURE`; only reachable when the two clocks disagree on the date).
  - The model call runs inside the transaction, as the story asks. With the 30 s read timeout and 2 attempts
    a slow model holds a DB connection for that long; fine at this load, worth revisiting if it grows.
- **Tests:** 32 new, all passing: `RuleBasedRankerTest` 7, `InsuranceAdvisorTest` 3 (real `ChatClient` over a
  canned `ChatModel`: parsing, model id, goals stay inside the block, no call without a key),
  `RecommendationServiceTest` 12, `RecommendationApiTest` 10. Full suite `mvn -q test`: 38 tests, 0 failures.

## QA results
**Gate: CONCERNS** (QA, 25 Nov 2026). Fallback, validation of codes, persistence, no-candidate notice, 400s (no input, future DOB,
goals > 500, term 0/41) and prompt-injection text all behave. Model path checked only through tests (no key on dev).
Open: **QA-1 (Medium)**: eligibility uses the DOB typed into the form, registration uses the profile DOB, so a
recommended product can fail with 422 ENTRY_AGE_NOT_ELIGIBLE. QA-6 (Low): fractional `preferredTermYears` truncated.
AC-2.7: RULE_BASED rows with null `prompt_text`/`model_version` need PM sign-off.
