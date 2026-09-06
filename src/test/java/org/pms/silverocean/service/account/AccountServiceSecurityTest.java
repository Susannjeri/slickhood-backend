package org.pms.silverocean.service.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.CommunityFundRepo;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.PaymentAccountProperty;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.dto.CreateAccountRequestDTO;
import org.pms.silverocean.service.account.dto.UpdateAccountPropertyRequestDTO;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceSecurityTest {

    @Mock AccountDao accountDao;
    @Mock UserDao userDao;
    @Mock EncryptionService encryptionService;
    @Mock I18NService i18NService;
    @Mock PaymentPlatformFactory paymentPlatformFactory;
    @Mock NotificationService notificationService;
    @Mock CommunityFundRepo communityFundRepo;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks AccountService service;

    @Test
    void accountDetailRejectsAUserWhoIsNeitherOwnerNorAdministrator() {
        PaymentAccount account = account(42L, 99L, AccountCategory.LANDLORD, PaymentChannel.MPESA, false);
        when(accountDao.getAccountById(42L)).thenReturn(account);
        when(userDao.getUserId()).thenReturn(7L);
        when(userDao.hasPermission("view_all_accounts")).thenReturn(false);

        assertThatThrownBy(() -> service.getAccount(42L)).isInstanceOf(PMSCustomException.class);
        verify(accountDao, never()).getPropertiesForAccount(any());
    }

    @Test
    void accountOwnerCanReadTheirOwnMaskedDetail() {
        PaymentAccount account = account(42L, 7L, AccountCategory.LANDLORD, PaymentChannel.MPESA, false);
        when(accountDao.getAccountById(42L)).thenReturn(account);
        when(userDao.getUserId()).thenReturn(7L);
        when(accountDao.getPropertiesForAccount(42L)).thenReturn(List.of());
        when(paymentPlatformFactory.getChannelImage(PaymentChannel.MPESA)).thenReturn("icon");

        assertThat(service.getAccount(42L).id()).isEqualTo(42L);
    }

    @Test
    void internalSafeDetailsNeverDecryptAnEncryptedDisplayField() {
        PaymentAccount account = account(42L, 7L, AccountCategory.LANDLORD, PaymentChannel.PAYSTACK, true);
        PaymentAccountProperty property = new PaymentAccountProperty();
        property.setAccountId(42L);
        property.setPropertyKey(PaymentPropertyKeys.SUBACCOUNT_CODE);
        property.setValue("ciphertext".getBytes(StandardCharsets.UTF_8));
        property.setEncrypted(true);
        when(accountDao.getAccountById(42L)).thenReturn(account);
        when(accountDao.getPropertiesForAccount(42L)).thenReturn(List.of(property));
        when(paymentPlatformFactory.getChannelImage(PaymentChannel.PAYSTACK)).thenReturn("icon");

        assertThat(service.getAccountSafeDetails(42L).properties()).singleElement()
                .extracting("value").isEqualTo("*****");
        verifyNoInteractions(encryptionService);
    }

    @Test
    void landlordCannotCreateAMerchantAccountByCallingTheApiDirectly() {
        when(userDao.getActiveRole()).thenReturn(PMSRole.LANDLORD);

        assertThatThrownBy(() -> service.createAccount(new CreateAccountRequestDTO(
                PaymentChannel.MPESA, "Wrong category", AccountCategory.MERCHANT)))
                .isInstanceOf(PMSCustomException.class);
        verify(accountDao, never()).createAccount(any());
    }

    @Test
    void serviceProviderCanCreateOnlyTheirMerchantAccount() {
        when(userDao.getActiveRole()).thenReturn(PMSRole.SERVICE_PROVIDER);
        when(userDao.getUserId()).thenReturn(7L);
        when(accountDao.createAccount(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentPlatformFactory.getChannelImage(PaymentChannel.MPESA)).thenReturn("icon");

        service.createAccount(new CreateAccountRequestDTO(PaymentChannel.MPESA, "Shop collections", AccountCategory.MERCHANT));

        ArgumentCaptor<PaymentAccount> saved = ArgumentCaptor.forClass(PaymentAccount.class);
        verify(accountDao).createAccount(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(7L);
        assertThat(saved.getValue().getCategory()).isEqualTo(AccountCategory.MERCHANT);
    }

    @Test
    void salesAgentCanCreateOnlyAPropertySalesAccount() {
        when(userDao.getActiveRole()).thenReturn(PMSRole.SALES_AGENT);
        when(userDao.getUserId()).thenReturn(7L);
        when(accountDao.createAccount(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentPlatformFactory.getChannelImage(PaymentChannel.MPESA)).thenReturn("icon");

        service.createAccount(new CreateAccountRequestDTO(
                PaymentChannel.MPESA, "Completion proceeds", AccountCategory.PROPERTY_SALES));

        ArgumentCaptor<PaymentAccount> saved = ArgumentCaptor.forClass(PaymentAccount.class);
        verify(accountDao).createAccount(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(7L);
        assertThat(saved.getValue().getCategory()).isEqualTo(AccountCategory.PROPERTY_SALES);
    }

    @Test
    void estateManagerCanCreateAnEstateOperatingAccount() {
        when(userDao.getActiveRole()).thenReturn(PMSRole.ESTATE_MANAGER);
        when(userDao.getUserId()).thenReturn(7L);
        when(accountDao.createAccount(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentPlatformFactory.getChannelImage(PaymentChannel.MPESA)).thenReturn("icon");

        service.createAccount(new CreateAccountRequestDTO(
                PaymentChannel.MPESA, "Service charge collections", AccountCategory.ESTATE_MANAGEMENT));

        ArgumentCaptor<PaymentAccount> saved = ArgumentCaptor.forClass(PaymentAccount.class);
        verify(accountDao).createAccount(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(7L);
        assertThat(saved.getValue().getCategory()).isEqualTo(AccountCategory.ESTATE_MANAGEMENT);
    }

    @Test
    void salesAgentCannotCreateALandlordAccount() {
        when(userDao.getActiveRole()).thenReturn(PMSRole.SALES_AGENT);

        assertThatThrownBy(() -> service.createAccount(new CreateAccountRequestDTO(
                PaymentChannel.MPESA, "Wrong category", AccountCategory.LANDLORD)))
                .isInstanceOf(PMSCustomException.class);
        verify(accountDao, never()).createAccount(any());
    }

    @Test
    void changingAFieldRevokesVerificationAndPublishesCacheInvalidationEvent() {
        PaymentAccount account = account(42L, 7L, AccountCategory.LANDLORD, PaymentChannel.MPESA, true);
        when(accountDao.getAccountById(42L)).thenReturn(account);
        when(userDao.getUserId()).thenReturn(7L);
        when(accountDao.getProperty(42L, PaymentPropertyKeys.CONSUMER_SECRET)).thenReturn(Optional.empty());
        when(encryptionService.encrypt("new-secret")).thenReturn("ciphertext".getBytes(StandardCharsets.UTF_8));

        service.updateAccountProperty(42L,
                new UpdateAccountPropertyRequestDTO(PaymentPropertyKeys.CONSUMER_SECRET, "new-secret"));

        verify(accountDao).updateVerification(account, false);
        verify(accountDao).upsertProperty(any(PaymentAccountProperty.class));
        verify(eventPublisher).publishEvent(new PaymentAccountCredentialsChangedEvent(42L, PaymentChannel.MPESA));
    }

    @Test
    void rejectingVerificationPersistsTheUnverifiedState() {
        PaymentAccount account = account(42L, 7L, AccountCategory.LANDLORD, PaymentChannel.MPESA, true);
        Users owner = org.mockito.Mockito.mock(Users.class);
        when(owner.getEmail()).thenReturn("owner@example.test");
        when(accountDao.getAccountById(42L)).thenReturn(account);
        when(accountDao.getPropertiesForAccount(42L)).thenReturn(List.of());
        when(userDao.findById(7L)).thenReturn(Optional.of(owner));
        when(i18NService.getLocalizedMessage(any(String.class))).thenReturn("Account %s: %s");

        service.verifyAccount(42L, false, "Paybill ownership could not be confirmed");

        verify(accountDao).updateVerification(account, false);
    }

    @Test
    void slickhoodPaystackAccountDoesNotAskForAMerchantSubaccount() {
        PaymentAccount account = account(42L, 7L, AccountCategory.SLICKHOOD, PaymentChannel.PAYSTACK, false);
        when(accountDao.getAccountById(42L)).thenReturn(account);
        when(userDao.getUserId()).thenReturn(7L);
        assertThat(service.getAccount(42L).properties()).isEmpty();
        verify(accountDao, never()).getPropertiesForAccount(42L);
    }

    private static PaymentAccount account(long id, long ownerId, AccountCategory category,
                                          PaymentChannel channel, boolean verified) {
        PaymentAccount account = new PaymentAccount();
        account.setId(id);
        account.setCreatedBy(ownerId);
        account.setCategory(category);
        account.setChannel(channel);
        account.setName("Collections");
        account.setActive(true);
        account.setVerified(verified);
        return account;
    }
}
