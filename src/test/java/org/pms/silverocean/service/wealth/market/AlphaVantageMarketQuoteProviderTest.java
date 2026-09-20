package org.pms.silverocean.service.wealth.market;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AlphaVantageMarketQuoteProviderTest {
    @Test void rejectsUnsupportedExchangeOrCurrencyWithoutCallingTheProvider() {
        RestClient.Builder builder=RestClient.builder();
        MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        var provider=new AlphaVantageMarketQuoteProvider(builder,"https://quotes.example.test","secret",true,"EOD","NASDAQ=USD");

        assertThat(provider.quote("NASDAQ","AAPL","KES")).isEmpty();
        assertThat(provider.quote("DIGITAL","BTC","USD")).isEmpty();
        server.verify();
    }

    @Test void returnsOnlyTheConfiguredMarketCurrency() {
        RestClient.Builder builder=RestClient.builder();
        MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        server.expect(once(),requestTo(containsString("function=GLOBAL_QUOTE")))
                .andExpect(requestTo(containsString("symbol=AAPL")))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"Global Quote":{"05. price":"250.25","07. latest trading day":"2026-09-18","08. previous close":"248.00","09. change":"2.25","10. change percent":"0.9073%"}}
                        """,APPLICATION_JSON));
        var provider=new AlphaVantageMarketQuoteProvider(builder,"https://quotes.example.test","secret",true,"EOD","NASDAQ=USD");

        var quote=provider.quote("nasdaq","aapl","usd");

        assertThat(quote).isPresent();
        assertThat(quote.orElseThrow().currency()).isEqualTo("USD");
        assertThat(quote.orElseThrow().price()).isEqualByComparingTo("250.25");
        server.verify();
    }
}
