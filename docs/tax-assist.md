# Slickhood Tax Assist

Tax Assist separates private estimates from external tax transmission.

## Supported calculations

- `KENYA_MRI`: resident taxpayer, Kenyan residential property, annual gross rent above KES 288,000 and not above KES 15,000,000. The seeded rate is 7.5% of gross rent received.
- `KENYA_CGT_PROPERTY`: indicative 15% of a positive net gain after supplied transfer, acquisition and enhancement costs.

The service stops rather than presenting a definitive estimate for non-residents, commercial property, MRI opt-outs, income outside the MRI thresholds, property dealers and possible CGT exemptions.

Every saved estimate includes immutable input and rule snapshots. Tax rules are effective-dated and can only be governed through super-admin endpoints. The calculation list is owner-scoped and bounded to 50 records per request.

## System-owner release controls

Migration `V62` creates a singleton configuration with fail-safe controls. Only a `SUPER_ADMIN` can update them:

- `estimatesEnabled` pauses or releases MRI and property CGT estimates without altering historical rules.
- `connectionRequestsEnabled` controls new consent-backed KRA onboarding requests and defaults to `false`.
- `legalNoticeVersion` identifies the currently approved customer guidance wording.
- `liveKraTransmissionEnabled` is read-only and always `false`; neither a user nor an administrator can activate transmission through this module.

If the configuration row is missing or unavailable, estimates and connection requests return `503 Service Unavailable`. Existing calculation history, connection history and disconnect operations remain available.

## External connection boundary

Connection requests retain only a masked KRA PIN, explicit consent version, requested scopes and review state. The module does not accept an iTax password and contains no outbound KRA client.

Allowed onboarding path:

`REQUESTED -> AWAITING_KRA_APPROVAL -> SANDBOX_READY`

Requests may be suspended or rejected. There is deliberately no `ACTIVE` or production-transmission state.

Before an external adapter can be added, Slickhood must complete the relevant KRA developer onboarding, obtain the actual supported API catalogue, complete eTIMS OSCU/VSCU certification where applicable, store credentials in the production secret manager, validate callbacks and reconciliation, and add sandbox contract tests plus an explicit per-submission approval step.

## Deployment

1. Deploy the backend and verify Flyway migrations `V61` and `V62` both succeed.
2. In Tax Administration, confirm both seeded rule versions, effective dates, rates and official-source URLs against the approved legal position.
3. Confirm the approved `legalNoticeVersion`; enable estimates only after the rule check.
4. Keep connection requests disabled until Slickhood has a staffed onboarding/review process. Enabling them still cannot transmit tax data.
5. Deploy the frontend and verify its configuration request succeeds.
6. Verify owner calculation isolation, super-admin-only controls, paused-state behaviour, PIN masking and disconnect behaviour in staging.
7. Keep all external KRA networking disabled; connection requests are onboarding records only.
