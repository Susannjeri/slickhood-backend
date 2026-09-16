package org.pms.silverocean.service.payment.currency;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.UserCurrencyPreferenceRepo;
import org.pms.silverocean.database.pms.entities.UserCurrencyPreference;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CurrencyPreferenceServiceTest {
    private final UserCurrencyPreferenceRepo repo = mock(UserCurrencyPreferenceRepo.class);
    private final UserDao users = mock(UserDao.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final CurrencyPreferenceService service = new CurrencyPreferenceService(repo, users, audit);

    @Test void newUsersStartWithKesAndReceiveAnIsoCatalogue() {
        when(users.getUserObject()).thenReturn(user());
        when(repo.findByUserIdAndActiveTrue(7L)).thenReturn(Optional.empty());

        var view = service.current();

        assertThat(view.defaultCurrency()).isEqualTo("KES");
        assertThat(view.enabledCurrencies()).containsExactly("KES");
        assertThat(view.availableCurrencies()).extracting(CurrencyPreferenceModels.CurrencyOption::code)
                .contains("KES", "USD", "EUR", "GBP", "ZAR");
        assertThat(view.availableCurrencies()).allMatch(option -> option.fractionDigits() == 2);
    }

    @Test void savesMultipleEnabledCurrenciesAndAnExplicitDefault() {
        when(users.getUserObject()).thenReturn(user());
        when(repo.findByUserIdAndActiveTrue(7L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var view = service.update(new CurrencyPreferenceModels.Update("usd", List.of("KES", "usd", "EUR"), 0));

        assertThat(view.defaultCurrency()).isEqualTo("USD");
        assertThat(view.enabledCurrencies()).containsExactly("KES", "USD", "EUR");
        verify(audit).createAuditLog(any(UserCurrencyPreference.class), eq("UPDATE_CURRENCY_PREFERENCE"));
    }

    @Test void defaultMustBeEnabledAndCurrenciesMustBeUniqueAndLedgerCompatible() {
        when(users.getUserObject()).thenReturn(user());
        when(repo.findByUserIdAndActiveTrue(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new CurrencyPreferenceModels.Update("USD", List.of("KES"), 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("default currency");
        assertThatThrownBy(() -> service.update(new CurrencyPreferenceModels.Update("KES", List.of("KES", "kes"), 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("appear once");
        assertThatThrownBy(() -> service.update(new CurrencyPreferenceModels.Update("JPY", List.of("JPY"), 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("supported ISO");
    }

    @Test void rejectsAStaleEdit() {
        UserCurrencyPreference stored = new UserCurrencyPreference();
        stored.setId(2L); stored.setUserId(7L); stored.setVersion(4);
        when(users.getUserObject()).thenReturn(user());
        when(repo.findByUserIdAndActiveTrue(7L)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.update(new CurrencyPreferenceModels.Update("KES", List.of("KES"), 3)))
                .isInstanceOf(OptimisticLockingFailureException.class);
        verify(repo, never()).save(any());
    }

    private static Users user() {
        Users user = new Users(); user.setId(7L); user.setActive(true); return user;
    }
}
