package org.pms.silverocean.service.sp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ProviderDocument;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.sp.dao.ProviderDocumentDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.enums.DocumentStatus;
import org.pms.silverocean.service.sp.enums.DocumentType;
import org.pms.silverocean.service.sp.wrappers.ProviderDocumentDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderDocumentService {
    private final ProviderDocumentDao documentDao;
    private final UserDao userDao;
    private final GarageService garageService;
    private final ProviderProfileDao profileDao;
    private final ProviderServiceDao serviceDao;

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderDocumentDTO uploadDocument(long serviceId, MultipartFile file,
                                              String documentType, ZonedDateTime expiryDate) throws IOException {
        long userId = userDao.getUserId();
        assertOwnedService(serviceId, userId);
        DocumentType type;
        try { type=DocumentType.valueOf(documentType.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) { throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA); }
        byte[] bytes=validatedDocument(file);
        String contentType=file.getContentType().toLowerCase(Locale.ROOT);
        String fileRef="sp/documents/"+serviceId+"/"+type.name().toLowerCase(Locale.ROOT)+"-"+UUID.randomUUID()+extension(contentType);
        garageService.uploadBytes(fileRef,bytes,contentType);
        ProviderDocument document = new ProviderDocument();
        document.setServiceId(serviceId);
        document.setDocumentType(type.name());
        document.setFileRef(fileRef);
        document.setExpiryDate(expiryDate);
        document.setVerificationStatus(DocumentStatus.PENDING.name());
        document.setActive(true);
        document.setCreatedBy(userId);
        documentDao.save(document, Permission.UPLOAD_SP_DOCUMENT);

        String downloadUrl = garageService.getPresignedUrl(fileRef);
        return new ProviderDocumentDTO(document, downloadUrl);
    }

    public Page<ProviderDocumentDTO> listDocumentsForService(long serviceId, Pageable pageable) {
        return documentDao.findByServiceId(serviceId, pageable)
                .map(d -> new ProviderDocumentDTO(d, garageService.getPresignedUrl(d.getFileRef())));
    }

    public Page<ProviderDocumentDTO> listMyDocumentsForService(long serviceId, Pageable pageable) {
        assertOwnedService(serviceId,userDao.getUserId());
        return listDocumentsForService(serviceId,pageable);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void verifyDocument(long documentId, DocumentStatus status) {
        ProviderDocument document = documentDao.findById(documentId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_DOCUMENT_NOT_FOUND));
        document.setVerificationStatus(status.name());
        documentDao.save(document, Permission.VERIFY_SP_DOCUMENT);
    }

    private void assertOwnedService(long serviceId,long userId){
        var profile=profileDao.findByUserIdAndActive(userId).orElseThrow(()->new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        serviceDao.findByIdAndProfileId(serviceId,profile.getId()).orElseThrow(()->new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
    }

    private byte[] validatedDocument(MultipartFile file)throws IOException{
        if(file==null||file.isEmpty()||file.getSize()>8L*1024*1024)throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);
        String type=String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT);
        if(!Set.of("application/pdf","image/jpeg","image/png").contains(type))throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);
        byte[] b=file.getBytes();
        boolean valid=switch(type){case "application/pdf"->b.length>4&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F';case "image/jpeg"->b.length>3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8;case "image/png"->b.length>8&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G';default->false;};
        if(!valid)throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);return b;
    }
    private String extension(String type){return switch(type){case "application/pdf"->".pdf";case "image/png"->".png";default->".jpg";};}
}
