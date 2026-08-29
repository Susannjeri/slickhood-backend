package org.pms.silverocean.service.account.dao;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PaymentAccountPropertyRepo;
import org.pms.silverocean.database.pms.PaymentAccountRepo;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.PaymentAccountProperty;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.pms.silverocean.service.account.dao.AccountSpecifications.searchAccounts;

@Service
public class AccountDao {

    private final PaymentAccountRepo accountRepo;
    private final PaymentAccountPropertyRepo propertyRepo;
    private final AuditLogService auditLogService;

    public AccountDao(PaymentAccountRepo accountRepo,
                      PaymentAccountPropertyRepo propertyRepo,
                      AuditLogService auditLogService) {
        this.accountRepo = accountRepo;
        this.propertyRepo = propertyRepo;
        this.auditLogService = auditLogService;
    }

    @Transactional("pmsDBTransactionManager")
    public PaymentAccount createAccount(PaymentAccount account) {
        PaymentAccount saved = accountRepo.save(account);
        auditLogService.createAuditLog(saved, Permission.CREATE_ACCOUNT);
        return saved;
    }

    @Transactional("pmsDBTransactionManager")
    public void deleteAccount(PaymentAccount account) {
        account.setActive(false);
        accountRepo.save(account);
        auditLogService.createAuditLog(account, Permission.DELETE_ACCOUNT);
    }

    @Transactional("pmsDBTransactionManager")
    public void verifyAccount(PaymentAccount account) {
        account.setVerified(true);
        accountRepo.save(account);
        auditLogService.createAuditLog(account, Permission.VERIFY_ACCOUNT);
    }

    @Transactional("pmsDBTransactionManager")
    public PaymentAccountProperty upsertProperty(PaymentAccountProperty prop) {
        prop.setLastModifiedDate(LocalDateTime.now());
        PaymentAccountProperty saved = propertyRepo.save(prop);
        auditLogService.createAuditLog(saved, Permission.EDIT_ACCOUNT);
        return saved;
    }

    public Page<PaymentAccount> listAll(Pageable pageable, final Optional<String> landlordEmail, Optional<PaymentChannel> channel, boolean active, boolean verified) {
        return accountRepo.findAll(searchAccounts(landlordEmail, channel, active, verified), pageable);
    }

    public Page<PaymentAccount> listByProperty(Pageable pageable, long propertyId, long userId) {
        return accountRepo.listAccountsByProperty(pageable, propertyId, userId);
    }

    public Page<PaymentAccount> listByOwner(Long ownerId, Pageable pageable) {
        return accountRepo.findByCreatedByAndActiveTrue(ownerId, pageable);
    }

    public PaymentAccount getAccountById(Long id) {
        return accountRepo.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND));
    }

    public Page<PaymentAccount> getActiveSlickHoodAccount(Pageable pageable) {
        return accountRepo.listAllActiveSlickHoodAccount(pageable);
    }

    public Page<PaymentAccount> getActiveAndVerifiedSlickHoodAccount(Pageable pageable) {
        return accountRepo.listSlickHoodAccountByVerifiedTrueAndActive(pageable);
    }

    public PaymentAccount getAccountByIdAndCreatedBy(Long id, long userId) {
        return accountRepo.findByIdAndActiveTrueAndCreatedBy(id, userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND));
    }

    public List<PaymentAccountProperty> getPropertiesForAccount(Long accountId) {
        return propertyRepo.findByAccountId(accountId);
    }

    public Optional<PaymentAccountProperty> getProperty(Long accountId, String key) {
        return propertyRepo.findByAccountIdAndPropertyKey(accountId, key);
    }
}
