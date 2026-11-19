# Phase 4 WP1 Architecture C: Gate 2 verification response (V1–V4)

| | |
|---|---|
| Verification | [`PHASE-4-WP1-ARCHITECTURE-C-GATE2-VERIFICATION.md`](PHASE-4-WP1-ARCHITECTURE-C-GATE2-VERIFICATION.md) (Codex; verified `095ba3f`; ACCEPT WITH SPECIFIED CORRECTIONS) |
| Baseline | `ef5859e1122fea20a26547ad968a04211c6583df` |
| Revised contract | [`PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md`](PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md), **Draft 3**. Changes are tagged **[G3-V1]**…**[G3-V4]**. |
| Status | Documentation only. DRAFT — NOT ACCEPTED. No implementation, no experiments, no finding closed. The verification text is not altered. No new owner policy decision was needed. |

> **Draft 4 note (2026-11-18).** The Draft 3 verification
> ([`PHASE-4-WP1-ARCHITECTURE-C-DRAFT3-VERIFICATION.md`](PHASE-4-WP1-ARCHITECTURE-C-DRAFT3-VERIFICATION.md)) found
> V1 and V4 partial. Draft 4 **supersedes** these statements below:
> - the label census as proof that no former backend remains;
> - the label-based "exact attribution" for administrative termination;
> - "a spoofed or foreign label can only cause refusal";
> - the connection label as "the one new mechanism";
> - "every row matches exactly one listed class".
>
> In Draft 4, labels are diagnostic only. Reclaim requires E-1 and E-2. Termination requires an independently recorded
> tuple, or the offline drain. Exactly-one is scoped to producer classes. See
> [`PHASE-4-WP1-ARCHITECTURE-C-DRAFT4-CORRECTIONS.md`](PHASE-4-WP1-ARCHITECTURE-C-DRAFT4-CORRECTIONS.md).

## 1. Correction map

| Finding | Contract sections | Response |
|---|---|---|
| **V1** (MAJOR): ownership, reclaim and signalling | §4.1 (G10 premise replaced); §4.3 B-8; §5.3 (contention is not attribution); §5.4 connection attribution label, T-1 (outermost), **T-2** (own exact backend; wait, never signal), T-3 (token-owned O premise; converted findings become Ct → O windows; one owning thread per interval), T-4, **T-6** (executable SQL), traces T-c, T-e, T-f, T-j; **§5.4.1** (O-row lock classes and the no-cycle argument); §5.7; §10.1 (runtime signals nothing); §10.4 (exact-attribution termination, administrative only); §11 rows 3 and 19; Proof 2 (j)–(l); §13 OD-6; §15 | **Premise:** G10's "owned O via a live advisory lock" is replaced by **token-owned O**, established by T-3's interval predicate. Only the current interval's owning thread locks O non-terminally. Every other O-row writer is listed and terminal: token acquire, release and reclaim; stale-receiving CAS; notes; receiver renewal. Destruction over other writers acquires each token first. An unclassified O writer is a contract failure. **Reclaim** is one outermost transaction: `SET LOCAL lock_timeout`; `SELECT … FOR NO KEY UPDATE NOWAIT` on the exact old interval; a check that no backend labelled with the former instance remains; the CAS UPDATE; the event; COMMIT. UPDATE is not described as supporting NOWAIT. Every 55P03 (NOWAIT or `lock_timeout`) is "busy, retry"; nothing is inferred from it. **Attribution:** the existing `ApplicationName` label is extended with the process instance ID. That is a label, not a registry, and a spoofed label can only cause refusal. **Signalling:** the runtime signals no backend, not even its own. The administrative path terminates only an exactly attributed backend (its own recorded attempt, or a labelled backend of a confirmed-gone instance), after revalidating `(pid, backend_start)`, and confirms absence. An unlabelled or unrelated same-role backend is never terminated. |
| **V2** (MAJOR): Flyway checkpoints versus receipts | §7.1 **E-5**; §7.3 (new "history committed, MIGRATED receipt missing" branch); §7.4 step 3 (decision by kind); §9.2 (`reconciled` flag; **atomic-target-by-kind table**; lookup scope); §11 rows 10 and 18; Proof 1 (n), (o); §15 transition list | Native Flyway steps are decided **only** from history, checksums and schema, under exclusion after the backend is confirmed absent. No migration writes a receipt, apart from the control migration's SEED, and no receipt proves a migration. SEED is atomic with the control migration. ARCHIVE_INITIALIZED is atomic with the archive insert and its accepted change history. ENTER_OFFLINE, REBIND and READY are CAS plus receipt. MIGRATED is an aggregate checkpoint written afterwards, never atomic with migrations, and reconciled from the validated history when missing. One permanent Archive ID is preserved; accepted migration semantics are unchanged. |
| **V3** (BLOCKING): cancellation ownership boundary | §5.1 (**closed list of token-free transitions**: stale-RECEIVING abandonment, cancellation request note and job, notes, receiver renewal, the token transactions; EDT list extended); §5.4 T-7; trace **T-i**; §5.8 (cancel request versus **`performCancel` finalization**; retry, attest, release); §11 row 17; Proof 2 (m) | Administrator finalization (`performCancel`) runs wholly inside an interval, even when no bytes move: its observation, its CANCELLED_COPY move, `claimBecomesUnresolved` / `releaseTarget` with CANCELLED, and its staging delete and `markStagingCleaned` [SRC `OperationAdminService` 161–290]. Without the interval it refuses and the request stays pending. Timeout, renewal, late-receiver CAS and user-visible behaviour are unchanged. No new state or table. |
| **V4** (MAJOR): witness completeness | §8.3 rewritten as an **exhaustive classification by row class**, adopting the retained AU-1…AU-8 (§5.6.5 as rebuilt in §5.7.9, plus §5.8.7 and §5.10.6) with this contract's §8.2/§8.4/§8.5 taking precedence; outcome mapping; required passing and failing fixtures; §8.5 heading includes operations | The classification covers: initial INGEST generation g₀ (any number ≥ 1, matching `commitNewContent` [SRC]); later REPAIR generations; superseded generations keeping their creator (INGEST or REPAIR); the generation set; operation target meanings; **both owner types** with forward and reverse ledger and kind consistency; U by writer role W1/W2/W3 [SRC `OperationProcessor` 650–660, `DestructionProcessor` 331–369]; Q by reason, including the legacy DAMAGED_GENERATION (no current writer [SRC]) and recorded-absent intents; settled Q destinations; the pointer; aliases. A row matching no class is UNPROVED. D4-32 global-first refusal is unchanged. Nothing is inferred, merged, fabricated or exempted. |

## 2. Remaining uncertainties

- **Labelling inventory (V1).** The reclaim's safety now depends on every runtime connection being created through
  `DataSources` with the instance label. This is an implementation and inventory obligation, tested in Proof 2 (j).
  If a path bypasses it, a surviving backend of that path could be missed.
- **TCP disconnect timing (V1).** Trace T-f assumes a dead process's sockets close, so its backends end promptly. If
  the host crashes, they persist until TCP keepalive ends them, and the reclaim refuses until then, which is safe. The
  wait length depends on server keepalive settings.
- **O-row writer inventory (V1).** §5.4.1's class table is claimed exhaustive for the known writers. The complete
  inventory remains an implementation obligation; an unclassified writer is a failure.
- **Inherited fixtures (V4).** The witness derivations are retained designs, never executed against realistic
  inherited data (obligation 7).
- **P1, P2 and P3** remain feasibility-dependent.

## 3. Proposed changes to the implementation footprint (future; not authorized)

| Change | Footprint |
|---|---|
| Instance ID in `application_name` | `DataSources` label format, within 63 bytes; a build rule that runtime connections come only from `DataSources` |
| T-6 reclaim | One transaction: `SET LOCAL lock_timeout`, `SELECT … FOR NO KEY UPDATE NOWAIT`, a `pg_stat_activity` label check, the CAS UPDATE, the event |
| No runtime signalling | Removes a capability from the runtime grant inventory (OD-6 simplified) |
| `performCancel` | Wrapped in an interval. Each database step carries the T-3 predicate. Outcomes and messages are unchanged. |
| Administrator retry, attest, release | Run inside an interval, unless the inventory proves a step request-only |
| Converted effect-dependent findings | Short Ct → token-owned O transactions |
| MIGRATED receipt | Written after migrations, with a `reconciled` flag. No per-migration receipt plumbing. |
| Historical admission | Implements the retained AU derivations with this contract's precedence rules. It is read-only. |

Nothing above adds a table, state, controller, journal, epoch, registry or framework.

## 4. Unchanged

D4-31, D4-32 and OD-1…OD-6 and OD-8 are as recorded. F5-1…F5-3 are preserved. WP1-R1, F5-4 and
GAS-P4-002…GAS-P4-005 are OPEN. WP1 is unaccepted, implementation is unauthorized, and WP2 is on HOLD. The next gate
is independent Codex verification of Draft 3.
