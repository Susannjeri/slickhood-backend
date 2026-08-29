# Service extraction readiness

Extraction is not the current objective. This scorecard identifies the work required before any module can safely leave the monolith.

| Candidate | Current readiness | Why / remaining gate |
|---|---:|---|
| Notifications | Medium | Already asynchronous; formalize domain-event inputs, delivery idempotency and dead-letter operations. |
| Document generation and vault | Medium | Storage is external; separate metadata ownership, malware scanning and job status contracts. |
| Analytics / Wealth projections | Medium | Mostly read-oriented; replace direct unit/invoice repositories with query contracts or read models. |
| Payments | Low–Medium | Provider adapters are isolated and settlement now emits a durable event; complete reconciliation operations and remove remaining shared invoice internals before extraction. |
| Subscriptions | Low–Medium | Paid activation consumes a versioned event; replace remaining invoice and unit repository reads with public query contracts. |
| Property, leasing and invoicing | Low | Strong consistency and shared workflows make extraction expensive; keep together until clear scale or ownership pressure exists. |

## Mandatory gates

- Stable versioned commands/events and consumer compatibility tests.
- Exclusive table ownership with no cross-service joins.
- Idempotent commands, callbacks and consumers.
- Reconciliation and replay tooling.
- Metrics, traces, alerts and an operational owner.
- No request that requires a synchronous distributed transaction.
