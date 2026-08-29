package org.pms.silverocean.service.payment.platforms.flutterwave;

import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.StaticStrings;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.CustomizedFWConfigs;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import org.springframework.http.HttpHeaders;

/**
 * Common base for all FlutterWave service components.
 */
@Slf4j
public class FlutterWaveConfigService {

    protected final ConfigService configService;
    
    private final ParamService paramService;

    protected String flutterWaveUrl;
   
    protected String redirectUrl;
    protected String logoUrl;

    protected int maxVerifyRetries;

    protected final String INIT_PAYMENT_URL_SUFFIX = "/payments";
    protected final String VERIFY_PAYMENT_URL_SUFFIX = "/verify";
    protected final String TRANSACTIONS_PAYMENT_URL_SUFFIX = "/transactions";

    private final String FW_IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    public FlutterWaveConfigService(ConfigService configService,
                                   ParamService paramService) {
        this.configService = configService;
        this.paramService = paramService;
    }


    public void initCommonFlutterWaveConfig() {
        this.flutterWaveUrl = configService.getConfigByName(PMSConfigs.FW_CARD_PAYMENT_URL).get().stringValue();

        this.redirectUrl = configService.getConfigByName(PMSConfigs.FW_REDIRECT_URL).get().stringValue();
        this.logoUrl = configService.getConfigByName(PMSConfigs.FW_LOGO_URL).get().stringValue();
        this.maxVerifyRetries = configService.getConfigByName(PMSConfigs.FW_MAX_VERIFY_RETRIES).get().intValue();
    }

    protected HttpHeaders buildAuthHeaders(String secretKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, StaticStrings.BEARER_PREFIX + secretKey);
        return headers;
    }

    protected CustomizedFWConfigs getUserFWConfigs(long accountId, long propertyId) {
        String secretKey = paramService.getParamByAccountIdAndType(accountId, PaymentChannel.FLUTTER_WAVE.findProperty(PaymentPropertyKeys.SECRET_KEY), propertyId);
        String publicKey = paramService.getParamByAccountIdAndType(accountId, PaymentChannel.FLUTTER_WAVE.findProperty(PaymentPropertyKeys.PUBLIC_KEY), propertyId);
        String encryptionKey = paramService.getParamByAccountIdAndType(accountId, PaymentChannel.FLUTTER_WAVE.findProperty(PaymentPropertyKeys.ENCRYPTION_KEY), propertyId);
        return new CustomizedFWConfigs(secretKey, publicKey, encryptionKey);
    }
}
