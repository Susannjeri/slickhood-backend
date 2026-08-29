package org.pms.silverocean.service.kyc;

import java.util.Map;

public record OcrResult(String provider, double confidence, Map<String, String> fields) { }
