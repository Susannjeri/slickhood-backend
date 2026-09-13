package org.pms.silverocean.service.insurance;

import org.pms.silverocean.service.insurance.InsuranceModels.MarineIdfOcrView;

public interface MarineIdfOcrProvider {
    MarineIdfOcrView extract(byte[] document, String contentType);
}
