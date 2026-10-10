package org.pms.silverocean.service.soko;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SokoRequestsValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void checkoutCapsWorkAndRejectsUnsupportedDeliveryMethods() {
        var maximumCart = new SokoRequests.Checkout(1L,
                Collections.nCopies(25, new SokoRequests.CheckoutItem(2L, 100)),
                "PICKUP", null, "0712345678", null, null);
        var tooManyItems = new SokoRequests.Checkout(1L,
                Collections.nCopies(26, new SokoRequests.CheckoutItem(2L, 1)),
                "PICKUP", null, "0712345678", null, null);
        var invalidMethod = new SokoRequests.Checkout(1L,
                List.of(new SokoRequests.CheckoutItem(2L, 1)),
                "TELEPORT", null, "0712345678", null, null);
        var excessiveQuantity = new SokoRequests.Checkout(1L,
                List.of(new SokoRequests.CheckoutItem(2L, 101)),
                "DELIVERY", "Nairobi", "0712345678", null, null);

        assertThat(validator.validate(maximumCart)).isEmpty();
        assertThat(validator.validate(tooManyItems)).anyMatch(v -> v.getPropertyPath().toString().equals("items"));
        assertThat(validator.validate(invalidMethod)).anyMatch(v -> v.getPropertyPath().toString().equals("deliveryMethod"));
        assertThat(validator.validate(excessiveQuantity)).anyMatch(v -> v.getPropertyPath().toString().contains("quantity"));
    }

    @Test
    void riderAcceptsShortLegacyIdButRequiresSafeCharactersAndSixDigitConfirmation() {
        var accepted = new SokoRequests.RiderUpsert(1L,"INDIVIDUAL","John","254111379961","4567",null,"Motorbike",null,null);
        var unsafeId = new SokoRequests.RiderUpsert(1L,"INDIVIDUAL","John","254111379961","45 67",null,"Motorbike",null,null);

        assertThat(validator.validate(accepted)).isEmpty();
        assertThat(validator.validate(unsafeId)).anyMatch(v -> v.getPropertyPath().toString().equals("nationalIdNumber")
                && v.getMessage().contains("letters, numbers and hyphens"));
        assertThat(validator.validate(new SokoRequests.RiderVerificationConfirm("12345")))
                .anyMatch(v -> v.getPropertyPath().toString().equals("code") && v.getMessage().contains("six digits"));
        assertThat(validator.validate(new SokoRequests.RiderVerificationConfirm("123456"))).isEmpty();
    }

    @Test
    void catalogueMoneyAndStockInputsHaveDefensibleUpperBounds() {
        var excessiveFee = new SokoRequests.StoreUpsert("Shop",null,null,null,null,null,null,true,true,
                new BigDecimal("1000000.01"),"KES",1L);
        var excessiveProduct = new SokoRequests.ProductUpsert(1L,"Rice",null,"PANTRY_STAPLES","kg",
                new BigDecimal("10000000.01"),1,null);
        var excessiveProductStock = new SokoRequests.ProductUpsert(1L,"Rice",null,"PANTRY_STAPLES","kg",
                BigDecimal.ONE,1_000_001,null);
        var excessiveVariation = new SokoRequests.ProductVariation("Pack","Large",
                new BigDecimal("10000000.01"),1_000_001);

        assertThat(validator.validate(excessiveFee)).anyMatch(v -> v.getPropertyPath().toString().equals("deliveryFee")
                && v.getMessage().contains("1,000,000"));
        assertThat(validator.validate(excessiveProduct)).anyMatch(v -> v.getPropertyPath().toString().equals("price")
                && v.getMessage().contains("10,000,000"));
        assertThat(validator.validate(excessiveProductStock)).anyMatch(v -> v.getPropertyPath().toString().equals("stockQuantity")
                && v.getMessage().contains("1,000,000"));
        assertThat(validator.validate(excessiveVariation)).allMatch(v -> v.getPropertyPath().toString().contains("stockQuantity")
                || v.getPropertyPath().toString().contains("priceAdjustment"));
        assertThat(validator.validate(excessiveVariation)).hasSize(2);
    }

    @Test void monetaryInputsRejectPrecisionThatTheDatabaseWouldRound() {
        var store = new SokoRequests.StoreUpsert("Shop",null,null,null,null,null,null,true,true,
                new BigDecimal("10.001"),"KES",1L);
        var product = new SokoRequests.ProductUpsert(1L,"Rice",null,"PANTRY_STAPLES","kg",
                new BigDecimal("99.999"),1,null,List.of(new SokoRequests.ProductVariation("Pack","Large",new BigDecimal("1.005"),1)));
        var finance = new SokoRequests.FinanceUpdate(SokoRequests.FinanceType.REFUND,
                SokoRequests.FinanceStatus.PROCESSING,new BigDecimal("25.001"),null);

        assertThat(validator.validate(store)).anyMatch(v->v.getPropertyPath().toString().equals("deliveryFee")&&v.getMessage().contains("two decimal"));
        assertThat(validator.validate(product)).anyMatch(v->v.getPropertyPath().toString().contains("price")&&v.getMessage().contains("two decimal"));
        assertThat(validator.validate(product)).anyMatch(v->v.getPropertyPath().toString().contains("priceAdjustment")&&v.getMessage().contains("two decimal"));
        assertThat(validator.validate(finance)).anyMatch(v->v.getPropertyPath().toString().equals("amount")&&v.getMessage().contains("two decimal"));
    }
}
