# Issue Resolution Report (Targeted Follow-up)

Scope: static re-check of the previously reported 7 issues from `.tmp/delivery-architecture-audit-final-rerun.md`.

## Overall
- **Conclusion:** all 7 previously listed issues are now **resolved statically**.
- **Boundary:** runtime behavior is **not executed**; authorization enforcement and DB migration application remain manual/runtime verification items.

## Per-Issue Status

1) Forced-dispatch capacity bypass
- **Previous finding:** forced assign bypassed zone cap.
- **Status now:** **Resolved**
- **Evidence:** `src/main/java/com/civicworks/dispatch/application/DispatchService.java:127`, `src/main/java/com/civicworks/dispatch/application/DispatchService.java:130`
- **Test evidence:** `src/test/java/com/civicworks/dispatch/DispatchCapacityTest.java:115`

2) Fee-item setup did not expose `taxable_flag`
- **Previous finding:** DTO/controller missed prompt-required setup field.
- **Status now:** **Resolved**
- **Evidence:** `src/main/java/com/civicworks/billing/api/BillingController.java:42`, `src/main/java/com/civicworks/billing/api/BillingController.java:56`, `src/main/java/com/civicworks/billing/api/BillingController.java:126`
- **Service persistence evidence:** `src/main/java/com/civicworks/billing/application/BillingService.java:76`
- **Test evidence:** `src/test/java/com/civicworks/billing/FeeItemTaxableFlagTest.java:53`

3) Account resident identifier lacked full implementation path
- **Previous finding:** schema/entity/service existed but no API wiring.
- **Status now:** **Resolved**
- **API evidence:** `src/main/java/com/civicworks/billing/api/AccountController.java:33`, `src/main/java/com/civicworks/billing/api/AccountController.java:46`, `src/main/java/com/civicworks/billing/api/AccountController.java:61`
- **Protection evidence (encrypt/hash/masked/full view):** `src/main/java/com/civicworks/billing/application/AccountService.java:41`, `src/main/java/com/civicworks/billing/application/AccountService.java:76`
- **Schema evidence:** `src/main/resources/db/migration/V4__account_resident_id.sql:8`
- **Test evidence:** `src/test/java/com/civicworks/billing/AccountControllerTest.java:93`, `src/test/java/com/civicworks/billing/AccountResidentIdTest.java:58`

4) Sensitive-word matching was substring-based
- **Previous finding:** violated exact-word semantics.
- **Status now:** **Resolved**
- **Evidence:** `src/main/java/com/civicworks/moderation/application/CommentFilterService.java:29`
- **Test evidence:** `src/test/java/com/civicworks/platform/CommentFilterServiceTest.java:63`

5) HTML sanitization was regex-only
- **Previous finding:** weak sanitization semantics.
- **Status now:** **Resolved**
- **Evidence:** `src/main/java/com/civicworks/content/application/ContentService.java:16`, `src/main/java/com/civicworks/content/application/ContentService.java:197`, `src/main/java/com/civicworks/content/application/ContentService.java:202`
- **Test evidence:** `src/test/java/com/civicworks/content/HtmlSanitizerTest.java:24`

6) Payment timestamp semantics lacked `received_at`
- **Previous finding:** only `created_at` present.
- **Status now:** **Resolved**
- **Schema evidence:** `src/main/resources/db/migration/V4__account_resident_id.sql:17`
- **Entity evidence:** `src/main/java/com/civicworks/settlement/domain/Payment.java:28`, `src/main/java/com/civicworks/settlement/domain/Payment.java:32`
- **Test evidence:** `src/test/java/com/civicworks/settlement/PaymentReceivedAtTest.java:27`

7) Backup extension/format mismatch
- **Previous finding:** file named `.sql.gz` while using pg_dump custom format.
- **Status now:** **Resolved**
- **Evidence:** `src/main/java/com/civicworks/ops/application/BackupService.java:35`, `src/main/java/com/civicworks/ops/application/BackupService.java:38`, `src/main/java/com/civicworks/ops/application/BackupService.java:54`

## Manual Verification Required
- `@PreAuthorize` restrictions are present, but full enforcement in runtime filter chain is still a manual/runtime verification item.
  - Evidence references: `src/main/java/com/civicworks/billing/api/AccountController.java:34`, `src/main/java/com/civicworks/billing/api/AccountController.java:47`, `src/main/java/com/civicworks/billing/api/AccountController.java:62`
- Flyway V4 migration application on an upgraded environment requires runtime DB migration execution verification.
  - Evidence reference: `src/main/resources/db/migration/V4__account_resident_id.sql:1`
