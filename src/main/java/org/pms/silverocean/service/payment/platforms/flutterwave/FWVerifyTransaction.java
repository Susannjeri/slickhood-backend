package org.pms.silverocean.service.payment.platforms.flutterwave;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.service.RestRequestException;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.CustomizedFWConfigs;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWChargeCompletedData;
import org.pms.silverocean.service.payment.platforms.flutterwave.wrappers.FWVerifyTransactionDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.threadpooling.PMSThreadPoolExecutorService;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FWVerifyTransaction {
    private final UpdatePaymentService updatePaymentService;
    private final InvoiceDao invoiceDao;
    private final PaymentDao paymentDao;
    private final ThreadPoolBeans threadPoolBeans;
    private final EventService eventService;
    private final RestTemplateService restTemplateService;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();
    private final FlutterWaveConfigService flutterWaveConfigService;

    private  PMSThreadPoolExecutorService verifyFlutterWaveTransactionPool;


    public FWVerifyTransaction(ConfigService configService, UpdatePaymentService updatePaymentService, InvoiceDao invoiceDao,
                               EventService eventService, PaymentDao paymentDao, RestTemplateService restTemplateService,
                               ParamService paramService, ThreadPoolBeans threadPoolBeans) {
        this.updatePaymentService = updatePaymentService;
        this.invoiceDao = invoiceDao;
        this.paymentDao = paymentDao;
        this.threadPoolBeans = threadPoolBeans;
        this.flutterWaveConfigService = new FlutterWaveConfigService(configService, paramService);
        this.eventService = eventService;
        this.restTemplateService = restTemplateService;
    }

    @PostConstruct
    public void init() {
        verifyFlutterWaveTransactionPool = threadPoolBeans.ioExecutorService("verify-");

        loadPendingVerifyTransactionsFromDb();
    }

    public void scheduleVerifyFlutterWaveTransaction(long paymentId, int retries) {
        if (retries > flutterWaveConfigService.maxVerifyRetries) {
            log.info("Verify Flutter Wave Transaction retries exceeded for payment ID {}", paymentId);
            return;
        }
        ScheduledFuture<?> scheduledFuture = verifyFlutterWaveTransactionPool.schedule(() -> paymentDao.findPaymentByID(paymentId)
                .ifPresent(this::sendVerifyTransactionRequest), 1, TimeUnit.HOURS);
        ScheduledFuture<?> oldScheduledFuture = scheduledFutures.put(paymentId, scheduledFuture);
        cancelScheduledFuture(oldScheduledFuture);
    }

    public void cancelScheduledVerifyFlutterWaveTransaction(long paymentId) {
        log.info("Cancelling scheduled future, paymentId={}, events in queue {}", paymentId, scheduledFutures.size());
        cancelScheduledFuture(scheduledFutures.remove(paymentId));
    }

    public void verifyFlutterWaveTransaction(long paymentId) {
        verifyFlutterWaveTransactionPool.submit(() -> paymentDao.findPaymentByID(paymentId)
                .ifPresent(this::sendVerifyTransactionRequest));
    }

    private void cancelScheduledFuture(ScheduledFuture<?> scheduledFuture) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            log.info("Cancelled scheduled future {}", scheduledFuture);
        }
    }

    private void sendVerifyTransactionRequest(PMSPayment payment) {
        scheduledFutures.remove(payment.getId());
        log.info("Pending scheduled verify events {}", scheduledFutures.size());
        if (!PaymentChannel.FLUTTER_WAVE.getName().equals(payment.getChannel()) || !TransactionCategory.CARD_PAYMENT.name().equals(payment.getCategory())) {
            log.error("Wrong channel [{}] or category [{}]", payment.getChannel(), payment.getCategory());
            return;
        }
        if (!payment.isInProgress() || !"successful".equals(payment.getStatus())) {
            log.error("Payment is not eligible for verification, status [{}], inProgress [{}]",
                    payment.getStatus(), payment.isInProgress());
            return;
        }
        invoiceDao.getInvoiceByRef(payment.getBillReference())
                .ifPresentOrElse(invoice -> {
                    CustomizedFWConfigs customizedFWConfigs = flutterWaveConfigService.getUserFWConfigs(payment.getAccountId(), invoice.getPropertyId());
                    boolean verifiedSuccessfully = false;
                    boolean verificationResponseReceived = false;
                    double verifiedAmount = 0;
                    try {
                        FWVerifyTransactionDTO verifyTransactionResponse = restTemplateService
                                .sendGetRequest(flutterWaveConfigService.flutterWaveUrl + flutterWaveConfigService.TRANSACTIONS_PAYMENT_URL_SUFFIX + "/" + payment.getThirdPartyTransId() + flutterWaveConfigService.VERIFY_PAYMENT_URL_SUFFIX,
                                        flutterWaveConfigService.buildAuthHeaders(customizedFWConfigs.secretKey()), FWVerifyTransactionDTO.class);
                        eventService.saveEvent(verifyTransactionResponse, payment.getId());
                        verificationResponseReceived = verifyTransactionResponse != null
                                && verifyTransactionResponse.data() != null;
                        verifiedSuccessfully = isVerifiedTransaction(payment, invoice, verifyTransactionResponse);
                        if (verificationResponseReceived) {
                            verifiedAmount = verifyTransactionResponse.data().amount();
                            if (verifiedSuccessfully) {
                                payment.setStatus(verifyTransactionResponse.data().status());
                                payment.setStatusDesc(verifyTransactionResponse.data().processorResponse());
                                payment.setThirdPartyTransId(String.valueOf(verifyTransactionResponse.data().id()));
                            } else {
                                payment.setStatus("verification_failed");
                                payment.setStatusDesc("Flutterwave verification mismatch");
                            }
                        }

                    } catch (RestRequestException e) {
                        eventService.saveEvent(e.getMessage(), e.getHttpStatusCode(), payment.getId());
                        log.error("Exception occurred while sending verify transaction request [{}]", payment.getId(), e);
                    } catch (Exception e) {
                        eventService.saveEvent(e.getMessage(), 500, payment.getId());
                        log.error("Unhandled exception occurred while sending verify transaction request [{}]", payment.getId(), e);
                    } finally {
                        payment.setVerificationRetries(payment.getVerificationRetries() + 1);
                        if (verifiedSuccessfully) {
                            payment.setInProgress(false);
                            paymentDao.savePMSPayment(payment);
                            updatePaymentService.setInvoiceToPaid(invoice, payment.getThirdPartyTransId(), verifiedAmount);
                        } else if (verificationResponseReceived) {
                            payment.setInProgress(false);
                            paymentDao.savePMSPayment(payment);
                            updatePaymentService.setInvoiceTransactionStatusByBillRefNumber(payment.getBillReference(), false);
                        } else {
                            paymentDao.savePMSPayment(payment);
                            scheduleVerifyFlutterWaveTransaction(payment.getId(), payment.getVerificationRetries());
                        }
                    }
                }, () -> log.error("Payment ref [{}] not found", payment.getBillReference()));

    }

    static boolean isVerifiedTransaction(PMSPayment payment, PMSInvoice invoice,
                                         FWVerifyTransactionDTO response) {
        if (payment == null || invoice == null || response == null || response.data() == null) {
            return false;
        }
        FWChargeCompletedData data = response.data();
        return "success".equalsIgnoreCase(response.status())
                && "successful".equalsIgnoreCase(data.status())
                && String.valueOf(payment.getId()).equals(data.txRef())
                && String.valueOf(data.id()).equals(payment.getThirdPartyTransId())
                && BigDecimal.valueOf(payment.getAmount()).compareTo(BigDecimal.valueOf(data.amount())) == 0
                && StringUtils.equalsIgnoreCase(invoice.getCurrency(), data.currency());
    }

    private void loadPendingVerifyTransactionsFromDb() {
        invoiceDao.loadPendingVerifyTransactionsFromDb(PaymentChannel.FLUTTER_WAVE, "success")
                .forEach(payment -> scheduleVerifyFlutterWaveTransaction(payment.getId(), payment.getVerificationRetries()));
        log.info("{} pending verify transactions loaded from DB", scheduledFutures.size());
    }
}
