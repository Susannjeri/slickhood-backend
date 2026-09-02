# Slickhood Tax Assist

Tax Assist separates private estimates from external tax transmission.

## Supported calculations

- `KENYA_MRI`: resident taxpayer, Kenyan residential property, annual gross rent above KES 288,000 and not above KES 15,000,000. The seeded rate is 7.5% of gross rent received.
- `KENYA_CGT_PROPERTY`: indicative 15% of a positive net gain after supplied transfer, acquisition and enhancement costs.

The service stops rather than presenting a definitive estimate for non-residents, commercial property, MRI opt-outs, income outside the MRI thresholds, property dealers and possible CGT exemptions.

Every saved estimate includes immutable input and rule snapshots. Tax rules are effective-dated and can only be governed through super-admin endpoints. The calculation list is owner-scoped and bounded to 50 records per request.

## External connection boundary

Connection requests retain only a masked KRA PIN, explicit consent version, requested scopes and review state. The module does not accept an iTax password and contains no outbound KRA client.

Allowed onboarding path:

`REQUESTED -> AWAITING_KRA_APPROVAL -> SANDBOX_READY`

Requests may be suspended or rejected. There is deliberately no `ACTIVE` or production-transmission state.

Before an external adapter can be added, Slickhood must complete the relevant KRA developer onboarding, obtain the actual supported API catalogue, complete eTIMS OSCU/VSCU certification where applicable, store credentials in the production secret manager, validate callbacks and reconciliation, and add sandbox contract tests plus an explicit per-submission approval step.

## Deployment

1. Deploy the backend and verify Flyway migration `V61`.
2. Confirm both seeded rule versions and their official-source URLs.
3. Deploy the frontend.
4. Verify owner calculation isolation, super-admin rule access and connection masking in staging.
5. Keep all external KRA networking disabled; connection requests are onboarding records only.
