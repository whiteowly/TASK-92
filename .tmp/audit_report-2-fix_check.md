# Final Issue Resolution Report (Static Re-Check)

## Overall Verdict
- **Status:** All 9 previously tracked issues are now **Resolved (static evidence)**.
- **Boundary:** This is a static-only verification. Runtime behavior/performance still requires execution.

## Scope
- Reviewed implementation and tests for the 9 original findings.
- Did not run app, Docker, or tests.

## Issue-by-Issue Final Status

1) **Field-level encryption workflow**  
**Final status: Resolved**  
- `UserEntity` now has active service/controller workflows for resident-id encryption/hash and safe views, closing the prior scope gap.
- Evidence: `src/main/java/com/civicworks/platform/security/UserResidentIdService.java:51`, `src/main/java/com/civicworks/platform/security/UserResidentIdController.java:28`, `src/main/java/com/civicworks/platform/security/UserRepository.java:15`, `src/test/java/com/civicworks/platform/security/UserResidentIdServiceTest.java:54`, `src/test/java/com/civicworks/platform/security/UserResidentIdSecurityIT.java:153`

2) **Notification outbox external-channel flow**  
**Final status: Resolved**  
- External channels are config-gated and routed to outbox only; tests cover enabled/disabled behavior.
- Evidence: `src/main/java/com/civicworks/notifications/application/NotificationService.java:85`, `src/main/java/com/civicworks/platform/config/SystemConfigService.java:57`, `src/test/java/com/civicworks/notifications/NotificationOutboxFlowTest.java:77`

3) **Late-fee grace-period off-by-one**  
**Final status: Resolved**  
- Eligibility uses strict `< cutoff` semantics (day 11+), with boundary tests.
- Evidence: `src/main/java/com/civicworks/billing/infra/BillRepository.java:21`, `src/test/java/com/civicworks/billing/LateFeeGracePeriodBoundaryTest.java:71`

4) **FULL settlement semantics**  
**Final status: Resolved**  
- `FULL` settlement now enforces exact current balance.
- Evidence: `src/main/java/com/civicworks/settlement/application/SettlementService.java:70`, `src/test/java/com/civicworks/settlement/SettlementFullSemanticsTest.java:72`

5) **Sensitive-word exact-match policy**  
**Final status: Resolved**  
- Matching logic uses case-insensitive token boundaries instead of substrings; tests pin false-positive cases.
- Evidence: `src/main/java/com/civicworks/moderation/application/CommentFilterService.java:29`, `src/test/java/com/civicworks/platform/CommentFilterServiceTest.java:63`

6) **Runtime config wiring (dead-write risk)**  
**Final status: Resolved**  
- Timezone/session TTL now read from `SystemConfigService` in runtime consumers; refresh semantics documented.
- Evidence: `src/main/java/com/civicworks/platform/clock/MunicipalClock.java:47`, `src/main/java/com/civicworks/platform/security/AuthService.java:57`, `src/test/java/com/civicworks/platform/clock/MunicipalClockRuntimeConfigTest.java:21`, `src/test/java/com/civicworks/platform/security/AuthServiceRuntimeTtlTest.java:69`, `README.md:154`
- Note: Quartz cron timezone restart-bound behavior is explicitly documented, not silent dead config.
- Evidence: `README.md:165`

7) **Settlement rounding policy completeness**  
**Final status: Resolved**  
- Even-split and proportional allocation both enforce nearest-cent-compatible deterministic reconciliation.
- Evidence: `src/main/java/com/civicworks/settlement/application/SettlementService.java:144`, `src/main/java/com/civicworks/settlement/application/SettlementService.java:170`, `src/test/java/com/civicworks/settlement/application/SettlementEvenSplitRoundingTest.java:94`, `src/test/java/com/civicworks/settlement/application/SettlementRoundingPolicyTest.java:88`

8) **HTML sanitization hardening**  
**Final status: Resolved**  
- Regex sanitization replaced by jsoup allowlist sanitizer; regression tests cover script/event/javascript URI vectors.
- Evidence: `src/main/java/com/civicworks/content/application/ContentService.java:199`, `build.gradle:50`, `src/test/java/com/civicworks/content/HtmlSanitizerTest.java:24`

9) **Security integration test depth (401/403/object-level)**  
**Final status: Resolved**  
- Added real filter-chain route enforcement tests and user resident-id security slice tests with 401/403/2xx and safe response shape assertions.
- Evidence: `src/test/java/com/civicworks/platform/security/SecurityRouteEnforcementIT.java:49`, `src/test/java/com/civicworks/platform/security/SecurityRouteEnforcementIT.java:124`, `src/test/java/com/civicworks/platform/security/SecurityRouteEnforcementIT.java:193`, `src/test/java/com/civicworks/platform/security/UserResidentIdSecurityIT.java:46`, `src/test/java/com/civicworks/platform/security/UserResidentIdSecurityIT.java:121`

## Additional Documentation Alignment
- README now documents resident-id encryption coverage for both accounts and users, role visibility, and API matrix.
- Evidence: `README.md:224`, `README.md:231`, `README.md:235`, `README.md:249`

## Remaining Boundary (Static Audit)
- Runtime correctness/performance is **not claimed** from this report because tests/app were not executed in this pass.
