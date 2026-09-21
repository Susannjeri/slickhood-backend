-- Give the Help Desk and AI distinct, end-to-end guidance for the three
-- customer journeys that follow property setup. These roles must not be
-- treated as interchangeable: a tenancy, a purchase and home ownership have
-- different records, documents and completion conditions.

UPDATE pms_help_article
SET title = 'Tenant journey: invitation, lease, payments and move-out',
    body = 'A tenant normally starts from the invitation sent for one exact rental unit. Register or sign in with the invited email, select the Tenant role, complete the required profile and KYC steps, and return to the invitation if setup was interrupted. Review the property and unit details before accepting. The landlord or authorised manager prepares and issues the governed lease agreement; review the PDF and recorded rent, currency, dates and terms before acknowledging or signing. The tenancy becomes active only after the required signatures and activation checks complete. Use the tenant portal for invoices, reconciled payment history, receipts, statements, maintenance requests and issued notices. A provider message or payment attempt is not proof of payment; wait for the payment to reconcile to the correct invoice. Report incorrect unit, rent, dates or parties before signing. At termination or move-out, follow the issued notice, inspection and account-clearance process and retain the final documents.',
    keywords = 'tenant journey invitation invited email registration KYC rental unit lease agreement acknowledge sign rent invoice payment reconciliation receipt statement maintenance notice termination move out inspection',
    audience_roles = 'Tenant,Landlord,PropertyManager,LeasingOfficer',
    published = 1,
    active = 1,
    last_modified_date = NOW(6)
WHERE slug = 'tenant-and-lease-help';

INSERT INTO pms_help_article
(uuid,created_on,active,created_by,last_modified_date,slug,title,category,body,keywords,audience_roles,published)
VALUES
(UUID_TO_BIN(UUID()),NOW(6),1,NULL,NOW(6),'buyer-journey','Buyer journey: invitation, offer, payment and handover','Property sales',
 'A buyer starts from the invitation or secure sale link for one exact property and unit. Register or sign in with the invited email, select the Buyer role, and complete the required profile and KYC steps. Confirm the listing, unit, seller or appointed sales team and offer details before proceeding. Review each issued document in order. A letter of offer records the proposed amount, currency and response deadline; both required signatures may reserve the sale, but they do not prove payment, transfer ownership or complete the transaction. Complete the authorised due-diligence tasks, review and sign the separate sale agreement when issued, and pay only the verified destination shown on the linked invoice. Payment becomes confirmed only after provider callback and reconciliation. Track outstanding conditions, transfer documents and handover evidence in the buyer workspace. Treat the purchase as complete only when the governed completion process records the required payment, transfer and handover milestones. Report a wrong property, unit, amount, payee or party before signing or paying.',
 'buyer journey property sale invitation KYC listing unit offer letter deadline reservation due diligence sale agreement verified payment account invoice reconciliation transfer handover completion',
 'Buyer,SalesAgent,SalesCoordinator,ListingAgent',1),
(UUID_TO_BIN(UUID()),NOW(6),1,NULL,NOW(6),'homeowner-journey','Homeowner journey: ownership, service charges and estate services','Estate management',
 'A homeowner joins through the invitation for one exact estate home or unit. Register or sign in with the invited email, select the Homeowner role, and complete the required profile and KYC steps. Confirm that the displayed estate, home and ownership details are correct. Review and sign the Estate Residential Agreement issued from the current ownership record. This agreement governs estate services; it does not create a rental tenancy, reserve a property sale or prove title transfer. Use the homeowner workspace to review service-charge invoices, reconciled payments, receipts, community funds and authorised estate records; register visitors and follow service or maintenance requests through their recorded status. Payments must use the verified destination on the invoice and are complete only after reconciliation. Ownership history is preserved when a home changes hands, so contact the estate manager if the owner, home or effective date is wrong rather than creating a duplicate home. Access to meetings, budgets, visitors and community operations remains controlled by the active role, estate membership and assigned home.',
 'homeowner journey estate invitation KYC ownership home unit estate residential agreement service charge invoice payment reconciliation receipt community fund visitor service request maintenance meeting budget ownership transfer',
 'Homeowner,EstateManager,EstateOperationsManager',1)
ON DUPLICATE KEY UPDATE
 title=VALUES(title), category=VALUES(category), body=VALUES(body), keywords=VALUES(keywords),
 audience_roles=VALUES(audience_roles), published=1, active=1, last_modified_date=NOW(6);
