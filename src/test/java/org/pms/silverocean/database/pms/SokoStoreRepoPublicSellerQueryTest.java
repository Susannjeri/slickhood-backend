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
                    .contains("S.ACTIVE=1")
                    .contains("S.STATUS='PUBLISHED'")
                    .contains("EXISTS (SELECT 1 FROM PMS_SOKO_PRODUCT P")
                    .contains("P.STORE_ID=S.ID")
                    .contains("P.ACTIVE=1")
                    .contains("P.STATUS='PUBLISHED'")
                    .contains("P.STOCK_QUANTITY>0")
                    .contains(":FULFILMENT='DELIVERY' AND S.DELIVERY_ENABLED=1")
                    .contains(":FULFILMENT='PICKUP' AND S.PICKUP_ENABLED=1");
        }
    }

    @Test
    void pageCountAndStoreDetailRequireLiveSubscriptionAndOwnedReadyMerchantAccount() throws Exception {
        for(String candidate:List.of(normalized(query.value()),normalized(query.countQuery()))){
            assertSaleableSellerBoundary(candidate);
        }
        Query detail=SokoStoreRepo.class.getMethod("findPublicSaleableById",long.class).getAnnotation(Query.class);
        assertThat(detail.nativeQuery()).isTrue();
        assertSaleableSellerBoundary(normalized(detail.value()));
    }

    @Test
    void sellerOrderingIsStableAndPaginationIsRepositoryBacked() {
        assertThat(normalized(query.value())).contains("ORDER BY LOWER(S.NAME),S.ID");
        assertThat(query.nativeQuery()).isTrue();
        assertThat(method.getReturnType()).isEqualTo(Page.class);
        assertThat(method.getParameterTypes()[2]).isEqualTo(Pageable.class);
    }

    private void assertSaleableSellerBoundary(String sql){
        assertThat(sql)
                .contains("FROM PMS_USER_SUBSCRIPTION USUB")
                .contains("USUB.CREATED_BY=S.OWNER_USER_ID")
                .contains("USUB.PRODUCT_KEY='SOKO'")
                .contains("USUB.STATUS='ACTIVE'")
                .contains("USUB.ACTIVE=1")
                .contains("USUB.END_AT IS NULL OR USUB.END_AT>UTC_TIMESTAMP(6)")
                .contains("FROM PMS_PAYMENT_ACCOUNT PACC")
                .contains("PACC.ID=S.PAYMENT_ACCOUNT_ID")
                .contains("PACC.CREATED_BY=S.OWNER_USER_ID")
                .contains("PACC.CATEGORY='MERCHANT'")
                .contains("PACC.ACTIVE=1")
                .contains("PACC.VERIFIED=1")
                .contains("PACC.CHANNEL IS NOT NULL");
    }

    private String normalized(String jpql) {
        return jpql.replaceAll("\\s+"," ").trim().toUpperCase(Locale.ROOT);
    }
}
