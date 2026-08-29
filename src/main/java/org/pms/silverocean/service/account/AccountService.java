package org.pms.silverocean.service.account;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.PaymentAccountProperty;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.dto.AccountDTO;
import org.pms.silverocean.service.account.dto.AccountPropertyDTO;
import org.pms.silverocean.service.account.dto.AccountSummaryDTO;
import org.pms.silverocean.service.account.dto.CreateAccountRequestDTO;
import org.pms.silverocean.service.account.dto.UpdateAccountPropertyRequestDTO;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.payment.wrappers.AccountPropertyDefinition;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.security.DecryptDTO;
import org.pms.silverocean.service.security.EncryptionService;
import org.pms.silverocean.database.pms.CommunityFundRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountDao accountDao;
    private final UserDao userDao;
    private final EncryptionService encryptionService;
    private final I18NService i18NService;
    private final PaymentPlatformFactory paymentPlatformFactory;
    private final NotificationService notificationService;
    private final CommunityFundRepo communityFundRepo;


    public AccountDTO createAccount(CreateAccountRequestDTO dto) {
        if (userDao.getActiveRole() == PMSRole.ESTATE_MANAGER && dto.category() != AccountCategory.COMMUNITY_FUND) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_INSUFFICIENT_PERMISSIONS);
        }
        if (AccountCategory.SLICKHOOD.equals(dto.category()) && !userDao.hasRole(PMSRole.SUPER_ADMIN)) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_INSUFFICIENT_PERMISSIONS);
        }
        if (AccountCategory.INSURANCE.equals(dto.category()) &&
                !userDao.hasPermission(org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_INSURANCE_PAYMENT_CONFIG)) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_INSUFFICIENT_PERMISSIONS);
        }
        if (AccountCategory.COMMUNITY_FUND.equals(dto.category()) &&
                !userDao.hasPermission(org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_COMMUNITY_FUNDS)) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_INSUFFICIENT_PERMISSIONS);
        }
        PaymentAccount account = new PaymentAccount();
        account.setChannel(dto.channel());
        account.setName(dto.name().trim());
        account.setCategory(dto.category());
        account.setCreatedBy(userDao.getUserId());
        account.setActive(true);

        PaymentAccount saved = accountDao.createAccount(account);
        return new AccountDTO(saved, List.of(), paymentPlatformFactory.getChannelImage(dto.channel()));
    }

    public Page<AccountSummaryDTO> listAccounts(Pageable pageable,
                                                Optional<Long> propertyId,
                                                Optional<Boolean> byLandlord,
                                                Optional<Boolean> isSlickHood,
                                                Optional<String> landlordEmail,
                                                Optional<PaymentChannel> channel,
                                                boolean active, boolean verified) {
        Long userId = userDao.getUserId();
        if (propertyId.isPresent()) {
            return accountDao.listByProperty(pageable, propertyId.get(), userId)
                    .map(account -> new AccountSummaryDTO(account, paymentPlatformFactory.getChannelImage(account.getChannel())));
        }
        if (byLandlord.orElse(false)) {
            return accountDao.listByOwner(userId, pageable).map(account -> new AccountSummaryDTO(account, paymentPlatformFactory.getChannelImage(account.getChannel())));
        }
        if (isSlickHood.orElse(false)) {
            Page<PaymentAccount> paymentAccounts = userDao.hasRole(PMSRole.SUPER_ADMIN) ? accountDao.getActiveSlickHoodAccount(pageable) : accountDao.getActiveAndVerifiedSlickHoodAccount(pageable);
            return paymentAccounts
                    .map(account -> new AccountSummaryDTO(account, paymentPlatformFactory.getChannelImage(account.getChannel())));
        }

        if (userDao.hasRole(PMSRole.SUPER_ADMIN)) {
            return accountDao.listAll(pageable, landlordEmail, channel, active, verified)
                    .map(account -> new AccountSummaryDTO(account, paymentPlatformFactory.getChannelImage(account.getChannel())));
        } else if (userDao.hasRole(PMSRole.LANDLORD) || userDao.hasRole(PMSRole.SERVICE_PROVIDER) ||
                userDao.hasRole(PMSRole.INSURANCE_MANAGER) ||
                userDao.hasPermission(org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_COMMUNITY_FUNDS)) {
            return accountDao.listByOwner(userId, pageable).map(account -> new AccountSummaryDTO(account, paymentPlatformFactory.getChannelImage(account.getChannel())));
        }
        return Page.empty();
    }

    public void verifyAccount(long accountId, boolean verify, String comments) {
        PaymentAccount account = accountDao.getAccountById(accountId);
        for (AccountPropertyDTO accountProperty : buildPropertyDTOs(account)) {
            if (StringUtils.isBlank(accountProperty.value()) && verify) {
                throw new PMSCustomException(ResponseCode.ACCOUNT_INCOMPLETE_PROPERTIES);
            }
        }
        NotificationDTO notification;
        String email = userDao.findById(account.getCreatedBy()).map(Users::getEmail)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_EMAIL));
        if (verify) {
            accountDao.verifyAccount(account);
            String message = String.format(i18NService.getLocalizedMessage(NotificationType.ACCOUNT_VERIFICATION_SUCCESS_EMAIL.getBody()),
                    account.getName());
            notification = new NotificationDTO(message, email, NotificationType.ACCOUNT_VERIFICATION_SUCCESS_EMAIL);
        } else {
            if (StringUtils.isBlank(comments)) {
                throw new PMSCustomException(ResponseCode.ACCOUNT_VERIFICATION_INVALID_COMMENTS);
            }
            String message = String.format(i18NService.getLocalizedMessage(NotificationType.ACCOUNT_VERIFICATION_FAILED_EMAIL.getBody()),
                    account.getName(), comments);
            notification = new NotificationDTO(message, email, NotificationType.ACCOUNT_VERIFICATION_FAILED_EMAIL);
        }
        notificationService.sendNotification(notification);
    }

    public void requestVerification(long accountId) {
        PaymentAccount account = accountDao.getAccountByIdAndCreatedBy(accountId, userDao.getUserId());
        if (account.isVerified()) {
            throw new PMSCustomException(ResponseCode.PARAM_VERIFIED);
        }
        String message = String.format(i18NService.getLocalizedMessage(NotificationType.ACCOUNT_VERIFICATION_REQUEST_EMAIL.getBody()),
                userDao.getEmail(), account.getName(), account.getChannel().getName(), accountId);
        notificationService.sendEmailToSuperAdmin(NotificationType.ACCOUNT_VERIFICATION_REQUEST_EMAIL, message);
    }

    public AccountDTO getAccount(Long accountId) {
        PaymentAccount account = accountDao.getAccountById(accountId);
        List<AccountPropertyDTO> properties = buildPropertyDTOs(account);
        return new AccountDTO(account, properties, paymentPlatformFactory.getChannelImage(account.getChannel()));
    }

    public void updateAccountProperty(Long accountId, UpdateAccountPropertyRequestDTO dto) {
        PaymentAccount account = accountDao.getAccountById(accountId);
        assertOwnerOrAdmin(account);

        AccountPropertyDefinition definition = account.getChannel().findProperty(dto.key());

        PaymentAccountProperty prop = accountDao.getProperty(accountId, dto.key())
                .orElseGet(() -> {
                    PaymentAccountProperty p = new PaymentAccountProperty();
                    p.setAccountId(accountId);
                    p.setPropertyKey(dto.key());
                    return p;
                });

        if (definition.encrypted()) {
            prop.setValue(encryptionService.encrypt(dto.value()));
            prop.setEncrypted(true);
        } else {
            prop.setValue(dto.value().getBytes(StandardCharsets.UTF_8));
            prop.setEncrypted(false);
        }
        prop.setLastModifiedDate(LocalDateTime.now());
        accountDao.upsertProperty(prop);
    }

    public String decryptAccountProperty(Long accountId, String key) {
        PaymentAccount account = accountDao.getAccountById(accountId);
        assertOwnerOrAdmin(account);

        PaymentAccountProperty prop = accountDao.getProperty(accountId, key)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND));

        if (!prop.isEncrypted()) {
            return new String(prop.getValue(), StandardCharsets.UTF_8);
        }

        DecryptDTO result = encryptionService.decrypt(prop.getValue());
        if (result.usedOldKey()) {
            prop.setValue(encryptionService.encrypt(result.decryptedValue()));
            prop.setLastModifiedDate(LocalDateTime.now());
            accountDao.upsertProperty(prop);
        }
        return result.decryptedValue();
    }

    public void deleteAccount(Long accountId) {
        PaymentAccount account = accountDao.getAccountById(accountId);
        assertOwnerOrAdmin(account);
        if (communityFundRepo.existsByPaymentAccountIdAndActiveTrueAndStatusIn(accountId,List.of("DRAFT","OPEN","FROZEN"))) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_UNAUTHORIZED);
        }
        accountDao.deleteAccount(account);
    }

    public List<AccountPropertyDTO> getChannelProperties(org.pms.silverocean.service.payment.wrappers.PaymentChannel channel) {
        return channel.getAccountProperties().stream()
                .map(def -> new AccountPropertyDTO(
                        def.key(),
                        i18NService.getLocalizedMessage(def.labelKey()),
                        i18NService.getLocalizedMessage(def.descriptionKey()),
                        null,
                        def.encrypted(),
                        def.displayField()))
                .toList();
    }

    private List<AccountPropertyDTO> buildPropertyDTOs(PaymentAccount account) {
        List<PaymentAccountProperty> storedProps = accountDao.getPropertiesForAccount(account.getId());

        return account.getChannel().getAccountProperties().stream().map(def -> {
            Optional<PaymentAccountProperty> stored = storedProps.stream()
                    .filter(p -> p.getPropertyKey().equalsIgnoreCase(def.key()))
                    .findFirst();

            if (stored.isEmpty()) {
                return new AccountPropertyDTO(
                        def.key(),
                        i18NService.getLocalizedMessage(def.labelKey()),
                        i18NService.getLocalizedMessage(def.descriptionKey()),
                        "",
                        def.encrypted(),
                        def.displayField());
            }

            PaymentAccountProperty prop = stored.get();
            String displayValue = resolveDisplayValue(def, prop);

            return new AccountPropertyDTO(
                    def.key(),
                    i18NService.getLocalizedMessage(def.labelKey()),
                    i18NService.getLocalizedMessage(def.descriptionKey()),
                    displayValue,
                    def.encrypted(),
                    def.displayField());
        }).toList();
    }

    private String resolveDisplayValue(AccountPropertyDefinition def, PaymentAccountProperty prop) {
        if (!def.encrypted()) {
            return new String(prop.getValue(), StandardCharsets.UTF_8);
        }
        if (def.displayField()) {
            DecryptDTO result = encryptionService.decrypt(prop.getValue());
            if (result.usedOldKey()) {
                prop.setValue(encryptionService.encrypt(result.decryptedValue()));
                prop.setLastModifiedDate(LocalDateTime.now());
                accountDao.upsertProperty(prop);
            }
            return result.decryptedValue();
        }
        return "*****";
    }

    private void assertOwnerOrAdmin(PaymentAccount account) {
        long currentUserId = userDao.getUserId();
        boolean isOwner = account.getCreatedBy() != null && account.getCreatedBy().equals(currentUserId);
        boolean isSuperAdmin = userDao.hasPermission(org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_ALL_ACCOUNTS);
        if (!isOwner && !isSuperAdmin) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_UNAUTHORIZED);
        }
    }
}
