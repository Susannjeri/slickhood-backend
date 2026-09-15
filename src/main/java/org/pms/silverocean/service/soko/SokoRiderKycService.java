package org.pms.silverocean.service.soko;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.filestorage.UploadMalwarePolicy;
import org.pms.silverocean.service.kyc.MarketplaceKycGate;
import org.pms.silverocean.service.users.ProfileType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class SokoRiderKycService {
    private final SokoRiderRepo riders;
    private final SokoRiderCredentialRepo credentials;
    private final KycCaseRepo cases;
    private final KycMatrixReleaseRepo releases;
    private final KycMatrixRequirementRepo requirements;
    private final UserDao users;
    private final MarketplaceKycGate gate;
    private final GarageService garage;
    private final UploadMalwarePolicy malware;
    private final AuditLogService audit;
    private final org.pms.silverocean.service.notification.BusinessNotificationService businessAlerts;
    // Identity/business documents continue through the established common KYC flow.
    private static final Set<String> SUPPLEMENTAL_TYPES=Set.of("GOOD_CONDUCT_CERTIFICATE","PROFESSIONAL_CERTIFICATE","APPOINTMENT_LETTER");
    public record DocumentView(long id,String documentType,String status,String reviewNotes,ZonedDateTime expiresAt,String downloadUrl){}
    public record Checklist(boolean commonKycApproved,List<String> outstanding,List<String> uploadTypes,List<DocumentView> documents,List<String> renewalDocumentTypes){}
    public record Review(String decision,String notes,ZonedDateTime expiresAt){}

    public Checklist myChecklist(){Users user=currentUser();requireRegisteredRider(user);return checklist(user);}
    public Checklist adminChecklist(long riderId){requireAdmin();return checklist(riderUser(riderId));}

    @Transactional(transactionManager="pmsDBTransactionManager")
    public DocumentView upload(String documentType,MultipartFile file)throws Exception {
        Users user=currentUser();requireRegisteredRider(user);
        cases.findByUserIdForUpdate(user.getId()).orElseThrow(()->new PMSCustomException(ResponseCode.KYC_CONSENT_REQUIRED));
        if(!commonApproved(user))throw new PMSCustomException(ResponseCode.KYC_MISSING_DOCUMENTS,"Complete your existing account KYC first. Approved account KYC is reused, not repeated.");
        String type=StringUtils.defaultString(documentType).trim().toUpperCase(Locale.ROOT);
        if(!uploadTypes(user).contains(type))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This document is not an active rider requirement. Identity documents belong in your existing KYC profile.");
        byte[] bytes=validatedDocument(file);malware.requireSafe(bytes);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        List<SokoRiderCredential> existing=credentials.findAllByUserIdAndActiveTrueOrderByCreatedOnDesc(user.getId());
        for(SokoRiderCredential saved:existing){
            if(type.equals(saved.getDocumentType())&&current(saved)&&("PENDING_REVIEW".equals(saved.getStatus())||("VERIFIED".equals(saved.getStatus())&&!gate.outstandingDocumentTypes(user.getId(),"PROVIDER_TYPE","DELIVERY_RIDER",profileType(user)).contains(type))))return view(saved);
            if(hash.equals(saved.getSha256())&&!type.equals(saved.getDocumentType()))throw new PMSCustomException(ResponseCode.KYC_DUPLICATE_DOCUMENT);
        }
        String mime=file.getContentType().toLowerCase(Locale.ROOT);
        String ref="soko/rider-kyc/"+user.getId()+"/"+UUID.randomUUID()+("application/pdf".equals(mime)?".pdf":"image/png".equals(mime)?".png":".jpg");
        garage.uploadBytes(ref,bytes,mime);
        SokoRiderCredential c=new SokoRiderCredential();c.setUserId(user.getId());c.setDocumentType(type);c.setFileRef(ref);c.setContentType(mime);c.setSha256(hash);c.setStatus("PENDING_REVIEW");c.setActive(true);c.setCreatedBy(user.getId());
        c=credentials.save(c);audit.createAuditLog(c,"SOKO_RIDER_KYC_UPLOADED");
        String key="rider-credential:"+c.getId()+":submitted";
        businessAlerts.publish(user.getId(),key,"SOKO_RIDER_VERIFICATION","Your rider requirement was submitted for review.","/dashboard/soko-deliveries");
        users.findActiveSuperAdminAccounts().forEach(admin->businessAlerts.publish(admin.getId(),key,"SOKO_RIDER_REVIEW_REQUIRED","A rider requirement is awaiting review.","/dashboard/rider-verification"));
        return view(c);
    }

    @Transactional(transactionManager="pmsDBTransactionManager")
    public DocumentView review(long id,Review request){
        requireAdmin();SokoRiderCredential c=credentials.findByIdForUpdate(id).orElseThrow(()->new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
        if(!"PENDING_REVIEW".equals(c.getStatus()))throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        String decision=StringUtils.defaultString(request.decision()).toUpperCase(Locale.ROOT);
        if(!Set.of("VERIFY","REJECT").contains(decision)||StringUtils.isBlank(request.notes())||request.notes().length()>1000)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"Select verify or reject and record a useful review note.");
        if(request.expiresAt()!=null&&!request.expiresAt().isAfter(now()))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"The document expiry must be in the future.");
        Users user=users.findById(c.getUserId()).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
        if("VERIFY".equals(decision)&&(!commonApproved(user)||!uploadTypes(user).contains(c.getDocumentType())))throw new PMSCustomException(ResponseCode.KYC_INVALID_STATE);
        c.setStatus("VERIFY".equals(decision)?"VERIFIED":"REJECTED");c.setReviewedAt(now());c.setReviewedBy(users.getUserId());c.setReviewNotes(request.notes().trim());c.setExpiresAt(request.expiresAt());
        c=credentials.save(c);audit.createAuditLog(c,"SOKO_RIDER_KYC_"+decision);
        businessAlerts.publish(user.getId(),"rider-credential:"+c.getId()+":"+decision,"SOKO_RIDER_VERIFICATION","Your rider requirement has been reviewed. Open your checklist to see the result and any next step.","/dashboard/soko-deliveries");
        return view(c);
    }

    private Checklist checklist(Users user){
        List<DocumentView> docs=new ArrayList<>();
        gate.currentVerifiedDocuments(user.getId()).stream().filter(d->SUPPLEMENTAL_TYPES.contains(d.getDocumentType())).forEach(d->docs.add(new DocumentView(d.getId(),d.getDocumentType(),"APPROVED_COMMON_KYC_REUSED",null,d.getExpiresAt(),null)));
        credentials.findAllByUserIdAndActiveTrueOrderByCreatedOnDesc(user.getId()).forEach(c->docs.add(view(c)));
        return new Checklist(commonApproved(user),gate.missingRequirements(user.getId(),"PROVIDER_TYPE","DELIVERY_RIDER",profileType(user)),uploadTypes(user),docs,gate.outstandingDocumentTypes(user.getId(),"PROVIDER_TYPE","DELIVERY_RIDER",profileType(user)));
    }
    private List<String> uploadTypes(Users user){
        var live=releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("PUBLISHED").orElse(null);if(live==null)return List.of();
        return requirements.findAllByReleaseIdAndActiveTrueAndScopeTypeAndScopeKeyOrderByRequirementLabel(live.getId(),"PROVIDER_TYPE","DELIVERY_RIDER").stream()
                .filter(r->"BOTH".equals(r.getProfileScope())||user.getProfileType().equals(r.getProfileScope()))
                .flatMap(r->Arrays.stream(r.getAcceptedDocumentTypes().split(","))).map(String::trim).filter(SUPPLEMENTAL_TYPES::contains).distinct().toList();
    }
    private Users riderUser(long id){SokoRider r=riders.findById(id).filter(SokoRider::isActive).orElseThrow(()->new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));return users.findByEmail(StringUtils.defaultString(r.getEmail()).trim().toLowerCase(Locale.ROOT)).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"The rider must register using the email supplied by the merchant."));}
    private Users currentUser(){return users.findById(users.getUserId()).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));}
    private void requireRegisteredRider(Users user){if(riders.findAllByUserIdAndActiveTrue(user.getId()).isEmpty()&&(StringUtils.isBlank(user.getEmail())||riders.findAllByEmailIgnoreCaseAndActiveTrue(user.getEmail()).isEmpty()))throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);}
    private void requireAdmin(){if(!users.hasRole(PMSRole.SUPER_ADMIN))throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);}
    private boolean commonApproved(Users user){return user.isActive()&&user.isVerified()&&user.isEmailVerified()&&"ACTIVE".equals(user.getAccountStatus())&&cases.findByUserId(user.getId()).filter(c->c.isActive()&&"APPROVED".equals(c.getStatus())).isPresent();}
    private ProfileType profileType(Users user){return ProfileType.valueOf(user.getProfileType());}
    private ZonedDateTime now(){return ZonedDateTime.now(ZoneId.of("UTC"));}
    private boolean current(SokoRiderCredential c){return c.getExpiresAt()==null||c.getExpiresAt().isAfter(now());}
    private DocumentView view(SokoRiderCredential c){return new DocumentView(c.getId(),c.getDocumentType(),current(c)?c.getStatus():"EXPIRED",c.getReviewNotes(),c.getExpiresAt(),garage.getPresignedUrlForStoredObject(c.getFileRef()));}
    private byte[] validatedDocument(MultipartFile file)throws IOException{
        if(file==null||file.isEmpty()||file.getSize()>8L*1024*1024)throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);
        String type=String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT);byte[] b=file.getBytes();
        boolean valid=switch(type){case "application/pdf"->b.length>5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-';case "image/jpeg"->b.length>3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff;case "image/png"->b.length>8&&Arrays.equals(Arrays.copyOf(b,8),new byte[]{(byte)0x89,'P','N','G',13,10,26,10});default->false;};
        if(!valid)throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);return b;
    }
}
