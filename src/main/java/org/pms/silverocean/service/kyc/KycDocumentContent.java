package org.pms.silverocean.service.kyc;

public record KycDocumentContent(byte[] bytes, String contentType, String fileName, long contentLength) { }
