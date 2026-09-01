package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.sp.dao.ProviderDocumentDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderDocumentServiceAuthorizationTest {
    private ProviderDocumentDao documentDao;
    private ProviderProfileDao profileDao;
    private ProviderServiceDao serviceDao;
    private UserDao userDao;
    private GarageService garageService;
    private ProviderDocumentService service;

    @BeforeEach
    void setUp() {
        documentDao = mock(ProviderDocumentDao.class);
        profileDao = mock(ProviderProfileDao.class);
        serviceDao = mock(ProviderServiceDao.class);
        userDao = mock(UserDao.class);
        garageService = mock(GarageService.class);
        service = new ProviderDocumentService(documentDao, profileDao, serviceDao, userDao, garageService);

        ProviderProfile profile = new ProviderProfile();
        profile.setId(10L);
        when(userDao.getUserId()).thenReturn(7L);
        when(profileDao.findByUserIdAndActive(7L)).thenReturn(Optional.of(profile));
    }

    @Test
    void uploadRejectsAnotherProvidersServiceBeforeStorage() {
        when(serviceDao.findByIdAndProfileId(99L, 10L)).thenReturn(Optional.empty());
        var file = new MockMultipartFile("file", "certificate.pdf", "application/pdf", "clean".getBytes());

        assertThatThrownBy(() -> service.uploadDocument(99L, file, "CERTIFICATE", null))
                .isInstanceOf(PMSCustomException.class);

        verify(garageService, never()).uploadBytes(any(), any(), any());
        verify(documentDao, never()).save(any(), any());
    }

    @Test
    void listRejectsAnotherProvidersServiceBeforePresigning() {
        when(serviceDao.findByIdAndProfileId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listOwnedDocumentsForService(99L, PageRequest.of(0, 20)))
                .isInstanceOf(PMSCustomException.class);

        verify(documentDao, never()).findByServiceId(anyLong(), any());
        verify(garageService, never()).getPresignedUrl(any());
    }

    @Test
    void ownedUploadUsesServerGeneratedTenantScopedKey() throws Exception {
        when(serviceDao.findByIdAndProfileId(12L, 10L)).thenReturn(Optional.of(new ProviderService()));
        doAnswer(invocation -> {
            ((org.pms.silverocean.database.pms.entities.ProviderDocument) invocation.getArgument(0)).setId(1L);
            return null;
        }).when(documentDao).save(any(), any());
        var file = new MockMultipartFile("file", "../../certificate.PDF", "application/pdf", "clean".getBytes());

        service.uploadDocument(12L, file, "CERTIFICATE", null);

        verify(garageService).uploadBytes(
                org.mockito.ArgumentMatchers.matches("sp/documents/12/[0-9a-f-]{36}\\.pdf"),
                any(byte[].class),
                org.mockito.ArgumentMatchers.eq("application/pdf"));
    }
}
