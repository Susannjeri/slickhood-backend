package org.pms.silverocean.service.soko;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SokoRequestsValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void checkoutCapsWorkAndRejectsUnsupportedDeliveryMethods() {
        var tooManyItems = new SokoRequests.Checkout(1L,
                Collections.nCopies(51, new SokoRequests.CheckoutItem(2L, 1)),
                "PICKUP", null, "0712345678", null, null);
        var invalidMethod = new SokoRequests.Checkout(1L,
                List.of(new SokoRequests.CheckoutItem(2L, 1)),
                "TELEPORT", null, "0712345678", null, null);
        var excessiveQuantity = new SokoRequests.Checkout(1L,
                List.of(new SokoRequests.CheckoutItem(2L, 10_001)),
                "DELIVERY", "Nairobi", "0712345678", null, null);

        assertThat(validator.validate(tooManyItems)).anyMatch(v -> v.getPropertyPath().toString().equals("items"));
        assertThat(validator.validate(invalidMethod)).anyMatch(v -> v.getPropertyPath().toString().equals("deliveryMethod"));
        assertThat(validator.validate(excessiveQuantity)).anyMatch(v -> v.getPropertyPath().toString().contains("quantity"));
    }
}
