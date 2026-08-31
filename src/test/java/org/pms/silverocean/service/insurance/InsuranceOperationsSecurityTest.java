package org.pms.silverocean.service.insurance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.InsuranceAgency;
import org.pms.silverocean.database.pms.entities.InsuranceCase;
import org.pms.silverocean.database.pms.entities.InsurancePremiumPayment;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.notification.NotificationService;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsuranceOperationsSecurityTest {
 @Mock InsuranceAgencyRepo agencies;@Mock InsuranceCompanyRepo companies;@Mock InsuranceCaseRepo cases;@Mock InsuranceQuoteRepo quotes;
 @Mock InsurancePremiumPaymentRepo payments;@Mock InsurancePolicyRepo policies;@Mock InsuranceClaimRepo claims;@Mock InsuranceDocumentRepo documents;
 @Mock InsuranceActivityRepo activities;@Mock UserRoleRepo userRoles;@Mock UserDao users;@Mock GarageService garage;@Mock NotificationService notifications;@Mock I18NService i18n;
 @InjectMocks InsuranceOperationsService service;

 @Test void customerCannotReadAnotherCustomersCase(){when(users.getUserId()).thenReturn(77L);when(cases.findByIdAndCustomerUserIdAndActiveTrue(15,77)).thenReturn(Optional.empty());assertThatThrownBy(()->service.myCase(15)).isInstanceOf(PMSCustomException.class);verify(cases,never()).findById(15L);}
 @Test void applicationCannotBeAssignedToAnUnapprovedUser(){when(userRoles.findByUserId(901L)).thenReturn(Set.of());assertThatThrownBy(()->service.assign(8,new InsuranceModels.AssignmentRequest(901))).isInstanceOf(PMSCustomException.class);verify(cases,never()).findByIdForUpdate(anyLong());}
 @Test void documentMustHaveExactlyOneAuthorisedParent(){MultipartFile file=mock(MultipartFile.class);assertThatThrownBy(()->service.upload(1L,2L,null,"KYC",file)).isInstanceOf(PMSCustomException.class);verifyNoInteractions(garage);}
 @Test void quoteCannotBeAddedAfterCustomerHasEnteredPaymentFlow(){InsuranceAgency agency=new InsuranceAgency();agency.setId(1L);InsuranceCase insuranceCase=new InsuranceCase();insuranceCase.setId(8L);insuranceCase.setAgencyId(1L);insuranceCase.setStatus("PAYMENT_PENDING");when(cases.findByIdForUpdate(8L)).thenReturn(Optional.of(insuranceCase));when(agencies.findByCodeAndActiveTrue("SILVERWOOD")).thenReturn(Optional.of(agency));assertThatThrownBy(()->service.addQuote(8L,null)).isInstanceOf(PMSCustomException.class);verifyNoInteractions(companies,quotes);}
 @Test void submittedPaymentProofIsImmutable(){InsurancePremiumPayment payment=new InsurancePremiumPayment();payment.setStatus("PENDING_VERIFICATION");payment.setProofFileRef("insurance/existing-proof.pdf");when(payments.findByIdForUpdate(4L)).thenReturn(Optional.of(payment));assertThatThrownBy(()->service.uploadPaymentProof(4L,mock(MultipartFile.class))).isInstanceOf(PMSCustomException.class);verifyNoInteractions(garage,cases);}
 @Test void customerCaseListUsesBoundedBatchQueries(){when(users.getUserId()).thenReturn(77L);InsuranceCase first=new InsuranceCase();first.setId(1L);InsuranceCase second=new InsuranceCase();second.setId(2L);when(cases.findAllByCustomerUserIdAndActiveTrueOrderByCreatedOnDesc(77)).thenReturn(List.of(first,second));when(quotes.findAllByCaseIdInAndActiveTrueOrderByCaseIdAscTotalPremiumAsc(List.of(1L,2L))).thenReturn(List.of());when(payments.findAllByCaseIdInAndActiveTrueOrderByCaseIdAscCreatedOnDesc(List.of(1L,2L))).thenReturn(List.of());service.mine();verify(quotes,times(1)).findAllByCaseIdInAndActiveTrueOrderByCaseIdAscTotalPremiumAsc(anyCollection());verify(payments,times(1)).findAllByCaseIdInAndActiveTrueOrderByCaseIdAscCreatedOnDesc(anyCollection());verify(quotes,never()).findAllByCaseIdAndActiveTrueOrderByTotalPremiumAsc(anyLong());}
}
