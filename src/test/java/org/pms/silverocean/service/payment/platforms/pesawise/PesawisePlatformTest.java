package org.pms.silverocean.service.payment.platforms.pesawise;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.wrappers.AccountPropertyDefinition;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PesawisePlatformTest {
    @Mock UpdatePaymentService updater;
    @Mock PaymentDao payments;
    @Mock AccountDao accounts;
    @Mock ParamService params;
    @Mock RestTemplateService http;
    @Mock EventService events;
    PesawisePlatform platform;

    @BeforeEach void setUp() {
        platform = new PesawisePlatform(updater, payments, accounts, params, http, events, new ObjectMapper());
        ReflectionTestUtils.setField(platform, "enabled", true);
        ReflectionTestUtils.setField(platform, "apiUrl", "https://api.pesawise.xyz");
        ReflectionTestUtils.setField(platform, "icon", "mpesa_icon.png");
    }

    @Test void subscriptionStkSettlesOnlyAfterAuthenticatedServerSideVerification() {
        PMSInvoice invoice = invoice();
        AtomicReference<PMSPayment> stored = new AtomicReference<>();
        doAnswer(call -> { PMSPayment p=call.getArgument(0); if(p.getId()==null)p.setId(501L); stored.set(p); return null; })
                .when(payments).savePMSPayment(any(PMSPayment.class));
        when(accounts.getAccountById(91L)).thenReturn(account());
        when(params.getParamByAccountIdAndType(eq(91L), any(AccountPropertyDefinition.class), anyLong()))
                .thenAnswer(call -> switch (((AccountPropertyDefinition) call.getArgument(1)).key()) {
                    case "pesawise_api_key" -> "test-api-key";
                    case "pesawise_api_secret" -> "test-api-secret";
                    case "pesawise_balance_id" -> "100121";
                    case "pesawise_webhook_secret" -> "test-webhook-secret";
                    default -> throw new AssertionError("Unexpected property");
                });
        when(http.sendPostRequest(eq("https://api.pesawise.xyz/api/payments/stk-push"), any(),
                any(HttpHeaders.class), eq(PesawisePlatform.PesawisePaymentResponse.class)))
                .thenReturn(new PesawisePlatform.PesawisePaymentResponse(
                        "f9eb1b23-4f78-495c-968c-9dfdfc89a16d", true, "STK sent", "INV-SUB-1"));

        var initialized = platform.processPayment(invoice, "+254712345678", 91L);
        assertThat(initialized.success()).isTrue();
        assertThat(stored.get().getThirdPartyTransId()).isEqualTo("f9eb1b23-4f78-495c-968c-9dfdfc89a16d");

        when(payments.findPaymentByThirdPartyID("f9eb1b23-4f78-495c-968c-9dfdfc89a16d"))
                .thenReturn(Optional.of(stored.get()));
        when(updater.getInvoicePayToIDUsingInvoiceRef("INV-SUB-1")).thenReturn(Optional.of(invoice));
        when(payments.providerReceiptAlreadyProcessed(PaymentChannel.PESAWISE.getName(), "PW-TEST-1", 501L))
                .thenReturn(false);
        when(http.sendGetRequest(anyString(), any(HttpHeaders.class), eq(PesawisePlatform.PesawisePaymentStatus.class)))
                .thenReturn(new PesawisePlatform.PesawisePaymentStatus("SUCCESS",
                        "f9eb1b23-4f78-495c-968c-9dfdfc89a16d", "INV-SUB-1", "PW-TEST-1",
                        1000D, "KES", "Payment completed", "100121"));
        String body="{\"requestId\":\"f9eb1b23-4f78-495c-968c-9dfdfc89a16d\",\"reference\":\"INV-SUB-1\",\"success\":true,\"details\":\"complete\",\"amount\":1000}";

        platform.handleCallBack(new PesawiseCallbackDTO(body, "test-webhook-secret",
                "merchant.stk-push.update", "127.0.0.1"));

        assertThat(stored.get().getProviderReceipt()).isEqualTo("PW-TEST-1");
        assertThat(stored.get().isInProgress()).isFalse();
        verify(updater).setInvoiceToPaid(invoice, "PW-TEST-1", 1000D);
    }

    @Test void invalidWebhookSecretCannotTriggerProviderVerificationOrSettlement() {
        PMSInvoice invoice = invoice();
        PMSPayment payment = new PMSPayment(invoice, "PesaWise customer", 91L);
        payment.setId(501L);
        payment.setChannel(PaymentChannel.PESAWISE.getName());
        payment.setThirdPartyTransId("f9eb1b23-4f78-495c-968c-9dfdfc89a16d");
        payment.setInProgress(true);
        when(payments.findPaymentByThirdPartyID(payment.getThirdPartyTransId())).thenReturn(Optional.of(payment));
        when(updater.getInvoicePayToIDUsingInvoiceRef("INV-SUB-1")).thenReturn(Optional.of(invoice));
        when(accounts.getAccountById(91L)).thenReturn(account());
        when(params.getParamByAccountIdAndType(eq(91L), any(AccountPropertyDefinition.class), anyLong()))
                .thenReturn("expected-webhook-secret");
        String body="{\"requestId\":\"f9eb1b23-4f78-495c-968c-9dfdfc89a16d\",\"reference\":\"INV-SUB-1\",\"success\":true}";

        assertThatThrownBy(() -> platform.handleCallBack(new PesawiseCallbackDTO(
                body, "wrong-webhook-secret", "merchant.stk-push.update", "127.0.0.1")))
                .isInstanceOf(PMSCustomException.class);

        verify(http, never()).sendGetRequest(anyString(), any(HttpHeaders.class), any());
        verify(updater, never()).setInvoiceToPaid(any(PMSInvoice.class), anyString(), anyDouble());
    }

    private static PMSInvoice invoice() {
        PMSInvoice i=new PMSInvoice(); i.setRef("INV-SUB-1"); i.setSubscriptionPlanCode("BRONZE");
        i.setBillingType("SUBSCRIPTION"); i.setPropertyId(0L); i.setPaymentAccountId(91L);
        i.setPayToUserId(77L); i.setBilledUserId(22L); i.setPendingAmount(1000D); i.setCurrency("KES");
        i.setCustomerPhoneNumber("+254712345678"); i.setActive(true); return i;
    }

    private static PaymentAccount account() {
        PaymentAccount a=new PaymentAccount(); a.setId(91L); a.setCreatedBy(77L);
        a.setCategory(AccountCategory.SLICKHOOD); a.setChannel(PaymentChannel.PESAWISE);
        a.setActive(true); a.setVerified(true); return a;
    }
}
