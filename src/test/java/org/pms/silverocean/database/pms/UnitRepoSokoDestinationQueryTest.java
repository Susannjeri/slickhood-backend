package org.pms.silverocean.database.pms;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class UnitRepoSokoDestinationQueryTest {
    @Test
    void tenancyDestinationsRequireCurrentUserAcceptanceAndActiveUnitAndProperty() throws Exception {
        Query query=UnitRepo.class.getMethod("findAcceptedTenancyDeliveryDestinations",long.class)
                .getAnnotation(Query.class);
        assertThat(normalized(query.value()))
                .contains("UT.USERID=:USERID")
                .contains("UT.ACTIVE")
                .contains("UT.LEASEACCEPTED")
                .contains("U.ACTIVE")
                .contains("P.ACTIVE");
    }

    @Test
    void ownershipDestinationsRequireCurrentActiveUnexpiredOwnership() throws Exception {
        Query query=UnitRepo.class.getMethod("findHomeownerDeliveryDestinations",long.class)
                .getAnnotation(Query.class);
        assertThat(normalized(query.value()))
                .contains("PO.HOMEOWNERUSERID=:USERID")
                .contains("PO.ACTIVE")
                .contains("PO.OWNERSHIPSTART<=CURRENT_DATE")
                .contains("PO.OWNERSHIPEND IS NULL OR PO.OWNERSHIPEND>=CURRENT_DATE")
                .contains("PO.UNITID IS NULL OR PO.UNITID=U.ID")
                .contains("U.ACTIVE")
                .contains("P.ACTIVE");
    }

    private String normalized(String jpql) {
        return jpql.replaceAll("\\s+"," ").trim().toUpperCase(Locale.ROOT);
    }
}
