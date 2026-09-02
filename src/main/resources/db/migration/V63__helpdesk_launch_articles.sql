INSERT INTO pms_help_article
(uuid, created_on, active, created_by, last_modified_date, slug, title, category, body, keywords, audience_roles, published)
VALUES
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6),
 'property-setup', 'Setting up properties and units', 'Property management',
 'Use the business area that matches the work you are doing. Landlords, Estate Managers and Property Sale Managers can add properties and units only within their authorised workspace and scope. Complete required identity checks and choose an eligible subscription before using restricted functions. If a property or unit is missing, ask the workspace owner to confirm your Team & Access assignment.',
 'property setup unit landlord estate manager property sale team access subscription', NULL, 1),
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6),
 'insurance-support', 'Insurance quotes, policies and claims', 'Insurance',
 'Submit insurance requests through the Insurance Hub and provide only the requested risk and policy information. Quotes, premium evidence, policy issue, renewals and claims remain subject to review by authorised insurance operations staff. Never send payment PINs, OTPs or full card details in Help Desk messages. Escalate disputed payments, policy decisions and urgent claims to a person.',
 'insurance quote policy premium renewal claim evidence silverwood', NULL, 1),
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6),
 'services-bookings', 'Booking and completing home services', 'Services',
 'Choose a listed service provider, confirm the booking details and use the invoice reference shown by SlickHood. A booking progresses only after the required payment confirmation and operational steps. Use the complaint process for fulfilment disputes, refunds or unsafe conduct. Never share passwords, OTPs, payment PINs or private access credentials with a provider or the Help Desk.',
 'services provider booking invoice payment completion complaint refund', NULL, 1),
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6),
 'affiliate-support', 'Affiliate referrals, commissions and payouts', 'Affiliate',
 'Use your assigned referral link or code so eligible attribution can be recorded. Commission eligibility, holding periods and payout thresholds follow the active SlickHood affiliate terms shown in your workspace. A payout is released only after the underlying qualifying payment and review controls pass. Escalate missing attribution or payout disputes with the referral reference, never with payment credentials.',
 'affiliate referral attribution commission holding period payout', NULL, 1);
