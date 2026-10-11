package org.pms.silverocean.service.sp;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.sp.wrappers.ApproveProviderProfileRequest;
import org.pms.silverocean.service.sp.wrappers.RejectProviderProfileRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderProfileReviewRequestValidationTest {

    @Test
    void approvalAndRejectionRequireANonNegativeReviewedVersion() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertFalse(validator.validate(new ApproveProviderProfileRequest(null, null)).isEmpty());
            assertFalse(validator.validate(new ApproveProviderProfileRequest(-1L, null)).isEmpty());
            assertTrue(validator.validate(new ApproveProviderProfileRequest(0L, null)).isEmpty());

            assertFalse(validator.validate(new RejectProviderProfileRequest(null, "Incomplete documents")).isEmpty());
            assertFalse(validator.validate(new RejectProviderProfileRequest(-1L, "Incomplete documents")).isEmpty());
            assertTrue(validator.validate(new RejectProviderProfileRequest(0L, "Incomplete documents")).isEmpty());
        }
    }
}
