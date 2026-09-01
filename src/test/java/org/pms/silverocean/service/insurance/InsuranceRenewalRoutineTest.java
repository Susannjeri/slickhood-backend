package org.pms.silverocean.service.insurance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.notification.*;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuranceRenewalRoutineTest {
 @Mock InsuranceAgencyRepo agencies;@Mock InsurancePolicyRepo policies;@Mock InsuranceCaseRepo cases;@Mock InsuranceRenewalReminderRepo reminders;@Mock NotificationService notifications;@Mock I18NService i18n;
 @InjectMocks InsuranceRenewalRoutine routine;
 @BeforeEach void setup(){ReflectionTestUtils.setField(routine,"publicUrl","https://app.slickhood.test");lenient().when(i18n.getLocalizedMessage(anyString())).thenReturn("Policy %1$s: %2$s. Continue %3$s");InsuranceAgency agency=new InsuranceAgency();agency.setId(1L);lenient().when(agencies.findByCodeAndActiveTrue("SILVERWOOD")).thenReturn(Optional.of(agency));}
 @Test void queuesEachExpiryStageOnceWithADeepLink(){InsurancePolicy p=policy(LocalDate.now().plusDays(7));when(policies.findStagedReminderQueue(eq(1L),anyCollection(),any(),any(),any())).thenReturn(new PageImpl<>(List.of(p)));when(policies.findExpiryQueue(eq(1L),any(),any())).thenReturn(Page.empty());when(reminders.existsByPolicyIdAndPolicyEndDateAndReminderDays(4L,p.getEndDate(),7)).thenReturn(false);when(cases.findById(9L)).thenReturn(Optional.of(insuranceCase()));routine.remind();ArgumentCaptor<NotificationDTO> notification=ArgumentCaptor.forClass(NotificationDTO.class);verify(notifications).queueNotification(notification.capture());assertThat(notification.getValue().formattedMessage()).contains("EXPIRES IN 7 DAYS","/dashboard/insurance?tab=policies&amp;policyId=4");verify(reminders).save(argThat(r->r.getPolicyId()==4L&&r.getPolicyEndDate().equals(p.getEndDate())&&r.getReminderDays()==7));}
 @Test void neverQueuesADuplicateReminderStageForTheSamePolicyTerm(){InsurancePolicy p=policy(LocalDate.now().plusDays(3));when(policies.findStagedReminderQueue(eq(1L),anyCollection(),any(),any(),any())).thenReturn(new PageImpl<>(List.of(p)));when(policies.findExpiryQueue(eq(1L),any(),any())).thenReturn(Page.empty());when(reminders.existsByPolicyIdAndPolicyEndDateAndReminderDays(4L,p.getEndDate(),3)).thenReturn(true);routine.remind();verifyNoInteractions(notifications);verify(reminders,never()).save(any());}
 @Test void expiresCoverOnlyAfterTheEndDateAndNotifiesTheCustomer(){when(policies.findStagedReminderQueue(eq(1L),anyCollection(),any(),any(),any())).thenReturn(Page.empty());InsurancePolicy p=policy(LocalDate.now().minusDays(1));when(policies.findExpiryQueue(eq(1L),eq(LocalDate.now().minusDays(1)),any())).thenReturn(new PageImpl<>(List.of(p)));when(cases.findById(9L)).thenReturn(Optional.of(insuranceCase()));routine.remind();assertThat(p.getStatus()).isEqualTo("EXPIRED");assertThat(p.getRenewalStatus()).isEqualTo("LAPSED");verify(policies).save(p);verify(notifications).queueNotification(any());}
 @Test void verifiedRenewalPaymentIsNotDowngradedWhilePolicyIssuanceIsPending(){when(policies.findStagedReminderQueue(eq(1L),anyCollection(),any(),any(),any())).thenReturn(Page.empty());InsurancePolicy p=policy(LocalDate.now().minusDays(1));p.setRenewalStatus("PAID");when(policies.findExpiryQueue(eq(1L),eq(LocalDate.now().minusDays(1)),any())).thenReturn(new PageImpl<>(List.of(p)));when(cases.findById(9L)).thenReturn(Optional.of(insuranceCase()));routine.remind();assertThat(p.getStatus()).isEqualTo("EXPIRED");assertThat(p.getRenewalStatus()).isEqualTo("PAID");verify(policies).save(p);}
 private InsurancePolicy policy(LocalDate end){InsurancePolicy p=new InsurancePolicy();p.setId(4L);p.setCaseId(9L);p.setPolicyNumber("APA-POL-4");p.setStatus("ACTIVE");p.setRenewalStatus("UPCOMING");p.setEndDate(end);p.setActive(true);return p;}
 private InsuranceCase insuranceCase(){InsuranceCase c=new InsuranceCase();c.setEmail("client@example.com");c.setActive(true);return c;}
}
