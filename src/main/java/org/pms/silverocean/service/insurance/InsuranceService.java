package org.pms.silverocean.service.insurance;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.InsuranceCompanyRepo;
import org.pms.silverocean.database.pms.InsurancePaymentConfigurationRepo;
import org.pms.silverocean.database.pms.entities.InsuranceCompany;
import org.pms.silverocean.database.pms.entities.InsurancePaymentConfiguration;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.AccountService;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.dto.AccountDTO;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.pms.silverocean.service.insurance.InsuranceModels.*;

@Service
@RequiredArgsConstructor
public class InsuranceService {
    private final InsuranceCompanyRepo companyRepo;
    private final InsurancePaymentConfigurationRepo configurationRepo;
    private final AccountDao accountDao;
    private final AccountService accountService;
    private final UserDao userDao;

    public List<CompanyView> companies() {
        return companyRepo.findByActiveTrueOrderByNameAsc().stream().map(this::view).toList();
    }

    public List<CompanyEmailConfigurationView> companyEmailConfigurations() {
        return companyRepo.findByActiveTrueOrderByNameAsc().stream().map(this::emailView).toList();
    }

    @Transactional("pmsDBTransactionManager")
    public CompanyEmailConfigurationView configureCompanyEmails(String companyCode,
                                                                 CompanyEmailConfigurationRequest request) {
        InsuranceCompany company = company(companyCode);
        company.setQuotationEmail(trimToNull(request.quotationEmail()));
        company.setClaimsEmail(trimToNull(request.claimsEmail()));
        company.setRenewalsEmail(trimToNull(request.renewalsEmail()));
        return emailView(companyRepo.save(company));
    }

    public List<PaymentConfigurationView> customerPaymentOptions(String companyCode) {
        InsuranceCompany company = company(companyCode);
        LocalDate today = LocalDate.now();
        Map<org.pms.silverocean.service.payment.wrappers.PaymentChannel, InsurancePaymentConfiguration> current =
                configurationRepo.findByCompanyIdAndActiveTrueOrderByPaymentChannelAsc(company.getId()).stream()
                        .filter(c -> !c.getEffectiveFrom().isAfter(today))
                        .filter(c -> c.getEffectiveTo() == null || !c.getEffectiveTo().isBefore(today))
                        .collect(Collectors.toMap(InsurancePaymentConfiguration::getPaymentChannel, c -> c,
                                (left, right) -> left.getVersion() >= right.getVersion() ? left : right));
        return current.values().stream().map(c -> paymentView(company, c, true))
                .filter(PaymentConfigurationView::accountVerified)
                .sorted(Comparator.comparing(v -> v.channel().name())).toList();
    }

    public List<PaymentConfigurationView> adminPaymentConfigurations(String companyCode) {
        InsuranceCompany company = company(companyCode);
        return configurationRepo.findByCompanyIdOrderByPaymentChannelAscVersionDesc(company.getId()).stream()
                .map(c -> paymentView(company, c, false)).toList();
    }

    @Transactional("pmsDBTransactionManager")
    public PaymentConfigurationView configurePayment(String companyCode, PaymentConfigurationRequest request) {
        InsuranceCompany company = company(companyCode);
        PaymentAccount account = accountDao.getAccountById(request.paymentAccountId());
        if (account.getCategory() != AccountCategory.INSURANCE) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        }
        List<InsurancePaymentConfiguration> previous = configurationRepo
                .findByCompanyIdAndPaymentChannelAndActiveTrue(company.getId(), account.getChannel());
        previous.stream().filter(c -> c.getEffectiveTo() == null || !c.getEffectiveTo().isBefore(request.effectiveFrom()))
                .forEach(c -> {
                    if (c.getEffectiveFrom().isBefore(request.effectiveFrom())) {
                        c.setEffectiveTo(request.effectiveFrom().minusDays(1));
                    } else {
                        c.setActive(false);
                    }
                });
        configurationRepo.saveAll(previous);

        InsurancePaymentConfiguration configuration = new InsurancePaymentConfiguration();
        configuration.setCompanyId(company.getId());
        configuration.setPaymentAccountId(account.getId());
        configuration.setPaymentChannel(account.getChannel());
        configuration.setLabel(request.label().trim());
        configuration.setInstructions(request.instructions().trim());
        configuration.setReferenceTemplate(trimToNull(request.referenceTemplate()));
        configuration.setVersion((int) configurationRepo.countByCompanyIdAndPaymentChannel(company.getId(), account.getChannel()) + 1);
        configuration.setEffectiveFrom(request.effectiveFrom());
        configuration.setCreatedBy(userDao.getUserId());
        configuration.setActive(true);
        return paymentView(company, configurationRepo.save(configuration), false);
    }

    @Transactional("pmsDBTransactionManager")
    public void deactivatePaymentConfiguration(long id) {
        InsurancePaymentConfiguration configuration = configurationRepo.findById(id)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
        configuration.setActive(false);
        configuration.setEffectiveTo(LocalDate.now());
        configurationRepo.save(configuration);
    }

    private PaymentConfigurationView paymentView(InsuranceCompany company, InsurancePaymentConfiguration c, boolean safeOnly) {
        AccountDTO account = accountService.getAccount(c.getPaymentAccountId());
        var details = safeOnly ? account.properties().stream()
                .filter(p -> p.displayField() && p.value() != null && !p.value().isBlank()).toList() : account.properties();
        return new PaymentConfigurationView(c.getId(), company.getCode(), company.getName(), account.id(), account.name(),
                c.getPaymentChannel(), c.getLabel(), c.getInstructions(), c.getReferenceTemplate(), c.getVersion(),
                c.getEffectiveFrom(), c.getEffectiveTo(), c.isActive(), account.verified(), details);
    }

    private InsuranceCompany company(String code) {
        return companyRepo.findByCodeIgnoreCaseAndActiveTrue(code)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
    }

    private CompanyView view(InsuranceCompany c) {
        return new CompanyView(c.getId(), c.getCode(), c.getName(), c.getLogoUrl(), c.getDescription());
    }

    private CompanyEmailConfigurationView emailView(InsuranceCompany c) {
        return new CompanyEmailConfigurationView(c.getId(), c.getCode(), c.getName(), c.getQuotationEmail(),
                c.getClaimsEmail(), c.getRenewalsEmail(), c.getQuotationEmail() != null && !c.getQuotationEmail().isBlank());
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
