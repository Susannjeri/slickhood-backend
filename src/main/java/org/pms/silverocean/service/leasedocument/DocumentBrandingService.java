package org.pms.silverocean.service.leasedocument;

import jakarta.transaction.Transactional;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.DocumentBrandingRepo;
import org.pms.silverocean.database.pms.entities.DocumentBranding;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class DocumentBrandingService {
    static final long MAX_LOGO_BYTES = 512 * 1024;
    private final DocumentBrandingRepo branding;
    private final UserDao users;

    public DocumentBrandingService(DocumentBrandingRepo branding, UserDao users) {
        this.branding = branding;
        this.users = users;
    }

    @Transactional
    public BrandingView upload(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_LOGO_BYTES) throw invalid();
        try {
            byte[] content = file.getBytes();
            String mime = detectMime(content);
            validateDimensions(content);
            long userId = users.getUserId();
            DocumentBranding value = branding.findByOwnerUserIdAndActiveTrue(userId).orElseGet(DocumentBranding::new);
            value.setOwnerUserId(userId); value.setLogoContent(content); value.setLogoMimeType(mime);
            value.setLogoSha256(sha256(content)); value.setCreatedBy(userId); value.setActive(true);
            return new BrandingView(true, value.getLogoSha256(), branding.save(value).getLastModifiedDate());
        } catch (IOException e) {
            throw invalid();
        }
    }

    public BrandingView current() {
        return branding.findByOwnerUserIdAndActiveTrue(users.getUserId())
                .map(value -> new BrandingView(true, value.getLogoSha256(), value.getLastModifiedDate()))
                .orElse(new BrandingView(false, null, null));
    }

    public String dataUri(long ownerUserId) {
        return branding.findByOwnerUserIdAndActiveTrue(ownerUserId)
                .map(value -> "data:" + value.getLogoMimeType() + ";base64," + Base64.getEncoder().encodeToString(value.getLogoContent()))
                .orElse(null);
    }

    private String detectMime(byte[] content) {
        if (content.length >= 8 && content[0] == (byte) 0x89 && content[1] == 0x50 && content[2] == 0x4e && content[3] == 0x47) return "image/png";
        if (content.length >= 3 && content[0] == (byte) 0xff && content[1] == (byte) 0xd8 && content[2] == (byte) 0xff) return "image/jpeg";
        throw invalid();
    }

    private void validateDimensions(byte[] content) throws IOException {
        try (var input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 32 || height < 32 || width > 2400 || height > 2400) throw invalid();
            } finally { reader.dispose(); }
        }
    }

    private String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private PMSCustomException invalid() { return new PMSCustomException(ResponseCode.INVALID_FIELD_DATA); }
    public record BrandingView(boolean configured, String sha256, java.time.LocalDateTime updatedAt) {}
}
