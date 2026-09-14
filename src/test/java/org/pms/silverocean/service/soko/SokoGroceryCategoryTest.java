package org.pms.silverocean.service.soko;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.PMSCustomException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SokoGroceryCategoryTest {
    @Test void acceptsCodeOrDisplayLabel(){
        assertEquals("FRESH_PRODUCE",SokoGroceryCategory.normalize("fresh produce"));
        assertEquals("DAIRY_EGGS",SokoGroceryCategory.normalize("DAIRY_EGGS"));
    }
    @Test void rejectsAlcoholAndUnrestrictedFreeText(){
        assertThrows(PMSCustomException.class,()->SokoGroceryCategory.normalize("Alcohol"));
        assertThrows(PMSCustomException.class,()->SokoGroceryCategory.normalize("Electronics"));
    }
}
