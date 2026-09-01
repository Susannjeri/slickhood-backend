package org.pms.silverocean.service.insurance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.AccountService;
import org.pms.silverocean.service.account.dto.AccountDTO;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.filestorage.UploadMalwarePolicy;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuranceRenewalServiceSecurityTest {
 @Mock InsuranceAgencyRepo agencies;@Mock InsuranceCaseRepo cases;@Mock InsurancePolicyRepo policies;@Mock InsuranceCompanyRepo companies;@Mock InsuranceRenewalOfferRepo offers;@Mock InsuranceRenewalPaymentRepo payments;@Mock InsurancePaymentConfigurationRepo configurations;@Mock AccountService accounts;@Mock UserDao users;@Mock GarageService garage;@Mock UploadMalwarePolicy malwarePolicy;@Mock NotificationService notifications;@Mock I18NService i18n;
 @InjectMocks InsuranceRenewalService service;
 @Test void customerCannotStartAnotherCustomersRenewal(){InsurancePolicy p=policy();p.setCustomerUserId(77L);when(users.getUserId()).thenReturn(99L);when(policies.findByIdForUpdate(4L)).thenReturn(Optional.of(p));assertThatThrownBy(()->service.request(4L)).isInstanceOf(PMSCustomException.class);verify(policies,never()).save(any());verifyNoInteractions(notifications);}
 @Test void renewalPaymentRejectsAnUnverifiedDestination(){InsurancePolicy p=policy();p.setCustomerUserId(77L);p.setRenewalStatus("ACCEPTED");InsuranceRenewalOffer offer=offer();InsurancePaymentConfiguration cfg=new InsurancePaymentConfiguration();cfg.setId(8L);cfg.setCompanyId(2L);cfg.setPaymentAccountId(12L);cfg.setPaymentChannel(PaymentChannel.MPESA);cfg.setEffectiveFrom(LocalDate.now().minusDays(1));cfg.setActive(true);AccountDTO account=new AccountDTO(12L,"Insurer",AccountCategory.INSURANCE,PaymentChannel.MPESA,null,"M-Pesa",true,false,null,List.of());when(users.getUserId()).thenReturn(77L);when(policies.findByIdForUpdate(4L)).thenReturn(Optional.of(p));when(offers.findFirstByPolicyIdAndActiveTrueOrderByCreatedOnDesc(4L)).thenReturn(Optional.of(offer));when(payments.findFirstByRenewalOfferIdAndActiveTrueOrderByCreatedOnDesc(6L)).thenReturn(Optional.empty());when(configurations.findById(8L)).thenReturn(Optional.of(cfg));when(accounts.getAccount(12L)).thenReturn(account);assertThatThrownBy(()->service.recordPayment(4L,new InsuranceModels.RenewalPaymentRequest(8L,"MPESA-1",LocalDateTime.now()))).isInstanceOf(PMSCustomException.class);verify(payments,never()).save(any());}
 @Test void expiredCoverCannotStartANewPayment(){InsurancePolicy p=policy();p.setCustomerUserId(77L);p.setStatus("EXPIRED");p.setRenewalStatus("ACCEPTED");when(users.getUserId()).thenReturn(77L);when(policies.findByIdForUpdate(4L)).thenReturn(Optional.of(p));assertThatThrownBy(()->service.recordPayment(4L,new InsuranceModels.RenewalPaymentRequest(8L,"TOO-LATE",LocalDateTime.now()))).isInstanceOf(PMSCustomException.class);verifyNoInteractions(offers,payments,accounts);}
 @Test void verifierCannotApproveRenewalPaymentWithoutEvidence(){InsuranceRenewalPayment payment=new InsuranceRenewalPayment();payment.setStatus("PENDING_VERIFICATION");when(payments.findByIdForUpdate(5L)).thenReturn(Optional.of(payment));assertThatThrownBy(()->service.decide(5L,new InsuranceModels.PaymentDecisionRequest("VERIFIED",null))).isInstanceOf(PMSCustomException.class);verify(policies,never()).save(any());}
 @Test void customerCannotOpenAnotherCustomersRenewalEvidence(){InsuranceRenewalPayment payment=new InsuranceRenewalPayment();payment.setPolicyId(4L);payment.setProofFileRef("insurance/private.pdf");payment.setActive(true);InsurancePolicy p=policy();p.setCustomerUserId(77L);when(users.getUserId()).thenReturn(99L);when(users.hasPermission(anyString())).thenReturn(false);when(payments.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(payment));when(policies.findById(4L)).thenReturn(Optional.of(p));assertThatThrownBy(()->service.proof(5L)).isInstanceOf(PMSCustomException.class);verifyNoInteractions(garage);}
 private InsurancePolicy policy(){InsurancePolicy p=new InsurancePolicy();p.setId(4L);p.setCaseId(9L);p.setCompanyId(2L);p.setPolicyNumber("APA-4");p.setStatus("ACTIVE");p.setRenewalStatus("UPCOMING");p.setEndDate(LocalDate.now().plusDays(30));p.setActive(true);return p;}
 private InsuranceRenewalOffer offer(){InsuranceRenewalOffer o=new InsuranceRenewalOffer();o.setId(6L);o.setPolicyId(4L);o.setStatus("ACCEPTED");o.setAcceptedAt(LocalDateTime.now().minusMinutes(1));o.setTotalPremium(new BigDecimal("25000"));o.setCurrency("KES");o.setActive(true);return o;}
}
