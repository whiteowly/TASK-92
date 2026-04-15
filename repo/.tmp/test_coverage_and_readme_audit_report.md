# Test Coverage Audit

## Backend Endpoint Inventory

Detected project API surface from Spring controllers in:
- `src/main/java/com/civicworks/platform/security/AuthController.java`
- `src/main/java/com/civicworks/platform/config/AdminController.java`
- `src/main/java/com/civicworks/content/api/ContentController.java`
- `src/main/java/com/civicworks/moderation/api/ModerationController.java`
- `src/main/java/com/civicworks/notifications/api/NotificationController.java`
- `src/main/java/com/civicworks/searchanalytics/api/SearchController.java`
- `src/main/java/com/civicworks/searchanalytics/api/ReportController.java`
- `src/main/java/com/civicworks/settlement/api/SettlementController.java`
- `src/main/java/com/civicworks/dispatch/api/DispatchController.java`
- `src/main/java/com/civicworks/billing/api/BillingController.java`
- `src/main/java/com/civicworks/billing/api/AccountController.java`
- `src/main/java/com/civicworks/platform/security/UserResidentIdController.java`

Resolved endpoints (`METHOD + fully resolved PATH`):

1. `POST /api/v1/auth/login`
2. `POST /api/v1/auth/logout`
3. `GET /api/v1/auth/sessions`
4. `POST /api/v1/auth/sessions/{sessionId}/revoke`
5. `GET /api/v1/admin/system-config`
6. `PUT /api/v1/admin/system-config`
7. `GET /api/v1/admin/audit-log`
8. `GET /api/v1/public/content-items`
9. `GET /api/v1/public/content-items/{id}`
10. `POST /api/v1/content-items`
11. `GET /api/v1/content-items`
12. `GET /api/v1/content-items/{id}`
13. `PUT /api/v1/content-items/{id}`
14. `POST /api/v1/content-items/{id}/publish`
15. `POST /api/v1/content-items/{id}/unpublish`
16. `GET /api/v1/content-items/{id}/publish-history`
17. `POST /api/v1/content-items/{contentItemId}/comments`
18. `GET /api/v1/moderation/comments`
19. `POST /api/v1/moderation/comments/{commentId}/actions`
20. `POST /api/v1/moderation/sensitive-words`
21. `PUT /api/v1/moderation/sensitive-words/{id}`
22. `DELETE /api/v1/moderation/sensitive-words/{id}`
23. `GET /api/v1/moderation/sensitive-words`
24. `POST /api/v1/notifications/templates`
25. `PUT /api/v1/notifications/templates/{id}`
26. `GET /api/v1/notifications/templates`
27. `POST /api/v1/notifications/messages`
28. `GET /api/v1/notifications/messages`
29. `POST /api/v1/notifications/messages/{id}/ack`
30. `POST /api/v1/notifications/reminders`
31. `GET /api/v1/notifications/reminders`
32. `GET /api/v1/notifications/outbox`
33. `POST /api/v1/notifications/outbox/{id}/mark-exported`
34. `GET /api/v1/public/search`
35. `GET /api/v1/public/search/typeahead`
36. `GET /api/v1/public/search/recommendations`
37. `GET /api/v1/search`
38. `GET /api/v1/search/typeahead`
39. `GET /api/v1/search/recommendations`
40. `GET /api/v1/search/history`
41. `DELETE /api/v1/search/history`
42. `GET /api/v1/reports/financial`
43. `GET /api/v1/reports/activity`
44. `GET /api/v1/reports/kpis`
45. `GET /api/v1/reports/anomalies`
46. `POST /api/v1/settlements/payments`
47. `POST /api/v1/settlements/payments/{paymentId}/reverse`
48. `POST /api/v1/settlements/shifts/{shiftId}/handover`
49. `GET /api/v1/settlements/discrepancies`
50. `POST /api/v1/settlements/discrepancies/{id}/resolve`
51. `POST /api/v1/dispatch/orders`
52. `GET /api/v1/dispatch/orders`
53. `POST /api/v1/dispatch/orders/{orderId}/grab`
54. `POST /api/v1/dispatch/orders/{orderId}/assign`
55. `POST /api/v1/dispatch/orders/{orderId}/forced-assign`
56. `POST /api/v1/dispatch/orders/{orderId}/driver-response`
57. `PUT /api/v1/dispatch/zones/{zoneId}/capacity-rule`
58. `POST /api/v1/billing/fee-items`
59. `PUT /api/v1/billing/fee-items/{id}`
60. `GET /api/v1/billing/fee-items`
61. `GET /api/v1/billing/policies/due-date`
62. `PUT /api/v1/billing/policies/due-date`
63. `POST /api/v1/billing/runs`
64. `GET /api/v1/billing/runs/{runId}`
65. `GET /api/v1/billing/bills`
66. `GET /api/v1/billing/bills/{billId}`
67. `POST /api/v1/billing/bills/{billId}/discounts`
68. `POST /api/v1/billing/accounts/{accountId}/resident-id`
69. `GET /api/v1/billing/accounts/{accountId}/resident-id`
70. `GET /api/v1/billing/accounts/by-resident-id`
71. `POST /api/v1/users/{userId}/resident-id`
72. `GET /api/v1/users/{userId}/resident-id`
73. `GET /api/v1/users/by-resident-id`

## API Test Mapping Table

Legend:
- `true no-mock HTTP`: Spring app bootstrapped, HTTP request via `TestRestTemplate`, no mock/stub in API test files.
- `unit-only / indirect`: no successful handler-reaching HTTP test for the endpoint.

| Endpoint | Covered | Test type | Test files | Evidence |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | yes | true no-mock HTTP | `AuthApiIT.java` | `AuthApiIT#login_success` |
| `POST /api/v1/auth/logout` | yes | true no-mock HTTP | `AuthApiIT.java` | `AuthApiIT#logout_success` |
| `GET /api/v1/auth/sessions` | yes | true no-mock HTTP | `AuthApiIT.java` | `AuthApiIT#sessions_asAdmin_returns200` |
| `POST /api/v1/auth/sessions/{sessionId}/revoke` | yes | true no-mock HTTP | `AuthApiIT.java` | `AuthApiIT#revokeSession_asAdmin_returns200` |
| `GET /api/v1/admin/system-config` | yes | true no-mock HTTP | `AdminApiIT.java` | `AdminApiIT#getSystemConfig_asAdmin_returns200` |
| `PUT /api/v1/admin/system-config` | yes | true no-mock HTTP | `AdminApiIT.java` | `AdminApiIT#updateSystemConfig_asAdmin_returns200` |
| `GET /api/v1/admin/audit-log` | yes | true no-mock HTTP | `AdminApiIT.java` | `AdminApiIT#getAuditLog_asAdmin_returns200` |
| `GET /api/v1/public/content-items` | yes | true no-mock HTTP | `ContentApiIT.java` | `ContentApiIT#publicListContent_noAuth_returns200` |
| `GET /api/v1/public/content-items/{id}` | yes | true no-mock HTTP | `ContentApiIT.java` | `ContentApiIT#publicGetContent_published_returns200` |
| `POST /api/v1/content-items` | yes | true no-mock HTTP | `ContentApiIT.java` | `ContentApiIT#createContent_asEditor_returns201` |
| `GET /api/v1/content-items` | yes | true no-mock HTTP | `ContentApiIT.java` | `ContentApiIT#listContent_asEditor_returns200` |
| `GET /api/v1/content-items/{id}` | yes | true no-mock HTTP | `ContentApiIT.java` | `ContentApiIT#getContent_asEditor_returns200` |
| `PUT /api/v1/content-items/{id}` | yes | true no-mock HTTP | `ContentApiIT.java` | `ContentApiIT#updateContent_asEditor_returns200` |
| `POST /api/v1/content-items/{id}/publish` | yes | true no-mock HTTP | `ContentApiIT.java` | `ContentApiIT#publishContent_asEditor_returns200` |
| `POST /api/v1/content-items/{id}/unpublish` | yes | true no-mock HTTP | `ContentApiIT.java` | `ContentApiIT#unpublishContent_asEditor_returns200` |
| `GET /api/v1/content-items/{id}/publish-history` | yes | true no-mock HTTP | `ContentApiIT.java` | `ContentApiIT#publishHistory_asEditor_returns200` |
| `POST /api/v1/content-items/{contentItemId}/comments` | yes | true no-mock HTTP | `ModerationApiIT.java` | `ModerationApiIT#createComment_onPublished_returns201` |
| `GET /api/v1/moderation/comments` | yes | true no-mock HTTP | `ModerationApiIT.java` | `ModerationApiIT#listComments_asModerator_returns200` |
| `POST /api/v1/moderation/comments/{commentId}/actions` | yes | true no-mock HTTP | `ModerationApiIT.java` | `ModerationApiIT#moderateComment_approve_returns200` |
| `POST /api/v1/moderation/sensitive-words` | yes | true no-mock HTTP | `ModerationApiIT.java` | `ModerationApiIT#createSensitiveWord_returns201` |
| `PUT /api/v1/moderation/sensitive-words/{id}` | yes | true no-mock HTTP | `ModerationApiIT.java` | `ModerationApiIT#updateSensitiveWord_returns200` |
| `DELETE /api/v1/moderation/sensitive-words/{id}` | yes | true no-mock HTTP | `ModerationApiIT.java` | `ModerationApiIT#deleteSensitiveWord_returns204` |
| `GET /api/v1/moderation/sensitive-words` | yes | true no-mock HTTP | `ModerationApiIT.java` | `ModerationApiIT#listSensitiveWords_asModerator_returns200` |
| `POST /api/v1/notifications/templates` | yes | true no-mock HTTP | `NotificationApiIT.java` | `NotificationApiIT#createTemplate_asAdmin_returns201` |
| `PUT /api/v1/notifications/templates/{id}` | yes | true no-mock HTTP | `NotificationApiIT.java` | `NotificationApiIT#updateTemplate_asAdmin_returns200` |
| `GET /api/v1/notifications/templates` | yes | true no-mock HTTP | `NotificationApiIT.java` | `NotificationApiIT#listTemplates_asAdmin_returns200` |
| `POST /api/v1/notifications/messages` | yes | true no-mock HTTP | `NotificationApiIT.java` | `NotificationApiIT#sendMessage_asAdmin_returns201` |
| `GET /api/v1/notifications/messages` | yes | true no-mock HTTP | `NotificationApiIT.java` | `NotificationApiIT#listMessages_asRecipient_returns200` |
| `POST /api/v1/notifications/messages/{id}/ack` | yes | true no-mock HTTP | `NotificationApiIT.java` | `NotificationApiIT#ackMessage_returns200` |
| `POST /api/v1/notifications/reminders` | yes | true no-mock HTTP | `NotificationApiIT.java` | `NotificationApiIT#createReminder_asAdmin_returns201` |
| `GET /api/v1/notifications/reminders` | yes | true no-mock HTTP | `NotificationApiIT.java` | `NotificationApiIT#listReminders_asAdmin_returns200` |
| `GET /api/v1/notifications/outbox` | yes | true no-mock HTTP | `NotificationApiIT.java` | `NotificationApiIT#listOutbox_asAdmin_returns200` |
| `POST /api/v1/notifications/outbox/{id}/mark-exported` | no | unit-only / indirect | `NotificationApiIT.java` | Only auth rejection tests: `markExported_nonAdmin_returns403`, `markExported_noAuth_returns401` |
| `GET /api/v1/public/search` | yes | true no-mock HTTP | `SearchApiIT.java` | `SearchApiIT#publicSearch_noAuth_returns200` |
| `GET /api/v1/public/search/typeahead` | yes | true no-mock HTTP | `SearchApiIT.java` | `SearchApiIT#publicTypeahead_withLongerQuery_returns200AndSuggestions` |
| `GET /api/v1/public/search/recommendations` | yes | true no-mock HTTP | `SearchApiIT.java` | `SearchApiIT#publicRecommendations_returns200` |
| `GET /api/v1/search` | yes | true no-mock HTTP | `SearchApiIT.java` | `SearchApiIT#authSearch_returns200` |
| `GET /api/v1/search/typeahead` | yes | true no-mock HTTP | `SearchApiIT.java` | `SearchApiIT#authTypeahead_returns200` |
| `GET /api/v1/search/recommendations` | yes | true no-mock HTTP | `SearchApiIT.java` | `SearchApiIT#authRecommendations_returns200` |
| `GET /api/v1/search/history` | yes | true no-mock HTTP | `SearchApiIT.java` | `SearchApiIT#searchHistory_returns200` |
| `DELETE /api/v1/search/history` | yes | true no-mock HTTP | `SearchApiIT.java` | `SearchApiIT#deleteSearchHistory_returns204` |
| `GET /api/v1/reports/financial` | yes | true no-mock HTTP | `ReportApiIT.java` | `ReportApiIT#financialReport_asAdmin_returns200` |
| `GET /api/v1/reports/activity` | yes | true no-mock HTTP | `ReportApiIT.java` | `ReportApiIT#activityReport_asAdmin_returns200` |
| `GET /api/v1/reports/kpis` | yes | true no-mock HTTP | `ReportApiIT.java` | `ReportApiIT#kpiReport_asAdmin_returns200` |
| `GET /api/v1/reports/anomalies` | yes | true no-mock HTTP | `ReportApiIT.java` | `ReportApiIT#anomaliesReport_asAdmin_returns200` |
| `POST /api/v1/settlements/payments` | yes | true no-mock HTTP | `SettlementApiIT.java` | `SettlementApiIT#postPayment_asClerk_returns201` |
| `POST /api/v1/settlements/payments/{paymentId}/reverse` | yes | true no-mock HTTP | `SettlementApiIT.java` | `SettlementApiIT#reversePayment_asClerk_returns200` |
| `POST /api/v1/settlements/shifts/{shiftId}/handover` | yes | true no-mock HTTP | `SettlementApiIT.java` | `SettlementApiIT#shiftHandover_asClerk_returns200` |
| `GET /api/v1/settlements/discrepancies` | yes | true no-mock HTTP | `SettlementApiIT.java` | `SettlementApiIT#listDiscrepancies_asClerk_returns200` |
| `POST /api/v1/settlements/discrepancies/{id}/resolve` | no | unit-only / indirect | `SettlementApiIT.java`, `EndpointAuthorizationContractTest.java` | Only auth rejection tests: `resolveDiscrepancy_nonAdmin_returns403`, `resolveDiscrepancy_noAuth_returns401`; annotation contract only in `EndpointAuthorizationContractTest#discrepancyResolve_requiresSystemAdmin` |
| `POST /api/v1/dispatch/orders` | yes | true no-mock HTTP | `DispatchApiIT.java` | `DispatchApiIT#createOrder_asDispatcher_returns201` |
| `GET /api/v1/dispatch/orders` | yes | true no-mock HTTP | `DispatchApiIT.java` | `DispatchApiIT#listOrders_asDispatcher_seesAll` |
| `POST /api/v1/dispatch/orders/{orderId}/grab` | yes | true no-mock HTTP | `DispatchApiIT.java` | `DispatchApiIT#grabOrder_asDriver_returns200` |
| `POST /api/v1/dispatch/orders/{orderId}/assign` | yes | true no-mock HTTP | `DispatchApiIT.java` | `DispatchApiIT#assignOrder_asDispatcher_returns200` |
| `POST /api/v1/dispatch/orders/{orderId}/forced-assign` | yes | true no-mock HTTP | `DispatchApiIT.java` | `DispatchApiIT#forcedAssign_asDispatcher_returns200` |
| `POST /api/v1/dispatch/orders/{orderId}/driver-response` | yes | true no-mock HTTP | `DispatchApiIT.java` | `DispatchApiIT#driverResponse_accept_returns200` |
| `PUT /api/v1/dispatch/zones/{zoneId}/capacity-rule` | yes | true no-mock HTTP | `DispatchApiIT.java` | `DispatchApiIT#updateCapacityRule_asDispatcher_returns200` |
| `POST /api/v1/billing/fee-items` | yes | true no-mock HTTP | `BillingApiIT.java` | `BillingApiIT#createFeeItem_asClerk_returns201` |
| `PUT /api/v1/billing/fee-items/{id}` | yes | true no-mock HTTP | `BillingApiIT.java` | `BillingApiIT#updateFeeItem_asClerk_returns200` |
| `GET /api/v1/billing/fee-items` | yes | true no-mock HTTP | `BillingApiIT.java` | `BillingApiIT#listFeeItems_asClerk_returns200` |
| `GET /api/v1/billing/policies/due-date` | yes | true no-mock HTTP | `BillingApiIT.java` | `BillingApiIT#getDueDatePolicy_asClerk_returns200` |
| `PUT /api/v1/billing/policies/due-date` | yes | true no-mock HTTP | `BillingApiIT.java` | `BillingApiIT#updateDueDatePolicy_asAdmin_returns200` |
| `POST /api/v1/billing/runs` | yes | true no-mock HTTP | `BillingApiIT.java` | `BillingApiIT#createBillingRun_asClerk_returns201` |
| `GET /api/v1/billing/runs/{runId}` | yes | true no-mock HTTP | `BillingApiIT.java` | `BillingApiIT#getBillingRun_asClerk_returns200` |
| `GET /api/v1/billing/bills` | yes | true no-mock HTTP | `BillingApiIT.java` | `BillingApiIT#listBills_asClerk_returns200` |
| `GET /api/v1/billing/bills/{billId}` | yes | true no-mock HTTP | `BillingApiIT.java` | `BillingApiIT#getBill_asClerk_returns200` |
| `POST /api/v1/billing/bills/{billId}/discounts` | yes | true no-mock HTTP | `BillingApiIT.java` | `BillingApiIT#applyDiscount_asClerk_returns201` |
| `POST /api/v1/billing/accounts/{accountId}/resident-id` | yes | true no-mock HTTP | `AccountApiIT.java` | `AccountApiIT#setResidentId_asAdmin_returns200` |
| `GET /api/v1/billing/accounts/{accountId}/resident-id` | yes | true no-mock HTTP | `AccountApiIT.java` | `AccountApiIT#getResidentId_asAdmin_seesPlaintext` |
| `GET /api/v1/billing/accounts/by-resident-id` | yes | true no-mock HTTP | `AccountApiIT.java` | `AccountApiIT#searchByResidentId_asAdmin_returns200` |
| `POST /api/v1/users/{userId}/resident-id` | yes | true no-mock HTTP | `UserResidentIdApiIT.java` | `UserResidentIdApiIT#setResidentId_asAdmin_returns200` |
| `GET /api/v1/users/{userId}/resident-id` | yes | true no-mock HTTP | `UserResidentIdApiIT.java` | `UserResidentIdApiIT#getResidentId_asAdmin_seesPlaintext` |
| `GET /api/v1/users/by-resident-id` | yes | true no-mock HTTP | `UserResidentIdApiIT.java` | `UserResidentIdApiIT#searchByResidentId_asAdmin_returns200` |

## Coverage Summary

- Total endpoints: **73**
- Endpoints with HTTP tests (any HTTP request exists): **73/73**
- Endpoints with true no-mock handler-reaching HTTP tests: **71/73**
- HTTP coverage %: **100.0%**
- True API coverage %: **97.3%**
- Uncovered for true handler execution: `POST /api/v1/notifications/outbox/{id}/mark-exported`, `POST /api/v1/settlements/discrepancies/{id}/resolve`

## Unit Test Summary

Test files inventory:
- Total Java test files discovered: **45** (`src/test/java/**/*.java`)
- API HTTP integration tests: `src/test/java/com/civicworks/api/*ApiIT.java` + `BaseApiIT.java`
- Non-HTTP tests: security, billing, settlement, dispatch, content, notifications, config, crypto, clock packages

Modules covered by non-HTTP tests (with evidence):
- Controllers (direct invocation/annotation contract): `DispatchController` in `src/test/java/com/civicworks/dispatch/DispatchListAuthzTest.java`, authorization annotation contract in `src/test/java/com/civicworks/platform/security/EndpointAuthorizationContractTest.java`
- Services: `AuthService` (`AuthServiceTest.java`, `AuthServiceRuntimeTtlTest.java`), `DispatchService` (`DispatchCapacityTest.java`, `DispatchAcceptConstraintTest.java`), `NotificationService` (`NotificationOutboxFlowTest.java`), `SystemConfigService` (`SystemConfigServiceTest.java`), billing/settlement policy logic (`BillingCalculatorTest.java`, `LateFeePolicyTest.java`, `SettlementEvenSplitRoundingTest.java`)
- Repositories: mostly mocked in unit tests; exercised with real DB through API IT setup in `BaseApiIT.java` and per-API IT files
- Auth/guards/middleware: `TokenAuthenticationFilterTest.java`, `AuthEntryPointTest.java`, `SecurityIntegrationTest.java`, `EndpointAuthorizationContractTest.java`

Important modules with weak or missing direct unit tests:
- `ModerationService` has no dedicated unit test file (`src/test/java` contains no `ModerationService` test reference)
- `ReportController`/reporting business rules have API happy-path checks but little failure/edge logic coverage (`src/test/java/com/civicworks/api/ReportApiIT.java`)
- `SettlementController#resolveDiscrepancy` and `NotificationController#markExported` lack successful-path handler execution tests (only rejection tests)
- Several tests validate math/logic in isolation rather than service orchestration (e.g., `SettlementAllocationServiceTest.java`, `ContentVisibilityServiceTest.java`)

## Tests Check

API test classification:
1. **True No-Mock HTTP**
   - `src/test/java/com/civicworks/api/BaseApiIT.java` uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `TestRestTemplate`
   - API IT files (`AuthApiIT`, `AdminApiIT`, `AccountApiIT`, `BillingApiIT`, `DispatchApiIT`, `SettlementApiIT`, `ContentApiIT`, `ModerationApiIT`, `NotificationApiIT`, `SearchApiIT`, `ReportApiIT`, `UserResidentIdApiIT`) show real HTTP calls via `rest.exchange(...)`, `postForEntity(...)`
   - No mocks found in `src/test/java/com/civicworks/api/*.java`

2. **HTTP with Mocking**
   - None found for real HTTP endpoint tests.

3. **Non-HTTP (unit/integration without HTTP)**
   - Mockito-heavy service/filter/controller direct-call tests across `src/test/java/com/civicworks/platform/**`, `src/test/java/com/civicworks/dispatch/**`, `src/test/java/com/civicworks/settlement/**`, `src/test/java/com/civicworks/billing/**`, `src/test/java/com/civicworks/content/**`, `src/test/java/com/civicworks/notifications/**`

Mock detection findings (representative, evidence-based):
- `@Mock` repositories/services in `src/test/java/com/civicworks/platform/security/AuthServiceTest.java`
- `mock(AuthService.class)` in `src/test/java/com/civicworks/platform/security/SecurityIntegrationTest.java`
- `mock(...)` of filter dependencies in `src/test/java/com/civicworks/platform/security/TokenAuthenticationFilterTest.java`
- `@Mock` infra deps in `src/test/java/com/civicworks/dispatch/DispatchCapacityTest.java`
- `mock(...)` repos/config in `src/test/java/com/civicworks/notifications/NotificationOutboxFlowTest.java`

Observability check (endpoint/input/response clarity):
- Strong examples: `AccountApiIT#getResidentId_asAdmin_seesPlaintext`, `SearchApiIT#publicTypeahead_withLongerQuery_returns200AndSuggestions`, `SettlementApiIT#postPayment_fullSettlement_amountMismatch_returns422`
- Weak examples (status-only assertions, limited response semantics): `BillingApiIT#getDueDatePolicy_asClerk_returns200`, `SettlementApiIT#shiftHandover_asClerk_returns200`, multiple authorization-only tests across API IT files

Sufficiency assessment:
- Success paths: broad and mostly present across all modules
- Failure cases: strong auth and validation coverage (401/403/400/404/422 present)
- Edge cases: partial; strong in settlement rounding/idempotency, weaker in notifications/reporting operation-specific edge behavior
- Auth/permissions: strong coverage across almost every endpoint
- Integration boundaries: good DB-backed API path coverage; weak on selective endpoint-success gaps noted above
- Assertion depth: mixed (some deep payload checks, many status-only checks)

`run_tests.sh` check:
- Docker-based broad test path: **OK** (`run_tests.sh` uses `docker compose ...` + `docker-compose.test.yml`)
- Local dependency requirement in script itself: **No host Java/Gradle required for broad path**

End-to-end expectation check:
- Project inferred as backend API service (no frontend app artifacts inspected for FE test expectation).
- Full FE↔BE E2E expectation not applicable under backend inference.

## Test Coverage Score (0-100)

**84 / 100**

## Score Rationale

- High endpoint exercise breadth: all 73 endpoints are hit by HTTP tests.
- Strong true no-mock API depth for most endpoints (71/73 handler-reaching).
- Security/auth/validation coverage is broad and explicit.
- Score reduced for two unexecuted success handlers and uneven assertion depth (frequent status-only checks).
- Unit suite is extensive but partially fragmented into isolated logic tests with heavy mocking.

## Key Gaps

1. Missing positive handler-path tests:
   - `POST /api/v1/notifications/outbox/{id}/mark-exported`
   - `POST /api/v1/settlements/discrepancies/{id}/resolve`
2. Several API tests assert only status codes without contract-level response verification.
3. Reporting and moderation internals have less direct unit/service-depth coverage than auth/dispatch/settlement policies.

## Confidence & Assumptions

- Confidence: **High** for endpoint inventory and HTTP test mapping; **Medium** for qualitative sufficiency scoring.
- Assumptions:
  - Only `*Controller.java` classes under `src/main/java` define API endpoints.
  - Coverage was assessed statically; no runtime execution or dynamic route registration inference was used.

---

# README Audit

## Project Type Detection

- README does not explicitly declare one of the required type labels (`backend`, `fullstack`, `web`, `android`, `ios`, `desktop`) at top.
- Inferred type from codebase and README content: **backend** (`Spring Boot + PostgreSQL`, API-first docs).

## High Priority Issues

- Hard-gate startup command string mismatch for strict rule: README uses `docker compose up --build`, but required literal is `docker-compose up` (`README.md:28`).
- Authentication is clearly present, but README provides only one credential pair (`admin/admin123`) and not credentials for all roles (`README.md:181`, `README.md:182`, roles listed at `README.md:257`).
- README test section contains host-JDK dependent optional commands (`./gradlew ...`) which weakens strict “everything Docker-contained” posture (`README.md:119` to `README.md:126`).

## Medium Priority Issues

- Project type label not explicitly declared at top in required taxonomy; only inferred narrative description is present (`README.md:1` to `README.md:4`).
- README claims “unit + standalone-MockMvc” but test sources show `@SpringBootTest` + `TestRestTemplate` and no `MockMvc` usage (`README.md:117`, `src/test/java/com/civicworks/api/BaseApiIT.java:56`).

## Low Priority Issues

- README is long and technically dense; quick-start path exists but critical hard-gate items are not normalized to strict checklist wording.

## Hard Gate Failures

1. **Startup Instructions Gate (Backend/Fullstack)**: FAIL
   - Required: include `docker-compose up`
   - Found: `docker compose up --build` (`README.md:28`)

2. **Demo Credentials Gate (Auth exists)**: FAIL
   - Auth exists (`README.md:7`, `/api/v1/auth/login` in `src/main/java/com/civicworks/platform/security/AuthController.java:26`)
   - Missing per-role credential set for all roles; only admin credential provided (`README.md:181`, `README.md:182`)

3. **Project Type Declaration Gate (strict top declaration)**: FAIL
   - Required explicit type token near top; not present (`README.md:1` to `README.md:4`)

Hard gates that pass:
- README location exists at `repo/README.md`: PASS
- Access method (URL + port) documented: PASS (`README.md:47`)
- Verification method documented (`docker compose ps`, `curl`/health checks): PASS (`README.md:57` to `README.md:83`)
- No prohibited runtime package-install instructions (`npm install`, `pip install`, `apt-get`, manual DB setup) found: PASS

## README Verdict (PASS / PARTIAL PASS / FAIL)

**FAIL**

Rationale: multiple hard-gate failures (startup command literal requirement, demo credentials completeness for auth-enabled system, strict type declaration at top).
