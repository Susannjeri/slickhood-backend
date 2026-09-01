package org.pms.silverocean.service.wealth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.pms.silverocean.database.pms.entities.WealthAsset;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;

class WealthAnalyticsPerformanceTest {
    @Test @Timeout(2) void calculatesTenThousandAssetsWithoutQuadraticDegradation(){var assets=IntStream.range(0,10_000).mapToObj(i->{var a=new WealthAsset();a.setId((long)i+1);a.setName("Asset "+i);a.setAssetType("OTHER");a.setCurrency("KES");a.setCurrentValue(BigDecimal.TEN);a.setAcquisitionCost(BigDecimal.ONE);a.setValuationDate(LocalDate.now());return a;}).toList();var result=WealthAnalytics.calculate(assets,java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),1,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);assertThat(result.summary().assetCount()).isEqualTo(10_000);assertThat(result.summary().totalAssetValue()).isEqualByComparingTo("100000");}
}
