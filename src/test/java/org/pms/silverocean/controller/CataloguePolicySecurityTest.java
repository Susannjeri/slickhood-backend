package org.pms.silverocean.controller;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.junit.jupiter.api.Assertions.*;
class CataloguePolicySecurityTest {
 @Test void customPropertyTypesRemainSuperadminOnly(){assertEquals("hasRole('SUPER_ADMIN')",CustomPropertyTypeController.class.getAnnotation(PreAuthorize.class).value());}
 @Test void financialPolicyRemainsSuperadminOnly(){assertEquals("hasRole('SUPER_ADMIN')",AffiliatePolicyController.class.getAnnotation(PreAuthorize.class).value());}
}
