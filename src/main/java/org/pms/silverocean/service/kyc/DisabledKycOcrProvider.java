package org.pms.silverocean.service.kyc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "kyc.ocr.provider", havingValue = "none", matchIfMissing = true)
public class DisabledKycOcrProvider implements KycOcrProvider {
    @Override public OcrResult extract(byte[] document, String contentType, KycDocumentType documentType) {
        return new OcrResult("NONE", 0, Map.of());
    }
    @Override public boolean enabled() { return false; }
}
