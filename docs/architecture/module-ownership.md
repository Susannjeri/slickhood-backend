# SlickHood module ownership

| Module | Owns | Publishes | Consumes / permitted dependencies |
|---|---|---|---|
| Identity & access | users, roles, role assignments, authentication | user/role lifecycle | none; shared security policy |
| Property & leasing | properties, units, tenants, leases | lease and tenancy lifecycle | identity; billing contract |
| Estate | ownership history, service charges | ownership changes | property; billing contract; documents |
| Sales | sale pipeline and completion | sale completed | property; estate ownership contract; documents |
| Billing & invoices | invoices and their settlement state | `invoice.paid.v1` | accounts; payment providers |
| Payments | provider requests, callbacks, reconciliation records | payment observations | billing settlement contract |
| Subscriptions | plans, features, quotas, user subscriptions | subscription lifecycle | `invoice.paid.v1`; identity role query |
| Documents | templates, versions, signatures, vault references | document lifecycle | identity and scoped entity references |
| Notifications | email/SMS/in-app delivery attempts | delivery result | domain events only |
| Marketplace | provider profiles, services, bookings, ratings | booking lifecycle | identity; billing contract |
| Wealth | assets, values, liabilities, cash flow, obligations, goals | wealth lifecycle (future) | read contracts for property occupancy and invoice arrears |

Legacy repository access that crosses these intended boundaries is technical debt to remove incrementally; no new cross-module repository dependency is permitted.
