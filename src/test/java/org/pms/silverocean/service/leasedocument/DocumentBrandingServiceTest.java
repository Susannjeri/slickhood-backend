package org.pms.silverocean.service.leasedocument;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.DocumentBrandingRepo;
import org.pms.silverocean.database.pms.entities.DocumentBranding;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentBrandingServiceTest {
    @Mock DocumentBrandingRepo repo;
    @Mock UserDao users;

    @Test void validLogoIsValidatedHashedAndStoredForTheCurrentOwner() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", output);
        when(users.getUserId()).thenReturn(71L);
        when(repo.findByOwnerUserIdAndActiveTrue(71L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DocumentBrandingService service = new DocumentBrandingService(repo, users);

        var result = service.upload(new MockMultipartFile("logo", "owner.png", "image/png", output.toByteArray()));

        assertTrue(result.configured());
        assertEquals(64, result.sha256().length());
        verify(repo).save(argThat(value -> value.getOwnerUserId() == 71L
                && "image/png".equals(value.getLogoMimeType()) && value.getLogoContent().length > 0));
    }

    @Test void oversizedOrNonImagePayloadIsRejectedBeforeStorage() {
        DocumentBrandingService service = new DocumentBrandingService(repo, users);
        byte[] oversized = new byte[(int) DocumentBrandingService.MAX_LOGO_BYTES + 1];

        assertThrows(PMSCustomException.class, () -> service.upload(
                new MockMultipartFile("logo", "fake.png", "image/png", oversized)));
        verifyNoInteractions(repo, users);
    }
}
