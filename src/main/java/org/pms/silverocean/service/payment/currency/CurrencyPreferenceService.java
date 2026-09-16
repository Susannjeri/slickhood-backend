package org.pms.silverocean.service.payment.currency;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.UserCurrencyPreferenceRepo;
import org.pms.silverocean.database.pms.entities.UserCurrencyPreference;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.payment.money.MonetaryPolicy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.pms.silverocean.service.payment.currency.CurrencyPreferenceModels.*;

@Service
@RequiredArgsConstructor
public class CurrencyPreferenceService {
    private final UserCurrencyPreferenceRepo repo;
    private final UserDao userDao;
    private final AuditLogService auditLogService;

    @Transactional(value = "pmsDBTransactionManager", readOnly = true)
    public View current() {
        Users user = requireUser();
        return repo.findByUserIdAndActiveTrue(user.getId()).map(this::view)
                .orElseGet(() -> new View("KES", List.of("KES"), catalogue(), 0));
    }

    @Transactional("pmsDBTransactionManager")
    public View update(Update update) {
        Users user = requireUser();
        LinkedHashSet<String> enabled = new LinkedHashSet<>();
        update.enabledCurrencies().forEach(code -> enabled.add(MonetaryPolicy.currency(code)));
        if (enabled.size() != update.enabledCurrencies().size())
            throw new IllegalArgumentException("Each enabled currency must appear once");
        String defaultCurrency = MonetaryPolicy.currency(update.defaultCurrency());
        if (!enabled.contains(defaultCurrency))
            throw new IllegalArgumentException("The default currency must also be enabled");

        UserCurrencyPreference preference = repo.findByUserIdAndActiveTrue(user.getId())
                .orElseGet(UserCurrencyPreference::new);
        if (preference.getId() != null && preference.getVersion() != update.version())
            throw new OptimisticLockingFailureException("Currency preferences changed in another session");
        preference.setUserId(user.getId());
        preference.setDefaultCurrency(defaultCurrency);
        preference.setEnabledCurrencies(String.join(",", enabled));
        preference.setCreatedBy(user.getId());
        preference.setActive(true);
        preference = repo.save(preference);
        auditLogService.createAuditLog(preference, "UPDATE_CURRENCY_PREFERENCE");
        return view(preference);
    }

    public List<CurrencyOption> catalogue() {
        return Currency.getAvailableCurrencies().stream()
                .filter(currency -> currency.getDefaultFractionDigits() == MonetaryPolicy.SCALE)
                .sorted(Comparator.comparing(Currency::getCurrencyCode))
                .map(currency -> new CurrencyOption(currency.getCurrencyCode(),
                        currency.getDisplayName(Locale.ENGLISH), currency.getSymbol(Locale.ENGLISH),
                        currency.getDefaultFractionDigits()))
                .toList();
    }

    private View view(UserCurrencyPreference preference) {
        List<String> enabled = Arrays.stream(preference.getEnabledCurrencies().split(","))
                .filter(value -> !value.isBlank()).toList();
        return new View(preference.getDefaultCurrency(), enabled, catalogue(), preference.getVersion());
    }

    private Users requireUser() {
        Users user = userDao.getUserObject();
        if (user == null || !user.isActive()) throw new IllegalStateException("Authenticated user was not found");
        return user;
    }
}
