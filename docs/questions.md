## Business Logic Questions Log

### 1. Search Scope and the Meaning of `origin`
- Question: Which records are searchable, and what does `origin` mean in this domain? The prompt requires PostgreSQL full-text search, typeahead, filters (`category`, `price range`, `origin`), recommendations, and per-user search history retention, but it does not name the searchable record set or define `origin`.
- My Understanding: Search and recommendation behavior must at minimum cover the public content/resource catalog surface — news, policies, events, and classes. The implementation must not hard-code a search architecture that blocks other prompt-faithful searchable records from participating later. `category` maps to content tags/category metadata, `price range` applies to priced events/classes only, and `origin` is locally stored source/origin metadata on the searchable record (e.g. municipal department, community source, or other configured origin label).
- Solution: Implement a shared search capability whose mandatory baseline surface is publishable content/resources, with extensible support so additional prompt-faithful record types can participate without reworking the contract. Treat `category`, `price range`, and `origin` as defined above. This preserves the clearly implied public catalog search surface and the requested filters without silently forbidding broader prompt-faithful search participation where the data model supports it.

### 2. Local-Time Scheduling Semantics
- Question: Is "local time" a per-user concept or a single municipality-wide operational timezone? The prompt requires scheduled publishing in local time and billing generation at 12:05 AM on the cycle date without specifying which.
- My Understanding: The system is explicitly single-node and fully offline for local municipal operations, so time-driven workflows should run against one configured municipal/local timezone rather than per-user timezone logic.
- Solution: Store and use a single platform timezone in system configuration. Scheduled content publication, billing runs, grace-period cutoff, late-fee evaluation, reminder scheduling, and end-of-day reporting all use that timezone consistently. A single operational timezone is the strongest prompt-faithful default for a single-node offline deployment.

### 3. Even-Split Settlement Rounding
- Question: How should remainder cents be assigned when an even split does not divide perfectly? The prompt requires split/even-split settlement with rounding to the nearest $0.01 and proportional discount allocation, but it does not define residual-cent handling.
- My Understanding: The split algorithm needs deterministic penny allocation so the posted settlement total always matches the bill total after discounts. Accounting correctness and reproducible reversals depend on this being deterministic.
- Solution: Round each split to cents and allocate any residual cent difference deterministically from the earliest split line forward until the exact total is reached. Apply discounts proportionally before final cent reconciliation. This preserves accounting correctness, keeps reversals reproducible, and satisfies the prompt without inventing a separate balancing workflow.

### 4. Offline Outbound Notification Channels
- Question: Are stub network senders acceptable in development for email/SMS/IM channels? The prompt requires these channels to be configurable but disabled by default, and says they may only write to an outbox table for later manual export.
- My Understanding: No code path should perform actual network delivery in any environment for this project baseline. Stub senders would create misleading fake-online behavior inconsistent with the offline requirement.
- Solution: Implement in-app messages and reminders as first-class local records. Model external channels as configuration plus outbox generation only, with disabled-by-default settings and no live sender integration. This fully honors the offline requirement and avoids creating misleading fake-online behavior.

### 5. Late-Fee Application Timing
- Question: At what exact moment does a late fee become eligible? The prompt says late fees apply at 5% after a 10-day grace period, capped at $50 per bill, but it does not pin the eligibility moment.
- My Understanding: Late-fee evaluation should begin at the start of local day 11 after the due date. Ambiguous partial-day handling would create non-deterministic billing outcomes.
- Solution: Treat the grace period as ten full local calendar days after the due date. The first late-fee posting opportunity is 12:05 AM local time on day 11, and the total late-fee amount applied to a bill may not exceed $50. This matches the prompt's existing scheduled-job style, keeps timing deterministic, and avoids ambiguous partial-day handling.
