package org.pms.silverocean.service.param;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.PaymentAccountProperty;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.property.PropertyDao;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentParameterRoutingTest {
    final AccountDao accounts = mock(AccountDao.class);
    final PropertyDao properties = mock(PropertyDao.class);
    final ParamService service = new ParamService(accounts, null, null, null, properties, null, null, null);
    final org.pms.silverocean.service.payment.wrappers.AccountPropertyDefinition field = PaymentChannel.MPESA.findProperty(PaymentPropertyKeys.PAYBILL);

    PaymentAccount destination(AccountCategory category, boolean verified) {
        PaymentAccount account = new PaymentAccount(); account.setId(71L); account.setActive(true);
        account.setVerified(verified); account.setCategory(category); account.setChannel(PaymentChannel.MPESA);
        when(accounts.getAccountById(71L)).thenReturn(account); return account;
    }
    PaymentAccountProperty value() {
        PaymentAccountProperty value = new PaymentAccountProperty(); value.setValue("174379".getBytes(StandardCharsets.UTF_8)); return value;
    }
    @Test void verifiedSubscriptionAccountDoesNotNeedAFakeProperty() {
        destination(AccountCategory.SLICKHOOD, true); when(accounts.getProperty(71L, field.key())).thenReturn(Optional.of(value()));
        assertEquals("174379", service.getParamByAccountIdAndType(71L, field, 0)); verifyNoInteractions(properties);
    }
    @Test void landlordCannotBypassPropertyBindingWithZeroProperty() {
        destination(AccountCategory.LANDLORD, true);
        assertThrows(PMSCustomException.class, () -> service.getParamByAccountIdAndType(71L, field, 0));
        verify(accounts, never()).getProperty(anyLong(), anyString());
    }
    @Test void unverifiedPlatformDestinationIsRejected() {
        destination(AccountCategory.SLICKHOOD, false);
        assertThrows(PMSCustomException.class, () -> service.getParamByAccountIdAndType(71L, field, 0));
    }
    @Test void propertyPaymentsRetainTheExactPropertyScopedQuery() {
        when(properties.getParamByAccountIdAndTypeAndPropertyId(71L, field.key(), 44L)).thenReturn(Optional.of(value()));
        assertEquals("174379", service.getParamByAccountIdAndType(71L, field, 44L)); verifyNoInteractions(accounts);
    }
}
