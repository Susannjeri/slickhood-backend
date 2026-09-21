UPDATE pms_help_article
SET body = 'Submit insurance requests through the Insurance Hub and provide only the requested risk and policy information. For Marine Cargo Insurance, upload every Import Declaration Form (IDF/IM0), review the extracted consignment details, and attach the supplier commercial invoice separately. The commercial invoice must be a PDF, JPG or PNG no larger than 10 MB. SlickHood blocks insurer dispatch until both the declaration and commercial invoice are present. Quotes, premium evidence, policy issue, renewals and claims remain subject to review by authorised Silverwood insurance operations staff. CIC Insurance is a participating insurer; its logo identifies the insurer but does not mean the risk has been accepted or cover issued. Never send payment PINs, OTPs or full card details in Help Desk messages. Escalate disputed payments, policy decisions and urgent claims to a person.',
    keywords = 'insurance quote policy premium renewal claim evidence silverwood marine cargo MCI IDF IM0 import declaration commercial invoice supplier invoice CIC insurer logo',
    last_modified_date = NOW(6)
WHERE slug = 'insurance-support'
  AND active = 1;
