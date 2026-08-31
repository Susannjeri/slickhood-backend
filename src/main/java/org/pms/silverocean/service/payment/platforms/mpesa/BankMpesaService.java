package org.pms.silverocean.service.payment.platforms.mpesa;

import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.PaymentCallBackRequest;
import org.pms.silverocean.service.payment.PaymentCallBackResponse;
import org.pms.silverocean.service.payment.PaymentPlatform;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.springframework.stereotype.Service;

@Service("M-Pesa via Bank Paybill")
public class BankMpesaService extends PaymentPlatform {
    private final ConfigService configService;
    private final ParamService paramService;
    private final I18NService i18NService;
    private final MPesaService directMpesaService;

    public BankMpesaService(UpdatePaymentService updatePaymentService, ConfigService configService,
                            ParamService paramService, I18NService i18NService, MPesaService directMpesaService) {
        super(updatePaymentService);
        this.configService = configService;
        this.paramService = paramService;
        this.i18NService = i18NService;
        this.directMpesaService = directMpesaService;
    }

    @Override
    protected PaymentResponse initPayment(PMSInvoice invoice, long accountId) throws PaymentRequestException {
        String paybill = paramService.getParamByAccountIdAndType(accountId,
                PaymentChannel.MPESA_BANK.findProperty(PaymentPropertyKeys.BANK_PAYBILL), invoice.getPropertyId());
        String bankAccount = paramService.getParamByAccountIdAndType(accountId,
                PaymentChannel.MPESA_BANK.findProperty(PaymentPropertyKeys.BANK_ACCOUNT), invoice.getPropertyId());

        invoice.setTransactionInProgress(false);
        updatePaymentService.updateInvoice(invoice);
        String instructions = String.format(
                i18NService.getLocalizedMessage(ResponseCode.MPESA_BANK_PAYMENT_INSTRUCTIONS),
                paybill, bankAccount, invoice.getRef());
        return new PaymentResponse(true, ResponseCode.MPESA_BANK_PAYMENT_INSTRUCTIONS, instructions);
    }

    @Override
    protected boolean isActive() {
        return PMSUtils.booleanizeConfig(configService.getConfigByName(PMSConfigs.PAYMENT_MPESA_ENABLED).get());
    }

    @Override
    protected PaymentChannel channelType() {
        return PaymentChannel.MPESA_BANK;
    }

    @Override
    protected String channelIcon() {
        return configService.getConfigByName(PMSConfigs.MPESA_ICON).get().stringValue();
    }

    @Override
    public PaymentCallBackResponse handleCallBack(PaymentCallBackRequest paymentCallBackRequest) {
        return directMpesaService.handleCallBack(paymentCallBackRequest);
    }
}
