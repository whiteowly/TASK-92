# Test Coverage Audit

## Backend Endpoint Inventory

Endpoint source (controller mappings):
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

Resolved endpoint count: **73** unique `METHOD + PATH`.

Resolved endpoint inventory:
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

Static HTTP harness evidence:
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` in `src/test/java/com/civicworks/api/BaseApiIT.java:56`
- `TestRestTemplate` field in `src/test/java/com/civicworks/api/BaseApiIT.java:62`
- Real HTTP requests in API IT helpers (`postForEntity`, `rest.exchange`) in `src/test/java/com/civicworks/api/BaseApiIT.java:99`, `src/test/java/com/civicworks/api/BaseApiIT.java:132`

Per-endpoint coverage mapping (strict):

| Endpoint | Covered | Test type | Test files | Evidence |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | yes | true no-mock HTTP | `AuthApiIT.java`, `DemoCredentialsLoginIT.java` | login requests at `src/test/java/com/civicworks/api/DemoCredentialsLoginIT.java:37` |
| `POST /api/v1/auth/logout` | yes | true no-mock HTTP | `AuthApiIT.java` | endpoint exercised in auth IT file (`/api/v1/auth/logout`) |
| `GET /api/v1/auth/sessions` | yes | true no-mock HTTP | `AuthApiIT.java` | endpoint exercised in auth IT file (`/api/v1/auth/sessions`) |
| `POST /api/v1/auth/sessions/{sessionId}/revoke` | yes | true no-mock HTTP | `AuthApiIT.java` | endpoint exercised in auth IT file (`/api/v1/auth/sessions/{id}/revoke`) |
| `GET /api/v1/admin/system-config` | yes | true no-mock HTTP | `AdminApiIT.java` | endpoint exercised in admin IT file (`/api/v1/admin/system-config`) |
| `PUT /api/v1/admin/system-config` | yes | true no-mock HTTP | `AdminApiIT.java` | endpoint exercised in admin IT file (`/api/v1/admin/system-config`) |
| `GET /api/v1/admin/audit-log` | yes | true no-mock HTTP | `AdminApiIT.java` | endpoint exercised in admin IT file (`/api/v1/admin/audit-log`) |
| `GET /api/v1/public/content-items` | yes | true no-mock HTTP | `ContentApiIT.java` | endpoint exercised in content IT file |
| `GET /api/v1/public/content-items/{id}` | yes | true no-mock HTTP | `ContentApiIT.java` | endpoint exercised in content IT file |
| `POST /api/v1/content-items` | yes | true no-mock HTTP | `ContentApiIT.java` | endpoint exercised in content IT file |
| `GET /api/v1/content-items` | yes | true no-mock HTTP | `ContentApiIT.java` | endpoint exercised in content IT file |
| `GET /api/v1/content-items/{id}` | yes | true no-mock HTTP | `ContentApiIT.java` | endpoint exercised in content IT file |
| `PUT /api/v1/content-items/{id}` | yes | true no-mock HTTP | `ContentApiIT.java` | endpoint exercised in content IT file |
| `POST /api/v1/content-items/{id}/publish` | yes | true no-mock HTTP | `ContentApiIT.java` | endpoint exercised in content IT file |
| `POST /api/v1/content-items/{id}/unpublish` | yes | true no-mock HTTP | `ContentApiIT.java` | endpoint exercised in content IT file |
| `GET /api/v1/content-items/{id}/publish-history` | yes | true no-mock HTTP | `ContentApiIT.java` | endpoint exercised in content IT file |
| `POST /api/v1/content-items/{contentItemId}/comments` | yes | true no-mock HTTP | `ModerationApiIT.java` | endpoint exercised in moderation IT file |
| `GET /api/v1/moderation/comments` | yes | true no-mock HTTP | `ModerationApiIT.java` | endpoint exercised in moderation IT file |
| `POST /api/v1/moderation/comments/{commentId}/actions` | yes | true no-mock HTTP | `ModerationApiIT.java` | endpoint exercised in moderation IT file |
| `POST /api/v1/moderation/sensitive-words` | yes | true no-mock HTTP | `ModerationApiIT.java` | endpoint exercised in moderation IT file |
| `PUT /api/v1/moderation/sensitive-words/{id}` | yes | true no-mock HTTP | `ModerationApiIT.java` | endpoint exercised in moderation IT file |
| `DELETE /api/v1/moderation/sensitive-words/{id}` | yes | true no-mock HTTP | `ModerationApiIT.java` | endpoint exercised in moderation IT file |
| `GET /api/v1/moderation/sensitive-words` | yes | true no-mock HTTP | `ModerationApiIT.java` | endpoint exercised in moderation IT file |
| `POST /api/v1/notifications/templates` | yes | true no-mock HTTP | `NotificationApiIT.java` | `src/test/java/com/civicworks/api/NotificationApiIT.java:38` |
| `PUT /api/v1/notifications/templates/{id}` | yes | true no-mock HTTP | `NotificationApiIT.java` | `src/test/java/com/civicworks/api/NotificationApiIT.java:83` |
| `GET /api/v1/notifications/templates` | yes | true no-mock HTTP | `NotificationApiIT.java` | `src/test/java/com/civicworks/api/NotificationApiIT.java:102` |
| `POST /api/v1/notifications/messages` | yes | true no-mock HTTP | `NotificationApiIT.java` | `src/test/java/com/civicworks/api/NotificationApiIT.java:119` |
| `GET /api/v1/notifications/messages` | yes | true no-mock HTTP | `NotificationApiIT.java` | `src/test/java/com/civicworks/api/NotificationApiIT.java:156` |
| `POST /api/v1/notifications/messages/{id}/ack` | yes | true no-mock HTTP | `NotificationApiIT.java` | `src/test/java/com/civicworks/api/NotificationApiIT.java:173` |
| `POST /api/v1/notifications/reminders` | yes | true no-mock HTTP | `NotificationApiIT.java` | `src/test/java/com/civicworks/api/NotificationApiIT.java:191` |
| `GET /api/v1/notifications/reminders` | yes | true no-mock HTTP | `NotificationApiIT.java` | `src/test/java/com/civicworks/api/NotificationApiIT.java:223` |
| `GET /api/v1/notifications/outbox` | yes | true no-mock HTTP | `NotificationApiIT.java` | `src/test/java/com/civicworks/api/NotificationApiIT.java:240` |
| `POST /api/v1/notifications/outbox/{id}/mark-exported` | yes | true no-mock HTTP | `NotificationApiIT.java` | positive path `src/test/java/com/civicworks/api/NotificationApiIT.java:270` |
| `GET /api/v1/public/search` | yes | true no-mock HTTP | `SearchApiIT.java` | `src/test/java/com/civicworks/api/SearchApiIT.java:37` |
| `GET /api/v1/public/search/typeahead` | yes | true no-mock HTTP | `SearchApiIT.java` | `src/test/java/com/civicworks/api/SearchApiIT.java:58` |
| `GET /api/v1/public/search/recommendations` | yes | true no-mock HTTP | `SearchApiIT.java` | `src/test/java/com/civicworks/api/SearchApiIT.java:97` |
| `GET /api/v1/search` | yes | true no-mock HTTP | `SearchApiIT.java` | `src/test/java/com/civicworks/api/SearchApiIT.java:105` |
| `GET /api/v1/search/typeahead` | yes | true no-mock HTTP | `SearchApiIT.java` | `src/test/java/com/civicworks/api/SearchApiIT.java:129` |
| `GET /api/v1/search/recommendations` | yes | true no-mock HTTP | `SearchApiIT.java` | `src/test/java/com/civicworks/api/SearchApiIT.java:144` |
| `GET /api/v1/search/history` | yes | true no-mock HTTP | `SearchApiIT.java` | `src/test/java/com/civicworks/api/SearchApiIT.java:153` |
| `DELETE /api/v1/search/history` | yes | true no-mock HTTP | `SearchApiIT.java` | `src/test/java/com/civicworks/api/SearchApiIT.java:172` |
| `GET /api/v1/reports/financial` | yes | true no-mock HTTP | `ReportApiIT.java` | endpoint exercised in report IT file |
| `GET /api/v1/reports/activity` | yes | true no-mock HTTP | `ReportApiIT.java` | endpoint exercised in report IT file |
| `GET /api/v1/reports/kpis` | yes | true no-mock HTTP | `ReportApiIT.java` | endpoint exercised in report IT file |
| `GET /api/v1/reports/anomalies` | yes | true no-mock HTTP | `ReportApiIT.java` | endpoint exercised in report IT file |
| `POST /api/v1/settlements/payments` | yes | true no-mock HTTP | `SettlementApiIT.java` | `src/test/java/com/civicworks/api/SettlementApiIT.java:54` |
| `POST /api/v1/settlements/payments/{paymentId}/reverse` | yes | true no-mock HTTP | `SettlementApiIT.java` | `src/test/java/com/civicworks/api/SettlementApiIT.java:167` |
| `POST /api/v1/settlements/shifts/{shiftId}/handover` | yes | true no-mock HTTP | `SettlementApiIT.java` | `src/test/java/com/civicworks/api/SettlementApiIT.java:195` |
| `GET /api/v1/settlements/discrepancies` | yes | true no-mock HTTP | `SettlementApiIT.java` | `src/test/java/com/civicworks/api/SettlementApiIT.java:230` |
| `POST /api/v1/settlements/discrepancies/{id}/resolve` | yes | true no-mock HTTP | `SettlementApiIT.java` | positive path `src/test/java/com/civicworks/api/SettlementApiIT.java:282` |
| `POST /api/v1/dispatch/orders` | yes | true no-mock HTTP | `DispatchApiIT.java` | endpoint exercised in dispatch IT file |
| `GET /api/v1/dispatch/orders` | yes | true no-mock HTTP | `DispatchApiIT.java` | endpoint exercised in dispatch IT file |
| `POST /api/v1/dispatch/orders/{orderId}/grab` | yes | true no-mock HTTP | `DispatchApiIT.java` | endpoint exercised in dispatch IT file |
| `POST /api/v1/dispatch/orders/{orderId}/assign` | yes | true no-mock HTTP | `DispatchApiIT.java` | endpoint exercised in dispatch IT file |
| `POST /api/v1/dispatch/orders/{orderId}/forced-assign` | yes | true no-mock HTTP | `DispatchApiIT.java` | endpoint exercised in dispatch IT file |
| `POST /api/v1/dispatch/orders/{orderId}/driver-response` | yes | true no-mock HTTP | `DispatchApiIT.java` | endpoint exercised in dispatch IT file |
| `PUT /api/v1/dispatch/zones/{zoneId}/capacity-rule` | yes | true no-mock HTTP | `DispatchApiIT.java` | endpoint exercised in dispatch IT file |
| `POST /api/v1/billing/fee-items` | yes | true no-mock HTTP | `BillingApiIT.java` | `src/test/java/com/civicworks/api/BillingApiIT.java:46` |
| `PUT /api/v1/billing/fee-items/{id}` | yes | true no-mock HTTP | `BillingApiIT.java` | endpoint exercised in billing IT file |
| `GET /api/v1/billing/fee-items` | yes | true no-mock HTTP | `BillingApiIT.java` | endpoint exercised in billing IT file |
| `GET /api/v1/billing/policies/due-date` | yes | true no-mock HTTP | `BillingApiIT.java` | endpoint exercised in billing IT file |
| `PUT /api/v1/billing/policies/due-date` | yes | true no-mock HTTP | `BillingApiIT.java` | endpoint exercised in billing IT file |
| `POST /api/v1/billing/runs` | yes | true no-mock HTTP | `BillingApiIT.java` | endpoint exercised in billing IT file |
| `GET /api/v1/billing/runs/{runId}` | yes | true no-mock HTTP | `BillingApiIT.java` | endpoint exercised in billing IT file |
| `GET /api/v1/billing/bills` | yes | true no-mock HTTP | `BillingApiIT.java` | endpoint exercised in billing IT file |
| `GET /api/v1/billing/bills/{billId}` | yes | true no-mock HTTP | `BillingApiIT.java` | endpoint exercised in billing IT file |
| `POST /api/v1/billing/bills/{billId}/discounts` | yes | true no-mock HTTP | `BillingApiIT.java` | endpoint exercised in billing IT file |
| `POST /api/v1/billing/accounts/{accountId}/resident-id` | yes | true no-mock HTTP | `AccountApiIT.java` | endpoint exercised in account IT file |
| `GET /api/v1/billing/accounts/{accountId}/resident-id` | yes | true no-mock HTTP | `AccountApiIT.java` | endpoint exercised in account IT file |
| `GET /api/v1/billing/accounts/by-resident-id` | yes | true no-mock HTTP | `AccountApiIT.java` | endpoint exercised in account IT file |
| `POST /api/v1/users/{userId}/resident-id` | yes | true no-mock HTTP | `UserResidentIdApiIT.java` | endpoint exercised in user-resident-id IT file |
| `GET /api/v1/users/{userId}/resident-id` | yes | true no-mock HTTP | `UserResidentIdApiIT.java` | endpoint exercised in user-resident-id IT file |
| `GET /api/v1/users/by-resident-id` | yes | true no-mock HTTP | `UserResidentIdApiIT.java` | endpoint exercised in user-resident-id IT file |

## Coverage Summary

- Total endpoints: **73**
- Endpoints with HTTP tests: **73**
- Endpoints with TRUE no-mock tests: **73**
- HTTP coverage %: **100.0%**
- True API coverage %: **100.0%**

## Unit Test Summary

Static test inventory:
- Java test files under `src/test/java`: **48**
- API IT files under `src/test/java/com/civicworks/api`: **14**

Modules covered (non-HTTP + HTTP):
- Controllers/contracts: `EndpointAuthorizationContractTest`, `DispatchListAuthzTest`
- Services: auth, moderation, notifications, billing, dispatch, settlement, analytics
- Repositories: exercised by API IT setup/verification via injected repositories in `BaseApiIT`, `NotificationApiIT`, `SettlementApiIT`
- Auth/guards/middleware: `TokenAuthenticationFilterTest`, `AuthEntryPointTest`, `SecurityIntegrationTest`

Important modules with weaker direct unit depth:
- Report internals behind `ReportController` (more API-level than dedicated rule-level unit tests): `src/test/java/com/civicworks/api/ReportApiIT.java`

## Tests Check

API test classification:
1. True No-Mock HTTP
   - `src/test/java/com/civicworks/api/*IT.java` using bootstrapped app and `TestRestTemplate`
2. HTTP with Mocking
   - None detected in API IT directory
3. Non-HTTP tests
   - Unit/integration without HTTP under `src/test/java/com/civicworks/**` outside API ITs

Mock detection (strict):
- API IT scope: no `@MockBean`, `@Mock`, `mock(...)`, `Mockito`, `MockMvc` detected.
- Non-HTTP scope has expected mocking (e.g., `AuthServiceTest`, `DispatchCapacityTest`, `NotificationOutboxFlowTest`, `TokenAuthenticationFilterTest`).

Observability check:
- Strong endpoint/input/response visibility for critical operations with persisted-state checks:
  - `markExported_asAdmin_returns200_andMarksEntryExported` in `src/test/java/com/civicworks/api/NotificationApiIT.java:270`
  - `resolveDiscrepancy_asAdmin_returns200_andTransitionsCaseToResolved` in `src/test/java/com/civicworks/api/SettlementApiIT.java:282`
- Mixed depth elsewhere: several tests remain status-centric.

Quality & sufficiency:
- Success paths: broad
- Failure/validation/auth paths: broad (401/403/404/422 patterns present)
- Edge cases: strong in billing/settlement policy tests, moderate elsewhere
- Integration boundaries: real HTTP + DB-backed IT coverage present

`run_tests.sh` static check:
- Docker-based broad runner: **OK** (`run_tests.sh`, `docker-compose.test.yml`)
- Local dependency requirement in broad path: **none**

End-to-end expectations:
- Project type is backend; full FE↔BE E2E not expected.

## Test Coverage Score (0-100)

**92 / 100**

## Score Rationale

- Full endpoint coverage with true no-mock HTTP tests.
- Strong security/authorization and failure-case coverage.
- Score reduced for uneven assertion depth in some endpoint tests and thinner direct report-rule unit coverage.

## Key Gaps

1. Increase payload-level assertions for status-only tests.
2. Add deeper unit tests around reporting/business-rule internals.

## Confidence & Assumptions

- Confidence: **high** on endpoint inventory, mapping, and classification.
- Assumptions: only controller mappings define public API surface; audit is static-only.

**Test Coverage Verdict: PASS**

---

# README Audit

## High Priority Issues

- None blocking under strict gates.

## Medium Priority Issues

- README is extensive; core compliance items are spread across sections (can slow manual review).

## Low Priority Issues

- Credential section uses a shared demo password for all roles (acceptable for dev/demo, should remain explicitly non-production).

## Hard Gate Failures

- None.

## Hard Gate Checks (Evidence)

- Project type declaration at top: PASS (`README.md:3`)
- Required startup instruction includes `docker-compose up`: PASS (`README.md:56`)
- Access method includes URL + port: PASS (`README.md:75`, `README.md:77`)
- Verification method documented (health checks via docker/curl/wget): PASS (`README.md:89` to `README.md:107`)
- Environment rules (Docker-contained): PASS
  - no `npm install` / `pip install` / `apt-get`
  - testing guidance uses Dockerized runner (`README.md:127`, `README.md:138` to `README.md:149`)
- Auth conditional credential gate: PASS
  - auth exists in app (`src/main/java/com/civicworks/platform/security/AuthController.java:26`)
  - README provides username/password for all roles (`README.md:155` to `README.md:163`)
  - seed evidence for all roles exists: `src/main/resources/db/migration/V6__demo_users_per_role.sql`
- README location: PASS (`repo/README.md`)
- Formatting/readability: PASS (clean markdown with sections/tables/code blocks)

## README Verdict (PASS / PARTIAL PASS / FAIL)

**PASS**

---

## Final Combined Verdict

- **Test Coverage Audit:** PASS
- **README Audit:** PASS
