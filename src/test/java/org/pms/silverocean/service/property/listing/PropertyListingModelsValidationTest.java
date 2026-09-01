package org.pms.silverocean.service.property.listing;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyListingModelsValidationTest {
    private final jakarta.validation.Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void inquiryRequiresConsentAndValidContactDetails() {
        var request = new PropertyListingModels.InquiryRequest("", "not-an-email", "", "", false, "");
        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .contains("name", "email", "message", "consent");
    }

    @Test
    void acceptsMinimalSafeInquiry() {
        var request = new PropertyListingModels.InquiryRequest("Jane Doe", "jane@example.com", null,
                "Please arrange a viewing.", true, null);
        assertThat(validator.validate(request)).isEmpty();
    }
}
