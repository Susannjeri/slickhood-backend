package org.pms.silverocean.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PMSUtilsPhoneNumberTest {

    @Test
    void acceptsAndNormalisesCurrentKenyanMobileRanges() {
        assertEquals("+254111379961", PMSUtils.getLocalisedPhoneNumber("+254111379961"));
        assertEquals("+254712345678", PMSUtils.getLocalisedPhoneNumber("0712345678"));
    }

    @Test
    void rejectsIncompleteOrImpossibleKenyanNumbers() {
        assertNull(PMSUtils.getLocalisedPhoneNumber("+25411137996"));
        assertNull(PMSUtils.getLocalisedPhoneNumber("+254000000000"));
    }
}
