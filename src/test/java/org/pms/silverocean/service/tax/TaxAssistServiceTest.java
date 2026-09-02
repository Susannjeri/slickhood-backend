package org.pms.silverocean.service.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.tax.TaxModels.*;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxAssistServiceTest {
    @Mock TaxRuleVersionRepo rules;
    @Mock TaxCalculationRepo calculations;
    @Mock TaxConnectionRequestRepo connections;
    @Mock TaxAssistConfigurationRepo configurations;
    @Mock UserDao users;
    TaxAssistService service;
    TaxRuleVersion mri;
    TaxRuleVersion cgt;

    @BeforeEach
    void setUp() {
        service = new TaxAssistService(rules, calculations, connections, configurations, users, new ObjectMapper().findAndRegisterModules());
        TaxAssistConfiguration configuration = new TaxAssistConfiguration(); configuration.setId(1L); configuration.setEstimatesEnabled(true); configuration.setConnectionRequestsEnabled(true); configuration.setLegalNoticeVersion("tax-guidance-2026-09");
        lenient().when(configurations.findById(1L)).thenReturn(Optional.of(configuration));
        lenient().when(configurations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(users.getUserId()).thenReturn(42L);
        lenient().when(calculations.save(any())).thenAnswer(invocation -> {
            TaxCalculation value = invocation.getArgument(0); value.setId(91L); return value;
        });
        mri = rule(1L, "KENYA_MRI", "0.075", "288000", "15000000");
        cgt = rule(2L, "KENYA_CGT_PROPERTY", "0.15", null, null);
        lenient().when(rules.findById(1L)).thenReturn(Optional.of(mri));
        lenient().when(rules.findById(2L)).thenReturn(Optional.of(cgt));
    }

    @Test
    void estimatesEligibleMriFromGrossReceiptsAndAppliesVerifiedCredit() {
        when(rules.effectiveCandidates(eq("KENYA_MRI"), any(), any(Pageable.class))).thenReturn(List.of(mri));
        CalculationView result = service.estimateMri(new MriEstimateRequest(
                YearMonth.of(2026, 8), true, true, false,
                bd("1200000"), bd("100000"), bd("1000")));
        assertThat(result.outcome()).isEqualTo("MRI_ESTIMATE");
        assertThat(result.estimatedTax()).isEqualByComparingTo("7500.00");
        assertThat(result.estimatedPayable()).isEqualByComparingTo("6500.00");
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void doesNotApplyMriAtTheStrictLowerThreshold() {
        when(rules.effectiveCandidates(eq("KENYA_MRI"), any(), any(Pageable.class))).thenReturn(List.of(mri));
        CalculationView result = service.estimateMri(new MriEstimateRequest(
                YearMonth.of(2026, 8), true, true, false,
                bd("288000"), bd("24000"), bd("0")));
        assertThat(result.outcome()).isEqualTo("BELOW_THRESHOLD");
        assertThat(result.estimatedPayable()).isEqualByComparingTo("0.00");
    }

    @Test
    void estimatesCgtFromNetTransferValueLessAdjustedCost() {
        when(rules.effectiveCandidates(eq("KENYA_CGT_PROPERTY"), any(), any(Pageable.class))).thenReturn(List.of(cgt));
        CalculationView result = service.estimateCgt(new CgtEstimateRequest(
                LocalDate.of(2026, 8, 12), bd("10000000"), bd("500000"),
                bd("5000000"), bd("200000"), bd("300000"), false, false, null));
        assertThat(result.taxableAmount()).isEqualByComparingTo("4000000.00");
        assertThat(result.estimatedPayable()).isEqualByComparingTo("600000.00");
        assertThat(result.outcome()).isEqualTo("CGT_ESTIMATE");
    }

    @Test
    void stopsCgtEstimateForPotentialExemption() {
        when(rules.effectiveCandidates(eq("KENYA_CGT_PROPERTY"), any(), any(Pageable.class))).thenReturn(List.of(cgt));
        CalculationView result = service.estimateCgt(new CgtEstimateRequest(
                LocalDate.of(2026, 8, 12), bd("10000000"), bd("0"), bd("1000000"),
                bd("0"), bd("0"), false, true, "Qualifying private residence"));
        assertThat(result.outcome()).isEqualTo("POTENTIAL_EXEMPTION");
        assertThat(result.estimatedPayable()).isEqualByComparingTo("0.00");
    }

    @Test
    void cannotSkipKraApprovalAndJumpDirectlyFromRequestedToSandboxReady() {
        TaxConnectionRequest request = new TaxConnectionRequest(); request.setId(7L); request.setActive(true); request.setStatus("REQUESTED");
        when(connections.findById(7L)).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.reviewConnection(7L, new ConnectionReview("SANDBOX_READY", "Not yet approved")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("transition");
        verify(connections, never()).save(any());
    }

    @Test
    void cannotDisconnectAnotherUsersConnectionRequest() {
        TaxConnectionRequest request = new TaxConnectionRequest(); request.setId(8L); request.setOwnerUserId(99L); request.setActive(true);
        when(connections.findById(8L)).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.disconnect(8L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("not found");
        verify(connections, never()).save(any());
    }

    @Test
    void adminCanPauseEstimatesWithoutChangingTaxRules() {
        TaxAssistConfiguration configuration = new TaxAssistConfiguration(); configuration.setId(1L); configuration.setEstimatesEnabled(false); configuration.setLegalNoticeVersion("tax-guidance-2026-09");
        when(configurations.findById(1L)).thenReturn(Optional.of(configuration));
        assertThatThrownBy(() -> service.estimateMri(new MriEstimateRequest(
                YearMonth.of(2026, 8), true, true, false, bd("1200000"), bd("100000"), bd("0"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(rules, calculations);
    }

    @Test
    void connectionRequestsFailClosedUntilAdminEnablesThem() {
        TaxAssistConfiguration configuration = new TaxAssistConfiguration(); configuration.setId(1L); configuration.setEstimatesEnabled(true); configuration.setConnectionRequestsEnabled(false); configuration.setLegalNoticeVersion("tax-guidance-2026-09");
        when(configurations.findById(1L)).thenReturn(Optional.of(configuration));
        assertThatThrownBy(() -> service.requestConnection(new ConnectionRequest("GAVACONNECT", "A123456789B", Set.of("FILE_TAX_RETURN"), true)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(connections);
    }

    @Test
    void liveKraTransmissionCannotBeEnabledByConfiguration() {
        ConfigurationView result = service.updateConfiguration(new ConfigurationRequest(true, true, "tax-guidance-2026-10"));
        assertThat(result.liveKraTransmissionEnabled()).isFalse();
        assertThat(result.legalNoticeVersion()).isEqualTo("tax-guidance-2026-10");
    }

    private static TaxRuleVersion rule(long id, String code, String rate, String lower, String upper) {
        TaxRuleVersion r = new TaxRuleVersion(); r.setId(id); r.setRuleCode(code); r.setVersion(1);
        r.setEffectiveFrom(LocalDate.of(2023, 1, 1)); r.setRate(bd(rate)); r.setCurrency("KES");
        if (lower != null) r.setLowerThreshold(bd(lower)); if (upper != null) r.setUpperThreshold(bd(upper));
        r.setSourceUrl("https://example.test/law"); r.setSourceNote("Verified rule"); r.setActive(true); return r;
    }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
