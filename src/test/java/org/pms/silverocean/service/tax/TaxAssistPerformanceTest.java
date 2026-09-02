package org.pms.silverocean.service.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.tax.TaxModels.MriEstimateRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaxAssistPerformanceTest {
    @Test
    void oneThousandLocalMriEstimatesStayWithinTheCalculationBudget() {
        TaxRuleVersion rule = new TaxRuleVersion(); rule.setId(1L); rule.setRuleCode("KENYA_MRI"); rule.setVersion(1);
        rule.setEffectiveFrom(LocalDate.of(2024, 1, 1)); rule.setRate(new BigDecimal("0.075"));
        rule.setLowerThreshold(new BigDecimal("288000")); rule.setUpperThreshold(new BigDecimal("15000000"));
        rule.setCurrency("KES"); rule.setSourceUrl("https://new.kenyalaw.org/"); rule.setSourceNote("Current law"); rule.setActive(true);
        TaxRuleVersionRepo rules = mock(TaxRuleVersionRepo.class); TaxCalculationRepo calculations = mock(TaxCalculationRepo.class);
        TaxConnectionRequestRepo connections = mock(TaxConnectionRequestRepo.class); UserDao users = mock(UserDao.class);
        when(users.getUserId()).thenReturn(42L);
        when(rules.effectiveCandidates(eq("KENYA_MRI"), any(), any(Pageable.class))).thenReturn(List.of(rule));
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        when(calculations.save(any())).thenAnswer(call -> { TaxCalculation value = call.getArgument(0); value.setId(1L); return value; });
        TaxAssistService service = new TaxAssistService(rules, calculations, connections, users, new ObjectMapper().findAndRegisterModules());
        MriEstimateRequest request = new MriEstimateRequest(YearMonth.of(2026, 8), true, true, false,
                new BigDecimal("1200000"), new BigDecimal("100000"), BigDecimal.ZERO);
        assertTimeout(Duration.ofSeconds(5), () -> { for (int i = 0; i < 1_000; i++) service.estimateMri(request); });
    }
}
