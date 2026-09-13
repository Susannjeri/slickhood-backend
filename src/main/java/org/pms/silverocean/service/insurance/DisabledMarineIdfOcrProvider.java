package org.pms.silverocean.service.insurance;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.insurance.InsuranceModels.MarineIdfOcrView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(MarineIdfOcrProvider.class)
public class DisabledMarineIdfOcrProvider implements MarineIdfOcrProvider {
    @Override
    public MarineIdfOcrView extract(byte[] document, String contentType) {
        throw new PMSCustomException(ResponseCode.KYC_OCR_PROVIDER_UNAVAILABLE);
    }
}
