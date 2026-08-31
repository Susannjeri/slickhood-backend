# Soko and Services production audit

## Journeys reviewed

- Soko: shop setup, payment-account gate, catalogue creation, search, cart, checkout, merchant fulfilment, rider assignment, delivery confirmation, cancellation, refund and settlement.
- Services: provider profile, category selection, pricing, verification, review, discovery, booking, payment, fulfilment evidence, ratings and complaints.

## Verification policy

The provider's completed account KYC is the identity source of truth. Service onboarding must not request national ID or passport again. A service category may request a relevant credential only where customer risk justifies it (for example a professional, health, insurance, good-conduct or work-permit credential). Referees are not a default gate. Administrators can still configure a stricter category where a documented risk assessment requires it.

Soko sellers do not submit a second document pack. Publishing a shop requires a verified, active merchant payment account. Regulated goods should be introduced as governed categories with a specific licence requirement rather than adding blanket seller paperwork.

## Guardrails implemented

- Product catalogues support one to five uploaded JPEG, PNG or WebP images, with per-file and aggregate limits, signature validation, server-generated storage keys and ownership checks.
- Product publishing requires a catalogue image. Existing HTTPS image URLs remain readable for backward compatibility, but the UI now uses uploads.
- Catalogue image rows are fetched in one bulk query per page. Presigning database-backed keys avoids an object-store HEAD request for every image.
- New image batches use immutable storage prefixes, so a failed replacement cannot destroy the previous catalogue images.
- Provider credentials accept only PDF, JPEG or PNG content with signature and size checks; document types must be recognized enum values.
- Credential upload and provider-side listing verify that the service belongs to the signed-in provider, closing cross-provider object access.
- The Services wizard skips empty verification/referee steps and validates the configured credential before review submission.
- Checkout asks only for fields needed by the selected delivery method and shows only fulfilment methods supported by the shop.

## Deployment checks

Apply Flyway migration `V46__soko_product_catalog_images.sql` before deploying the application. Verify object storage CORS and upload limits allow five files and 25 MB aggregate, while the application enforces 8 MB per image. Smoke-test one delivery and one pickup order, one low-risk service with no credential, and one credential-gated service before production promotion.
