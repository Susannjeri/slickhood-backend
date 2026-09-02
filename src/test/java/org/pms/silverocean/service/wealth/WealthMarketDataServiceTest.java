package org.pms.silverocean.service.wealth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.wealth.market.MarketQuoteProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WealthMarketDataServiceTest {
    @Mock WealthAssetRepo assets; @Mock WealthMarketQuoteRepo quotes; @Mock WealthValuationRepo valuations;
    @Mock UserDao users; @Mock MarketQuoteProvider provider;
    WealthMarketDataService service; WealthAsset asset;
    @BeforeEach void setup(){service=new WealthMarketDataService(assets,quotes,valuations,users,provider);asset=new WealthAsset();asset.setId(7L);asset.setOwnerUserId(4);asset.setPricingMode("MARKET");asset.setExchangeCode("NASDAQ");asset.setInstrumentSymbol("AAPL");asset.setCurrency("USD");asset.setQuantity(BigDecimal.TEN);asset.setCurrentValue(new BigDecimal("1000"));asset.setValuationDate(LocalDate.of(2026,1,1));asset.setActive(true);when(users.getUserId()).thenReturn(4L);when(assets.findByIdAndOwnerUserIdAndActiveTrue(7,4)).thenReturn(Optional.of(asset));}
    @Test void providerFailurePreservesLastKnownValue(){when(provider.quote("NASDAQ","AAPL","USD")).thenReturn(Optional.empty());when(provider.available()).thenReturn(true);when(quotes.findByExchangeCodeAndInstrumentSymbolAndActiveTrue("NASDAQ","AAPL")).thenReturn(Optional.empty());service.refresh(7);assertThat(asset.getCurrentValue()).isEqualByComparingTo("1000");assertThat(asset.getQuoteStatus()).isEqualTo("UNAVAILABLE");verify(assets).save(asset);}
    @Test void quoteUpdatesValueAndRecordsOneDailyValuation(){var asOf=LocalDate.of(2026,8,30).atStartOfDay(ZoneOffset.UTC);var quote=new MarketQuoteProvider.Quote("USD",new BigDecimal("25"),new BigDecimal("24"),BigDecimal.ONE,new BigDecimal("4.17"),"TEST","EOD",asOf);when(provider.quote("NASDAQ","AAPL","USD")).thenReturn(Optional.of(quote));when(quotes.findByExchangeCodeAndInstrumentSymbolAndActiveTrue("NASDAQ","AAPL")).thenReturn(Optional.empty());when(quotes.save(any())).thenAnswer(i->i.getArgument(0));service.refresh(7);assertThat(asset.getCurrentValue()).isEqualByComparingTo("250.00");assertThat(asset.getQuoteProvider()).isEqualTo("TEST");verify(valuations).save(any(WealthValuation.class));}
    @Test void sharedFreshQuoteIsAppliedToTheOwnersAssetWithoutCallingProvider(){WealthMarketQuote cached=new WealthMarketQuote();cached.setPrice(new BigDecimal("30"));cached.setCurrency("USD");cached.setProvider("TEST");cached.setFreshness("EOD");cached.setQuoteAsOf(LocalDate.of(2026,8,31).atStartOfDay(ZoneOffset.UTC));cached.setLastModifiedDate(LocalDateTime.now());ReflectionTestUtils.setField(service,"minimumRefresh",Duration.ofMinutes(15));when(quotes.findByExchangeCodeAndInstrumentSymbolAndActiveTrue("NASDAQ","AAPL")).thenReturn(Optional.of(cached));service.refresh(7);assertThat(asset.getCurrentValue()).isEqualByComparingTo("300.00");assertThat(asset.getQuoteStatus()).isEqualTo("EOD");verifyNoInteractions(provider);}
}
