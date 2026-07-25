# InsuHR — Integrated HR Management System for Insurers

[한국어](./README.md) | **English**

[![build](https://github.com/hjryoo-ai/insu-hr/actions/workflows/build.yml/badge.svg)](https://github.com/hjryoo-ai/insu-hr/actions/workflows/build.yml)

A backend system that manages salaried employees and insurance agents (FCs) on **a single person model**, and synchronizes changes to HR master data to other systems across **three layers — events, a Pull API, and batch files**. A portfolio project built spec-first: the design document was written first, then corrected by the evidence of implementation (v1.0 → v2.3).

`Java 21 LTS` · `Spring Boot 4.1` · `Oracle 23ai` · `Spring Batch 6` · `Flyway V1–V17` · **168 tests (real Oracle Testcontainers)** · `@Disabled 0`

> ⚠️ This is a fictional system built for a portfolio. Law-dependent figures — continuing-education cycle, financial-guarantee amounts, personal-data retention periods — are not hardcoded; they live in a policy table (`TB_POLICY_CONFIG`), and real deployment would require checking the relevant statutes.

---

## Why this domain

An insurer's human resources split into **salaried employees** on labor contracts and **insurance agents** on engagement contracts. Under the Insurance Business Act, agents carry separate management requirements — sales licenses, statutory education, association registration, financial guarantees — and if any one of them breaks, recruiting must be blocked. HR information is also the master data of nearly every in-house system (sales, commissions, payroll, SSO), so propagating changes in order and without loss is half the system. These two problems — turning legal requirements into data, and synchronizing master data — are the heart of the project.

## Core features

- **Person–role model**: one person record keyed on the national ID (encrypted + hashed), with employee and agent roles attached separately. Supports real-world cases such as an agent engagement after an employee resigns.
- **Agent engagement state machine**: candidate → association-registered → active → suspended → terminated → re-engaged. The transition table (7 allowed · 18 forbidden) is a single source of truth in an enum; concurrent transitions are guarded by optimistic locking; 1 transition = 1 history row + 1 event.
- **Recruit-eligibility evaluation**: a **side-effect-free pure function** `evaluate(agentId, asOf)` that combines license, continuing education, financial guarantee, and sanctions, plus a reconciler that performs automatic transitions based only on the evaluation result. Every write to eligibility data triggers re-evaluation.
- **Employee appointments**: draft / confirm / cancel + future-dated reservations. The snapshot is defined not by incremental update but by a **recompute function** → batch idempotency follows from the definition.
- **Three-layer synchronization**: Transactional Outbox → a 2-stage relay (fanout/delivery, a per-subscriber ordering gate, HMAC-signed webhook + Kafka profile) / a watermark-delayed cursor Pull API / checksum-verified snapshot files.
- **Personal-data controls**: AES-256-GCM field encryption (key-versioned), masked responses by default, decryption via POST + reason + access log (same transaction), and anonymizing purge after the retention period (purge ledger + `person.purged` propagation).
- **10 batch jobs**: catching date-boundary transitions, enqueuing expiry notices (unique-key idempotent), consistency checks, snapshot generation, personal-data purge, and more — with `Clock` injection making boundary-date testing possible.

## Architecture

```
 insuhr-api ──┐                         ┌── webhook subscribers (sales / groupware)
 insuhr-batch ┼── Oracle 23ai ── insuhr-relay ──┤
              │   (HR + Outbox            └── Kafka (profile)
              │    + ChangeLog)
              └── Pull API /sync/changes ──── commission / payroll systems
                  snapshot files (daily batch) ──── DW / legacy
```

| Module | Role |
|---|---|
| `insuhr-common` | Framework-agnostic commons (exceptions · masking · crypto utils) — **kept at 0 dependencies** |
| `insuhr-domain` | JPA entities + domain services (state machine · evaluation) + owns Flyway migrations |
| `insuhr-api` | REST API server (JWT/RBAC, org-scope row-level control) |
| `insuhr-batch` | 10 Spring Batch jobs |
| `insuhr-relay` | Outbox relay (fanout → per-subscriber delivery, signing · backoff · retry) |

## Quick start (10-minute demo)

Requirements: JDK 21, Docker

```bash
docker compose up -d oracle          # Oracle 23ai Free — Flyway builds the schema (fresh DB)

# Run each of the three below in a separate terminal (servers run in the foreground)
./gradlew :insuhr-api:bootRun        # 8080 — API server
./gradlew :insuhr-relay:bootRun      # 8081 — Outbox relay
./demo/receiver.sh                   # 9099 — webhook receive dump

./demo/run.sh                        # seed demo account → run Appendix B scenario automatically
```

The flow `run.sh` walks through (design doc [Appendix B](./insuhr-design-spec.md)):

1. Log in → register an agent candidate (duplicate-person check via national-ID hash)
2. Register sales license, registration education, financial guarantee
3. Execute engagement → enter association registration number → **ACTIVE + recruitable**
4. Call account decryption → confirm plaintext + masking + **access log created**
5. The relay fans out 6 Outbox events → delivered as signed webhooks to the receive terminal → all `SENT`

## The design doc and its revision history — the real deliverable of this project

The entire design is managed in a single document, [insuhr-design-spec.md](./insuhr-design-spec.md) (v2.3, 13 chapters + appendices), and **whenever implementation evidence conflicted with the spec, the spec was corrected.** Representative entries from the revision history:

| Revision | What the evidence showed |
|---|---|
| v1.1 | Boot 4 autoconfiguration modularization — `flyway-core` alone **silently skips** migrations (health UP, no tables). `spring-boot-flyway` is required. Testcontainers 2.x artifact renames. |
| v1.2 | Org history = full-snapshot premise confirmed (point-in-time query becomes a single query); UTC convention for TIMESTAMP columns |
| v1.5 | Clock-derivation rule — stored time is Instant (UTC), business date is LocalDate (KST), `LocalDateTime` forbidden + midnight-boundary anchor tests |
| v1.7–1.8 | Relay redesign — a single STATUS can't represent multiple subscribers → event×subscriber delivery-record fanout, (subscriber, aggId) ordering gate |
| v2.0 | **Proved a reviewer's instruction wrong under Batch 6** — a job with an incrementer has `start()` silently discard business parameters. Instruction dropped, corrected to a caller-supplied run.id approach |

## Bug highlights the tests caught

Bugs that sequential tests and code review would have passed — the ones **you can only see by reading state back.**

1. **A rollback reverted the lock count** — a login failure threw, rolling back the transaction and reverting the failure count too → the account would never lock. A test that re-queries the DB after 5 failures caught it; fixed by splitting off with `REQUIRES_NEW`.
2. **A unique-constraint violation poisoned the persistence context** — code that used the constraint as a defensive line and switched to the existing person on violation failed 7 of 8 concurrent registrations (rollback-only). The insert attempt was isolated in its own transaction — the same pattern avoids the problem outright in the Phase 6 fanout via a single `INSERT…WHERE NOT EXISTS`.
3. **The framework silently dropped parameters** — the Spring Batch 6 incrementer + business-parameter combination. Other tests were passing because `targetDate` "happened to be today" → a date-fixed test caught it.

## v1.1 backlog (prioritized)

Things deliberately left out of v1.0 — listed in the same order, with rationale, in design doc [§13.4](./insuhr-design-spec.md#134-v11-백로그-릴리스-후-우선순위).

1. Change-audit AOP + `TB_AUDIT_LOG` — documented as an intentional deferral in [§10.4](./insuhr-design-spec.md). When implemented, exclude `TB_PERSON` sensitive fields from the audit JSON source (a premise of purge consistency).
2. `PUT /auth/password` + password history · 90-day change enforcement (policy values are seeded, endpoint unimplemented)
3. Consumer-side contract tests for the Pull API
4. Relay scale-out — aggId hash partitioning (the current single-instance assumption is stated in §9.2)
