package org.pms.silverocean.service.soko;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.filestorage.UploadMalwarePolicy;
import org.pms.silverocean.service.kyc.MarketplaceKycGate;
import org.springframework.mock.web.MockMultipartFile;
import java.time.ZonedDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class SokoRiderKycServiceTest {
    @Mock SokoRiderRepo riders;@Mock SokoRiderCredentialRepo credentials;@Mock KycCaseRepo cases;
    @Mock KycMatrixReleaseRepo releases;@Mock KycMatrixRequirementRepo requirements;@Mock UserDao users;
    @Mock MarketplaceKycGate gate;@Mock GarageService garage;@Mock UploadMalwarePolicy malware;@Mock AuditLogService audit;
    @Mock org.pms.silverocean.service.notification.BusinessNotificationService businessAlerts;
    SokoRiderKycService service;Users user;
    @Test void submittedEvidenceAlertsRiderAndAdminWithoutPrivateData()throws Exception{
        uploadReady();user.setProfileType("INDIVIDUAL");
        Users admin=new Users();admin.setId(20L);admin.setActive(true);
        when(users.findActiveSuperAdminAccounts()).thenReturn(Set.of(admin));
        when(credentials.save(any())).thenAnswer(i->{SokoRiderCredential c=i.getArgument(0);c.setId(4L);return c;});
        service.upload("GOOD_CONDUCT_CERTIFICATE",new MockMultipartFile("file","private-certificate.pdf","application/pdf","%PDF-test".getBytes()));
        verify(businessAlerts).publish(9L,"rider-credential:4:submitted","SOKO_RIDER_VERIFICATION","Your rider requirement was submitted for review.","/dashboard/soko-deliveries");
        verify(businessAlerts).publish(20L,"rider-credential:4:submitted","SOKO_RIDER_REVIEW_REQUIRED","A rider requirement is awaiting review.","/dashboard/rider-verification");
        verifyNoMoreInteractions(businessAlerts);verify(cases,never()).save(any());
    }
    @BeforeEach void setup(){service=new SokoRiderKycService(riders,credentials,cases,releases,requirements,users,gate,garage,malware,audit,businessAlerts);user=new Users();user.setId(9L);user.setEmail("rider@example.test");user.setActive(true);user.setVerified(true);user.setEmailVerified(true);user.setAccountStatus("ACTIVE");}
    void registered(){when(users.getUserId()).thenReturn(9L);when(users.findById(9L)).thenReturn(Optional.of(user));when(riders.findAllByEmailIgnoreCaseAndActiveTrue(user.getEmail())).thenReturn(List.of(new SokoRider()));}
    void approved(){KycCase c=new KycCase();c.setId(1L);c.setActive(true);c.setStatus("APPROVED");when(cases.findByUserId(9L)).thenReturn(Optional.of(c));}
    void uploadReady(){registered();approved();when(cases.findByUserIdForUpdate(9L)).thenReturn(Optional.of(new KycCase()));matrix();}
    void matrix(){KycMatrixRelease live=new KycMatrixRelease();live.setId(1L);when(releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("PUBLISHED")).thenReturn(Optional.of(live));KycMatrixRequirement req=new KycMatrixRequirement();req.setProfileScope("INDIVIDUAL");req.setAcceptedDocumentTypes("GOOD_CONDUCT_CERTIFICATE");when(requirements.findAllByReleaseIdAndActiveTrueAndScopeTypeAndScopeKeyOrderByRequirementLabel(1L,"PROVIDER_TYPE","DELIVERY_RIDER")).thenReturn(List.of(req));}

    @Test void nonRiderCannotReadPrivateChecklist(){when(users.getUserId()).thenReturn(9L);when(users.findById(9L)).thenReturn(Optional.of(user));assertThrows(PMSCustomException.class,()->service.myChecklist());verifyNoInteractions(credentials,garage);}
    @Test void merchantCannotReadAnotherRidersKyc(){assertThrows(PMSCustomException.class,()->service.adminChecklist(7L));verifyNoInteractions(riders,credentials,garage);}
    @Test void checklistReusesAccountKycWithoutChangingAccountOrCase(){registered();approved();var result=service.myChecklist();assertTrue(result.commonKycApproved());verify(users,never()).save(any());verify(cases,never()).save(any());assertEquals("ACTIVE",user.getAccountStatus());}
    @Test void spoofedUploadIsRejectedBeforeStorage(){uploadReady();assertThrows(PMSCustomException.class,()->service.upload("GOOD_CONDUCT_CERTIFICATE",new MockMultipartFile("file","fake.pdf","application/pdf","not a PDF".getBytes())));verifyNoInteractions(garage,malware);}
    @Test void identityUploadsStayInExistingCommonKyc(){uploadReady();assertThrows(PMSCustomException.class,()->service.upload("NATIONAL_ID_FRONT",new MockMultipartFile("file","id.pdf","application/pdf","%PDF-test".getBytes())));verifyNoInteractions(garage);}
    @Test void pendingEvidenceRetryDoesNotCreateDuplicateOrReopenKyc()throws Exception{
        uploadReady();SokoRiderCredential saved=new SokoRiderCredential();saved.setId(4L);saved.setDocumentType("GOOD_CONDUCT_CERTIFICATE");saved.setStatus("PENDING_REVIEW");saved.setFileRef("soko/rider-kyc/9/saved.pdf");when(credentials.findAllByUserIdAndActiveTrueOrderByCreatedOnDesc(9L)).thenReturn(List.of(saved));
        var result=service.upload("GOOD_CONDUCT_CERTIFICATE",new MockMultipartFile("file","certificate.pdf","application/pdf","%PDF-test".getBytes()));assertEquals(4L,result.id());verify(credentials,never()).save(any());verify(garage,never()).uploadBytes(anyString(),any(),anyString());verify(cases,never()).save(any());
    }
    @Test void reviewNeedsUsefulNotes(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);SokoRiderCredential c=new SokoRiderCredential();c.setStatus("PENDING_REVIEW");when(credentials.findByIdForUpdate(4L)).thenReturn(Optional.of(c));assertThrows(PMSCustomException.class,()->service.review(4L,new SokoRiderKycService.Review("VERIFY"," ",null)));verify(credentials,never()).save(any());}
    @Test void expiredEvidenceCannotBeApproved(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);SokoRiderCredential c=new SokoRiderCredential();c.setStatus("PENDING_REVIEW");when(credentials.findByIdForUpdate(4L)).thenReturn(Optional.of(c));assertThrows(PMSCustomException.class,()->service.review(4L,new SokoRiderKycService.Review("VERIFY","Checked issuer",ZonedDateTime.now().minusDays(1))));verify(credentials,never()).save(any());}
    @Test void rejectionPreservesAccountApprovalAndAudit(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);SokoRiderCredential c=new SokoRiderCredential();c.setId(4L);c.setUserId(9L);c.setStatus("PENDING_REVIEW");when(credentials.findByIdForUpdate(4L)).thenReturn(Optional.of(c));when(users.findById(9L)).thenReturn(Optional.of(user));when(credentials.save(any())).thenAnswer(i->i.getArgument(0));var result=service.review(4L,new SokoRiderKycService.Review("REJECT","The issuer cannot be verified",null));assertEquals("REJECTED",result.status());verify(audit).createAuditLog(c,"SOKO_RIDER_KYC_REJECT");verify(cases,never()).save(any());verify(users,never()).save(any());}
}
