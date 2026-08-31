package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.entities.ProviderDocument;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.sp.dao.ProviderDocumentDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderDocumentServiceTest {
    @Mock ProviderDocumentDao documents; @Mock UserDao users; @Mock GarageService garage;
    @Mock ProviderProfileDao profiles; @Mock ProviderServiceDao services;
    ProviderDocumentService service;

    @BeforeEach void setup(){service=new ProviderDocumentService(documents,users,garage,profiles,services);}

    @Test void uploadChecksOwnershipAndUsesGeneratedKey() throws Exception {
        ProviderProfile profile=new ProviderProfile();profile.setId(3L);
        ProviderService providerService=new ProviderService();providerService.setId(9L);providerService.setProfileId(3L);
        when(users.getUserId()).thenReturn(7L);when(profiles.findByUserIdAndActive(7L)).thenReturn(Optional.of(profile));
        when(services.findByIdAndProfileId(9L,3L)).thenReturn(Optional.of(providerService));
        doAnswer(invocation->{((ProviderDocument)invocation.getArgument(0)).setId(11L);return null;}).when(documents).save(any(),anyString());
        byte[] pdf={'%','P','D','F','-', '1'};
        service.uploadDocument(9L,new MockMultipartFile("file","../../id.pdf","application/pdf",pdf),"professional_certificate",null);
        verify(garage).uploadBytes(matches("sp/documents/9/professional_certificate-[0-9a-f-]+\\.pdf"),eq(pdf),eq("application/pdf"));
        ArgumentCaptor<ProviderDocument> captured=ArgumentCaptor.forClass(ProviderDocument.class);
        verify(documents).save(captured.capture(),anyString());
        assertEquals("PROFESSIONAL_CERTIFICATE",captured.getValue().getDocumentType());
        assertFalse(captured.getValue().getFileRef().contains(".."));
    }

    @Test void uploadRejectsAnotherProvidersServiceBeforeStorage() {
        ProviderProfile profile=new ProviderProfile();profile.setId(3L);
        when(users.getUserId()).thenReturn(7L);when(profiles.findByUserIdAndActive(7L)).thenReturn(Optional.of(profile));
        when(services.findByIdAndProfileId(9L,3L)).thenReturn(Optional.empty());
        var file=new MockMultipartFile("file","doc.pdf","application/pdf",new byte[]{'%','P','D','F','-'});
        assertThrows(PMSCustomException.class,()->service.uploadDocument(9L,file,"PROFESSIONAL_CERTIFICATE",null));
        verifyNoInteractions(garage,documents);
    }
}
