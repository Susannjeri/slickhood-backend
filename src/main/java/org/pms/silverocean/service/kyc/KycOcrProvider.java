package org.pms.silverocean.service.kyc;

public interface KycOcrProvider {
    OcrResult extract(byte[] document, String contentType, KycDocumentType documentType);
    boolean enabled();
}
