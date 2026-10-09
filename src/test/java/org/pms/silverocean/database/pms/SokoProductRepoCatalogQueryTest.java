package org.pms.silverocean.database.pms;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SokoProductRepoCatalogQueryTest {
    private final Method method=Arrays.stream(SokoProductRepo.class.getDeclaredMethods())
            .filter(candidate->candidate.getName().equals("searchCatalog"))
            .findFirst()
            .orElseThrow();
    private final Query query=method.getAnnotation(Query.class);

    @Test
    void categoryAndFulfilmentAreFilteredInBothPageAndCountQueries() {
        String pageSql=normalized(query.value());
        String countSql=normalized(query.countQuery());

        assertThat(pageSql)
                .contains("LOWER(P.CATEGORY) = LOWER(:CATEGORY)")
                .contains(":FULFILMENT = 'DELIVERY' AND S.DELIVERY_ENABLED = 1")
                .contains(":FULFILMENT = 'PICKUP' AND S.PICKUP_ENABLED = 1");
        assertThat(countSql)
                .contains("LOWER(P.CATEGORY) = LOWER(:CATEGORY)")
                .contains(":FULFILMENT = 'DELIVERY' AND S.DELIVERY_ENABLED = 1")
                .contains(":FULFILMENT = 'PICKUP' AND S.PICKUP_ENABLED = 1");
        assertThat(pageSql.indexOf("LOWER(P.CATEGORY)")).isLessThan(pageSql.indexOf(" ORDER BY "));
        assertThat(pageSql.indexOf(":FULFILMENT = 'ALL'")).isLessThan(pageSql.indexOf(" ORDER BY "));
    }

    @Test
    void anExplicitSellerFiltersBothThePageAndItsCount() {
        assertThat(normalized(query.value())).contains("(:STOREID IS NULL OR P.STORE_ID = :STOREID)");
        assertThat(normalized(query.countQuery())).contains("(:STOREID IS NULL OR P.STORE_ID = :STOREID)");
    }

    @Test
    void priceAndNearestOrderingAreStableAndAppliedByTheRepository() {
        String pageSql=normalized(query.value());

        assertThat(pageSql)
                .contains("CASE WHEN :SORTMODE = 'PRICE' THEN P.PRICE END ASC")
                .contains("CASE WHEN :SORTMODE = 'NEAREST' THEN 6371.0 * 2.0 * ASIN")
                .contains("P.NAME ASC, P.ID ASC");
    }

    @Test
    void exactHaversineRadiusAndCountRunBeforeDatabasePagination() {
        String pageSql=normalized(query.value());
        String countSql=normalized(query.countQuery());
        String haversine="6371.0 * 2.0 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0, POWER(SIN(RADIANS(S.LATITUDE - :LATITUDE) / 2.0), 2)";

        assertThat(pageSql).contains(haversine).contains("<= :RADIUSKM");
        assertThat(countSql).contains(haversine).contains("<= :RADIUSKM");
        assertThat(query.nativeQuery()).isTrue();
        assertThat(method.getReturnType()).isEqualTo(Page.class);
        assertThat(method.getParameterTypes()[0]).isEqualTo(Pageable.class);
    }

    @Test
    void deliveryResultsAlsoRespectEachShopsOwnServiceRadius() {
        String pageSql=normalized(query.value());
        String countSql=normalized(query.countQuery());

        assertThat(pageSql)
                .contains(":FULFILMENT <> 'DELIVERY' OR :LATITUDE IS NULL")
                .contains("S.SERVICE_RADIUS_KM IS NOT NULL")
                .contains("<= S.SERVICE_RADIUS_KM");
        assertThat(countSql)
                .contains(":FULFILMENT <> 'DELIVERY' OR :LATITUDE IS NULL")
                .contains("S.SERVICE_RADIUS_KM IS NOT NULL")
                .contains("<= S.SERVICE_RADIUS_KM");
    }

    @Test
    void indexedBoundingBoxPrecedesExactDistanceAndSupportsAntimeridianWrapping() {
        String pageSql=normalized(query.value());
        String countSql=normalized(query.countQuery());

        assertThat(pageSql)
                .contains("S.LATITUDE BETWEEN :MINLATITUDE AND :MAXLATITUDE")
                .contains(":WRAPLONGITUDE = 0 AND S.LONGITUDE BETWEEN :MINLONGITUDE AND :MAXLONGITUDE")
                .contains(":WRAPLONGITUDE = 1 AND (S.LONGITUDE >= :MINLONGITUDE OR S.LONGITUDE <= :MAXLONGITUDE)");
        assertThat(countSql)
                .contains("S.LATITUDE BETWEEN :MINLATITUDE AND :MAXLATITUDE")
                .contains(":WRAPLONGITUDE = 0 AND S.LONGITUDE BETWEEN :MINLONGITUDE AND :MAXLONGITUDE")
                .contains(":WRAPLONGITUDE = 1 AND (S.LONGITUDE >= :MINLONGITUDE OR S.LONGITUDE <= :MAXLONGITUDE)");
        assertThat(pageSql.indexOf("S.LATITUDE BETWEEN :MINLATITUDE"))
                .isLessThan(pageSql.indexOf("6371.0 * 2.0 * ASIN"));
    }

    private String normalized(String sql) {
        return sql.replaceAll("\\s+"," ").trim().toUpperCase(Locale.ROOT);
    }
}
