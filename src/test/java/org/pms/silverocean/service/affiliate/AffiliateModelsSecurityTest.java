package org.pms.silverocean.service.affiliate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.AffiliatePayout;
import org.pms.silverocean.database.pms.entities.AffiliateReferral;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AffiliateModelsSecurityTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void affiliateReferralViewDoesNotExposeUserOrAuditIdentifiers() throws Exception {
        AffiliateReferral entity = new AffiliateReferral();
        entity.setId(9L);
        entity.setAffiliateUserId(40L);
        entity.setReferredUserId(81L);
        entity.setCreatedBy(40L);
        entity.setStatus("REGISTERED");

        String json = mapper.writeValueAsString(new AffiliateModels.Referral(entity));

        assertFalse(json.contains("affiliateUserId"));
        assertFalse(json.contains("referredUserId"));
        assertFalse(json.contains("createdBy"));
    }

    @Test
    void affiliatePayoutViewDoesNotExposeInternalAccountOrProcessorIdentifiers() throws Exception {
        AffiliatePayout entity = new AffiliatePayout();
        entity.setId(10L);
        entity.setAffiliateUserId(40L);
        entity.setPaymentAccountId(22L);
        entity.setProcessedByUserId(1L);
        entity.setCreatedBy(40L);

        String json = mapper.writeValueAsString(new AffiliateModels.Payout(entity));

        assertFalse(json.contains("affiliateUserId"));
        assertFalse(json.contains("paymentAccountId"));
        assertFalse(json.contains("processedByUserId"));
        assertFalse(json.contains("createdBy"));
    }
}
