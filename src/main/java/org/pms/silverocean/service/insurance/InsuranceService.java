package org.pms.silverocean.service.insurance;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.InsuranceCompanyRepo;
import org.pms.silverocean.database.pms.InsuranceAgencyRepo;
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
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.pms.silverocean.service.insurance.InsuranceModels.*;

@Service
@RequiredArgsConstructor
public class InsuranceService {
    private final InsuranceCompanyRepo companyRepo;
    private final InsuranceAgencyRepo agencyRepo;
    private final InsurancePaymentConfigurationRepo configurationRepo;
    private final AccountDao accountDao;
    private final AccountService accountService;
    private final UserDao userDao;

    public List<CompanyView> companies() {
        return companyRepo.findByActiveTrueOrderByNameAsc().stream().map(this::view).toList();
    }

    public List<CompanyAdminView> adminCompanies() {
        return companyRepo.findAllByOrderByNameAsc().stream().map(this::adminView).toList();
    }

    @Transactional("pmsDBTransactionManager")
    public CompanyAdminView createCompany(CompanyCreateRequest request) {
        if (companyRepo.findByCodeIgnoreCase(request.code().trim()).isPresent()) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        }
        var agency = agencyRepo.findByCodeAndActiveTrue("SILVERWOOD")
                .orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
        InsuranceCompany company = new InsuranceCompany();
        company.setAgencyId(agency.getId());
        company.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        company.setCreatedBy(userDao.getUserId());
        apply(company, request.name(), request.logoUrl(), request.description(), request.quotationEmail(),
                request.claimsEmail(), request.renewalsEmail(), true);
        return adminView(companyRepo.save(company));
    }

    @Transactional("pmsDBTransactionManager")
    public CompanyAdminView updateCompany(String companyCode, CompanyUpdateRequest request) {
        InsuranceCompany company = anyCompany(companyCode);
        apply(company, request.name(), request.logoUrl(), request.description(), request.quotationEmail(),
                request.claimsEmail(), request.renewalsEmail(), request.active());
        return adminView(companyRepo.save(company));
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

    private InsuranceCompany anyCompany(String code) {
        return companyRepo.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
    }

    private CompanyView view(InsuranceCompany c) {
        return new CompanyView(c.getId(), c.getCode(), c.getName(), c.getLogoUrl(), c.getDescription(), c.isActive());
    }

    private CompanyAdminView adminView(InsuranceCompany c) {
        return new CompanyAdminView(c.getId(), c.getCode(), c.getName(), c.getLogoUrl(), c.getDescription(),
                c.getQuotationEmail(), c.getClaimsEmail(), c.getRenewalsEmail(), c.isActive());
    }

    private void apply(InsuranceCompany company, String name, String logoUrl, String description,
                       String quotationEmail, String claimsEmail, String renewalsEmail, boolean active) {
        company.setName(name.trim());
        company.setLogoUrl(validLogoUrl(logoUrl));
        company.setDescription(trimToNull(description));
        company.setQuotationEmail(trimToNull(quotationEmail));
        company.setClaimsEmail(trimToNull(claimsEmail));
        company.setRenewalsEmail(trimToNull(renewalsEmail));
        company.setActive(active);
    }

    private String validLogoUrl(String value) {
        String candidate = trimToNull(value);
        if (candidate == null) return null;
        if (candidate.matches("^/insurance/brands/[A-Za-z0-9._-]+$")) return candidate;
        try {
            URI uri = URI.create(candidate);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) {
                return candidate;
            }
        } catch (IllegalArgumentException ignored) {
            // Converted to the API's standard field-validation response below.
        }
        throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
    }

    private CompanyEmailConfigurationView emailView(InsuranceCompany c) {
        return new CompanyEmailConfigurationView(c.getId(), c.getCode(), c.getName(), c.getQuotationEmail(),
                c.getClaimsEmail(), c.getRenewalsEmail(), c.getQuotationEmail() != null && !c.getQuotationEmail().isBlank());
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
