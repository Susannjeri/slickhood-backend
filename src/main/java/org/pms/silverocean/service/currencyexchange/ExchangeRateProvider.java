package org.pms.silverocean.service.currencyexchange;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.entities.ConversionRate;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.threadpooling.PMSThreadPoolExecutorService;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExchangeRateProvider {
    private final RestTemplateService restTemplateService;
    private final ConversionRateDao conversionRateDao;
    private final ConfigService configService;
    private final PMSThreadPoolExecutorService updateCurrencyExecutor;

    private final Map<String, BigDecimal> conversionRates = new ConcurrentHashMap<>();

    private final static String API_SUFFIX = "latest/USD";


    public ExchangeRateProvider(RestTemplateService restTemplateService, ConversionRateDao conversionRateDao,
                                ConfigService configService, ThreadPoolBeans threadPoolBeans) {
        this.restTemplateService = restTemplateService;
        this.conversionRateDao = conversionRateDao;
        this.configService = configService;
        this.updateCurrencyExecutor = threadPoolBeans.cpuExecutorService("CURRENCIES", 1, 1);
    }

    @PostConstruct
    public void init() {
        try {
            loadRatesFromDb(false);
            if (CollectionUtils.isEmpty(conversionRates)) {
                fetchRatesFromApi();
            } else {
                scheduleNextUpdate();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            loadRatesFromDb(true);
            scheduleNextUpdate();
        }
    }

    public BigDecimal getCurrencyRate(String currency) {
        return conversionRates.get(currency);
    }

    private void fetchRatesFromApi() {
        ExchangeRateResponse apiResponse = restTemplateService.sendGetRequest(getApiUrl(), null, ExchangeRateResponse.class);
        if (!CollectionUtils.isEmpty(apiResponse.rates())) {
            updateRates(apiResponse.rates());
        }
    }

    private void updateRates(Map<String, BigDecimal> ratesFromAPI) {
        Map<String, ConversionRate> existingRates = conversionRateDao.findAll()
                .stream()
                .collect(Collectors.toMap(ConversionRate::getCurrency, r -> r));

        Set<ConversionRate> toSave = ratesFromAPI.entrySet().stream().map(entry -> {
            String currency = entry.getKey();
            BigDecimal rate = entry.getValue();

            conversionRates.put(currency, rate);

            ConversionRate entity = existingRates.getOrDefault(currency, new ConversionRate(currency));
            entity.setRate(rate);
            entity.setLastModifiedDate(LocalDateTime.now());
            return entity;
        }).collect(Collectors.toSet());

        conversionRateDao.saveAll(toSave);
        scheduleNextUpdate();
    }

    private void loadRatesFromDb(boolean skipLatestCheck) {
        conversionRateDao.findAll()
                .forEach(conversionRate -> {
                    if (skipLatestCheck || LocalDate.now().equals(conversionRate.getLastModifiedDate().toLocalDate())) {
                        conversionRates.put(conversionRate.getCurrency(), conversionRate.getRate());
                    }
                });
    }

    private void scheduleNextUpdate() {
        LocalDateTime lastUpdateDateTime = LocalDateTime.now();
        LocalDateTime nextMidnight = lastUpdateDateTime.plusDays(1).truncatedTo(ChronoUnit.DAYS);
        updateCurrencyExecutor.schedule(this::fetchRatesFromApi,
                Duration.between(lastUpdateDateTime, nextMidnight).toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private String getApiUrl() {
        String baseUrl = configService.getConfigByName(PMSConfigs.CURRENCY_EXCHANGE_URL).get().stringValue();
        return String.format("%s%s/%s", baseUrl, getApiKey(), API_SUFFIX);
    }

    private String getApiKey() {
        return configService.getConfigByName(PMSConfigs.CURRENCY_EXCHANGE_API_KEY).get().stringValue();
    }
}
