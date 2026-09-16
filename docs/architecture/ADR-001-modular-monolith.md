# ADR-001: SlickHood remains a modular monolith

Status: Accepted — 2026-08-27

## Decision

SlickHood will remain one deployable Spring Boot backend while business capabilities are separated by owned data, public contracts and durable domain events. A module may be extracted only when measured scaling, security or team-ownership needs justify the operational cost.

## Rules

1. Controllers call application services; they do not access repositories.
2. A module owns its tables and persistence repositories.
3. Cross-module commands use a public contract. Cross-module reactions use versioned domain events.
4. Financial events are written to the transactional outbox in the same transaction as the state change.
5. Event consumers are idempotent because delivery is at least once.
6. External callbacks and background jobs carry a correlation ID and expose safe retry behaviour.
7. No module imports another module's internal implementation package.

The CI rule in `scripts/check-module-boundaries.py` enforces the currently
machine-checkable boundaries: controllers cannot import persistence code,
services cannot depend on controllers, and payment can use only subscription
public contracts. Add a rule before introducing another cross-module dependency.

Operational boundaries are observable through Actuator's authenticated metrics
endpoint. Standard JVM/GC and datasource-pool meters are complemented by
`slickhood.outbox.*` and `slickhood.payment.callback.*` meters. Provider names
and outcomes are low-cardinality tags; customer, payment and invoice identifiers
must never be metric tags.

## Financial boundary

Payment settlement owns marking an invoice paid and emits `invoice.paid.v1`. Subscription consumes that event and owns plan activation. The payment module does not invoke subscription implementation classes. `pms_subscription_payment_completion.invoice_id` and the outbox dedupe key independently prevent duplicate activation.

## Extraction threshold

Extraction requires: a stable public contract, independent data ownership, observable workload pressure or security isolation need, no synchronous distributed transaction, and an operational owner. Notifications, document processing and analytics are likely candidates before core property/lease/invoice data.
