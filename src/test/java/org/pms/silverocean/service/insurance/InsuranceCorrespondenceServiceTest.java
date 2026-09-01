package org.pms.silverocean.service.insurance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.InsuranceCaseRepo;
import org.pms.silverocean.database.pms.InsuranceClaimRepo;
import org.pms.silverocean.database.pms.InsuranceCompanyRepo;
import org.pms.silverocean.database.pms.InsuranceEmailExchangeRepo;
import org.pms.silverocean.database.pms.InsurancePolicyRepo;
import org.pms.silverocean.database.pms.entities.InsuranceCompany;
import org.pms.silverocean.database.pms.entities.InsuranceEmailExchange;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.security.EncryptionService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuranceCorrespondenceServiceTest {
    @Mock InsuranceCompanyRepo companies;
    @Mock InsuranceEmailExchangeRepo exchanges;
    @Mock InsuranceCaseRepo cases;
    @Mock InsuranceClaimRepo claims;
    @Mock InsurancePolicyRepo policies;
    @Mock EncryptionService encryption;
    @Mock InsuranceEmailSender emailSender;
    @Mock DomainEventOutboxPublisher outbox;
    @Mock UserDao users;
    @InjectMocks InsuranceCorrespondenceService service;

    @Test
    void mailboxResponsePreservesInitiatingActorWithoutRequiringWebAuthentication() {
        InsuranceEmailExchange outbound = new InsuranceEmailExchange();
        outbound.setId(5L);
        outbound.setCompanyId(2L);
        outbound.setCaseReference("INS-2026-TEST");
        outbound.setCorrelationId("corr-1");
        outbound.setMessageType("QUOTATION_REQUEST");
        outbound.setRecipientAddress("quotes@insurer.example");
        outbound.setCreatedBy(77L);

        InsuranceCompany company = new InsuranceCompany();
        company.setId(2L);
        company.setCode("TEST");
        company.setName("Test Insurer");

        when(exchanges.findByCorrelationId("corr-1")).thenReturn(Optional.of(outbound));
        when(companies.findById(2L)).thenReturn(Optional.of(company));
        when(encryption.encrypt("Quote response")).thenReturn(new byte[]{1, 2, 3});
        when(exchanges.save(any())).thenAnswer(invocation -> {
            InsuranceEmailExchange value = invocation.getArgument(0);
            value.setId(6L);
            return value;
        });

        service.recordMailboxResponse(new InsuranceModels.InsurerEmailResponse(
                "corr-1", "quotes@insurer.example", "Re: quotation", "Quote response", "mail-1"));

        ArgumentCaptor<InsuranceEmailExchange> saved = ArgumentCaptor.forClass(InsuranceEmailExchange.class);
        verify(exchanges).save(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(77L);
        assertThat(saved.getValue().getStatus()).isEqualTo("RECEIVED_VERIFIED");
        verifyNoInteractions(users);
    }
}
