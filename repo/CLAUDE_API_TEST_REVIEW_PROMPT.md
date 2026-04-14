## API Test Reliability Prompt (Blocking)

Blocking task: Fix API test strategy to provide real endpoint confidence.

Do not treat mocked/controller-unit tests as API coverage.

### Requirements

1. Use true integration tests (`@SpringBootTest` + `@AutoConfigureMockMvc` or equivalent) with real app wiring + test DB.
2. Inventory all exposed `/api/v1` endpoints and map each to direct API test coverage.
3. For each endpoint family, test:
   - status
   - headers (including `X-Request-Id` where applicable)
   - body contract
   - `400` validation failures
   - `404` not-found
   - `401/403` auth/authz where applicable
   - conflict/error scenarios where domain rules apply
4. Replace or augment misleading coverage:
   - annotation-reflection-only tests
   - direct controller/service invocation used as API proxy
   - tests with mocked core behavior in API paths
5. Keep unit tests, but clearly separate them from API coverage claims.

### Create or Extend

- `AuthApiIT`
- `AdminApiIT`
- `ContentApiIT`
- `ModerationApiIT`
- `NotificationApiIT`
- `SearchApiIT`
- `ReportApiIT`
- `SettlementApiIT`
- `DispatchApiIT`
- `BillingApiIT`
- `AccountResidentIdApiIT`
- `UserResidentIdApiIT`

Place these under `src/test/java/...` matching package structure.

### Endpoint Scope to Cover Directly

- Auth: `/auth/login`, `/auth/logout`, `/auth/sessions`, `/auth/sessions/{id}/revoke`
- Admin: `/admin/system-config` (`GET/PUT`), `/admin/audit-log`
- Content: all `/public/content-items` + `/content-items` CRUD/publish/history
- Moderation: comments/actions + sensitive-words CRUD/list
- Notifications: templates/messages/ack/reminders/outbox
- Search: public + authenticated search/typeahead/recommendations/history (`GET/DELETE`)
- Reports: `/reports/financial`, `/activity`, `/kpis`, `/anomalies`
- Settlement: `/payments`, `/payments/{id}/reverse`, `/shifts/{id}/handover`, `/discrepancies`, `/discrepancies/{id}/resolve`
- Dispatch: `/orders`, `/orders/{id}/grab|assign|forced-assign|driver-response`, `/zones/{id}/capacity-rule`
- Billing: fee-items, due-date policy, runs, bills, discounts
- Account resident-id endpoints
- User resident-id endpoints

### Deliverables

1. Coverage matrix BEFORE and AFTER:
   - `[Endpoint] [true integration | mocked/simulated | none] [notes]`
2. Implemented test files and key scenarios added.
3. Remaining gaps/risks (if any).
4. Commands run + summarized test results.

### Acceptance Bar

- No endpoint remains at `none`.
- No critical endpoint relies only on mocked/simulated API tests.
- Real request-to-response behavior is validated for all endpoint families.
