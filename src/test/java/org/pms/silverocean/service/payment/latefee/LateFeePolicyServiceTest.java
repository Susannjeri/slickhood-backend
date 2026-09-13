package org.pms.silverocean.service.payment.latefee;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.PMSInvoiceRepo;
import org.pms.silverocean.database.pms.ReceivableLateFeePolicyRepo;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.ReceivableLateFeePolicy;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.payment.invoice.InvoiceService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LateFeePolicyServiceTest {
    @Mock ReceivableLateFeePolicyRepo policies;
    @Mock PMSInvoiceRepo invoices;
    @Mock InvoiceService invoiceService;
    @Mock UserDao users;
    @Mock NotificationService notifications;
    @Mock I18NService i18n;

    @Test
    void billerCanExplicitlySavePercentageAndGracePeriod() {
        when(users.getActiveRole()).thenReturn(PMSRole.SALES_AGENT);
        when(users.getUserId()).thenReturn(41L);
        when(policies.findByCreatedByAndBillingTypeAndActiveTrue(41L, "SALE")).thenReturn(Optional.empty());
        when(policies.save(any())).thenAnswer(invocation -> {
            ReceivableLateFeePolicy saved = invocation.getArgument(0); saved.setId(7L); return saved;
        });

        var result = service().save("sale", new LateFeePolicyModels.Update(new BigDecimal("2.50"), 3, true));

        assertThat(result.configured()).isTrue();
        assertThat(result.percentageRate()).isEqualByComparingTo("2.5000");
        assertThat(result.graceDays()).isEqualTo(3);
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void overduePrincipalCreatesOneSeparateNonCompoundingFeeInvoice() {
        PMSInvoice source = source();
        ReceivableLateFeePolicy policy = new ReceivableLateFeePolicy();
        policy.setCreatedBy(41L); policy.setBillingType("SALE"); policy.setPercentageRate(new BigDecimal("2.5000"));
        policy.setGraceDays(2); policy.setEnabled(true); policy.setActive(true);
        policy.setEffectiveFrom(LocalDate.now().minusDays(30));
        PMSInvoice fee = new PMSInvoice(); fee.setId(12L); fee.setRef("INV-C"); fee.setAmount(25); fee.setActive(true);
        when(invoices.findByIdForUpdate(10L)).thenReturn(Optional.of(source));
        when(policies.findByCreatedByAndBillingTypeAndActiveTrue(41L, "SALE")).thenReturn(Optional.of(policy));
        when(invoiceService.createLateFeeInvoice(eq(source), any(), eq(policy.getPercentageRate()), any())).thenReturn(fee);
        Users payer = user(30L, "buyer@example.test"); Users biller = user(41L, "seller@example.test");
        when(users.findById(30L)).thenReturn(Optional.of(payer)); when(users.findById(41L)).thenReturn(Optional.of(biller));
        when(i18n.getLocalizedMessage(NotificationType.LATE_FEE_ASSESSED_EMAIL.getBody()))
                .thenReturn("%s %s %s %s %s %s %s %s");
        when(i18n.getLocalizedMessage(NotificationType.LATE_FEE_BILLER_EMAIL.getBody()))
                .thenReturn("%s %s %s %s %s");

        var assessed = service().assess(10L);

        assertThat(assessed).contains(fee);
        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(invoiceService).createLateFeeInvoice(eq(source), amount.capture(), eq(policy.getPercentageRate()), any());
        assertThat(amount.getValue()).isEqualByComparingTo("25.00");
        verify(notifications, times(2)).queueEmailAndInApp(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void feeInvoiceCannotBeChargedAgain() {
        PMSInvoice fee = source(); fee.setLateFeeSourceInvoiceId(9L);
        when(invoices.findByIdForUpdate(10L)).thenReturn(Optional.of(fee));
        assertThat(service().assess(10L)).isEmpty();
        verifyNoInteractions(policies, invoiceService, notifications);
    }

    private LateFeePolicyService service() {
        return new LateFeePolicyService(policies, invoices, invoiceService, users, notifications, i18n);
    }
    private PMSInvoice source() {
        PMSInvoice invoice = new PMSInvoice(); invoice.setId(10L); invoice.setRef("INV-A"); invoice.setActive(true);
        invoice.setPaid(false); invoice.setPendingAmount(1000); invoice.setBillingType("SALE");
        invoice.setDueDate(LocalDate.now().minusDays(5)); invoice.setBilledUserId(30L); invoice.setPayToUserId(41L);
        invoice.setCurrency("KES"); invoice.setUnitId(4L); invoice.setPropertyId(3L); return invoice;
    }
    private Users user(long id, String email) {
        Users user = new Users(); user.setId(id); user.setEmail(email); user.setFullName(email); user.setActive(true); return user;
    }
}
