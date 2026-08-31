package org.pms.silverocean.service.wealth.market;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

@Service @Slf4j
public class AlphaVantageMarketQuoteProvider implements MarketQuoteProvider {
    private final RestClient client; private final String apiKey; private final boolean enabled; private final String freshness;
    public AlphaVantageMarketQuoteProvider(RestClient.Builder builder,
            @Value("${wealth.market.alpha-vantage.base-url:https://www.alphavantage.co}") String baseUrl,
            @Value("${wealth.market.alpha-vantage.api-key:}") String apiKey,
            @Value("${wealth.market.enabled:false}") boolean enabled,
            @Value("${wealth.market.alpha-vantage.freshness:EOD}") String freshness) {
        this.client=builder.baseUrl(baseUrl).build();this.apiKey=apiKey;this.enabled=enabled;this.freshness=freshness;
    }
    public boolean available(){return enabled&&apiKey!=null&&!apiKey.isBlank();}
    public Optional<Quote> quote(String exchange,String symbol,String currency){
        if(!available())return Optional.empty();
        try{
            JsonNode response=client.get().uri(uri->uri.path("/query").queryParam("function","GLOBAL_QUOTE").queryParam("symbol",symbol).queryParam("apikey",apiKey).build()).retrieve().body(JsonNode.class);
            JsonNode q=response==null?null:response.path("Global Quote");if(q==null||q.isMissingNode()||q.path("05. price").asText().isBlank())return Optional.empty();
            BigDecimal price=decimal(q,"05. price"),previous=decimal(q,"08. previous close"),change=decimal(q,"09. change"),changePct=new BigDecimal(q.path("10. change percent").asText("0").replace("%",""));
            LocalDate day=LocalDate.parse(q.path("07. latest trading day").asText(LocalDate.now(ZoneOffset.UTC).toString()));
            return Optional.of(new Quote(currency,price,previous,change,changePct,"ALPHA_VANTAGE",freshness,day.atStartOfDay(ZoneOffset.UTC)));
        }catch(Exception e){log.warn("Market quote unavailable for {}: {}",symbol,e.getClass().getSimpleName());return Optional.empty();}
    }
    private BigDecimal decimal(JsonNode node,String field){String value=node.path(field).asText("0");return value.isBlank()?BigDecimal.ZERO:new BigDecimal(value);}
}
