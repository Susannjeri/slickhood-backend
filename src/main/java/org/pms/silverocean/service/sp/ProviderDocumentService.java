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
import org.pms.silverocean.service.sp.enums.DocumentStatus;
import org.pms.silverocean.service.sp.wrappers.ProviderDocumentDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZonedDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderDocumentService {
    private final ProviderDocumentDao documentDao;
    private final UserDao userDao;
    private final GarageService garageService;

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderDocumentDTO uploadDocument(long serviceId, MultipartFile file,
                                              String documentType, ZonedDateTime expiryDate) throws IOException {
        String filePath = "sp/documents/" + serviceId + "/";
        garageService.uploadFile(filePath, file);
        String fileRef = (filePath + file.getOriginalFilename()).replaceAll("\\s+", "_");

        long userId = userDao.getUserId();
        ProviderDocument document = new ProviderDocument();
        document.setServiceId(serviceId);
        document.setDocumentType(documentType);
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

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void verifyDocument(long documentId, DocumentStatus status) {
        ProviderDocument document = documentDao.findById(documentId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_DOCUMENT_NOT_FOUND));
        document.setVerificationStatus(status.name());
        documentDao.save(document, Permission.VERIFY_SP_DOCUMENT);
    }
}
