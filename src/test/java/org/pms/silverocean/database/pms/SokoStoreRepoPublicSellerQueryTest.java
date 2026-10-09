package org.pms.silverocean.database.pms;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SokoStoreRepoPublicSellerQueryTest {
    private final Method method=Arrays.stream(SokoStoreRepo.class.getDeclaredMethods())
            .filter(candidate->candidate.getName().equals("searchPublicSellers"))
            .findFirst()
            .orElseThrow();
    private final Query query=method.getAnnotation(Query.class);

    @Test
    void pageAndCountExposeOnlyPublishedActiveStoresWithSellableStock() {
        String pageQuery=normalized(query.value());
        String countQuery=normalized(query.countQuery());

        for(String candidate:List.of(pageQuery,countQuery)){
            assertThat(candidate)
                    .contains("S.ACTIVE=TRUE")
                    .contains("S.STATUS='PUBLISHED'")
                    .contains("EXISTS (SELECT P.ID FROM SOKOPRODUCT P")
                    .contains("P.STOREID=S.ID")
                    .contains("P.ACTIVE=TRUE")
                    .contains("P.STATUS='PUBLISHED'")
                    .contains("P.STOCKQUANTITY>0")
                    .contains(":FULFILMENT='DELIVERY' AND S.DELIVERYENABLED=TRUE")
                    .contains(":FULFILMENT='PICKUP' AND S.PICKUPENABLED=TRUE");
        }
    }

    @Test
    void sellerOrderingIsStableAndPaginationIsRepositoryBacked() {
        assertThat(normalized(query.value())).contains("ORDER BY LOWER(S.NAME),S.ID");
        assertThat(method.getReturnType()).isEqualTo(Page.class);
        assertThat(method.getParameterTypes()[2]).isEqualTo(Pageable.class);
    }

    private String normalized(String jpql) {
        return jpql.replaceAll("\\s+"," ").trim().toUpperCase(Locale.ROOT);
    }
}
