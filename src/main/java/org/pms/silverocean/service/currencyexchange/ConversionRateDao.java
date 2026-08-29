package org.pms.silverocean.service.currencyexchange;

import org.pms.silverocean.database.pms.ConversionRateRepo;
import org.pms.silverocean.database.pms.entities.ConversionRate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ConversionRateDao {

    private final ConversionRateRepo conversionRateRepo;

    public ConversionRateDao(ConversionRateRepo conversionRateRepo) {
        this.conversionRateRepo = conversionRateRepo;
    }

    public List<ConversionRate> findAll() {
        return conversionRateRepo.findAll();
    }

    public Optional<ConversionRate> findByCurrency(String currency) {
        return conversionRateRepo.findByCurrency(currency);
    }

    public void saveAll(Set<ConversionRate> conversionRates) {
        conversionRateRepo.saveAll(conversionRates);
    }
}
