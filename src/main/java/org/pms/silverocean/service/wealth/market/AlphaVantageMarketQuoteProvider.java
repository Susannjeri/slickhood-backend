package org.pms.silverocean.service.wealth.market;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service @Slf4j
public class AlphaVantageMarketQuoteProvider implements MarketQuoteProvider {
    private final RestClient client; private final String apiKey; private final boolean enabled; private final String freshness;
    private final Map<String,String> supportedMarkets;
    public AlphaVantageMarketQuoteProvider(RestClient.Builder builder,
            @Value("${wealth.market.alpha-vantage.base-url:https://www.alphavantage.co}") String baseUrl,
            @Value("${wealth.market.alpha-vantage.api-key:}") String apiKey,
            @Value("${wealth.market.enabled:false}") boolean enabled,
            @Value("${wealth.market.alpha-vantage.freshness:EOD}") String freshness,
            @Value("${wealth.market.alpha-vantage.supported-markets:NASDAQ=USD,NYSE=USD,NYSEARCA=USD,AMEX=USD}") String supportedMarkets) {
        this.client=builder.baseUrl(baseUrl).build();this.apiKey=apiKey;this.enabled=enabled;this.freshness=freshness;
        this.supportedMarkets=parseMarkets(supportedMarkets);
    }
    public boolean available(){return enabled&&apiKey!=null&&!apiKey.isBlank();}
    public boolean supports(String exchange,String currency){String e=normalize(exchange),c=normalize(currency);return e!=null&&c!=null&&c.equals(supportedMarkets.get(e));}
    public Optional<Quote> quote(String exchange,String symbol,String currency){
        if(!available())return Optional.empty();
        String normalizedExchange=normalize(exchange),normalizedCurrency=normalize(currency),normalizedSymbol=normalize(symbol);
        if(normalizedSymbol==null||!supports(normalizedExchange,normalizedCurrency)){
            log.warn("Market quote rejected for unsupported exchange/currency pair: {}/{}",normalizedExchange,normalizedCurrency);
            return Optional.empty();
        }
        try{
            JsonNode response=client.get().uri(uri->uri.path("/query").queryParam("function","GLOBAL_QUOTE").queryParam("symbol",normalizedSymbol).queryParam("apikey",apiKey).build()).retrieve().body(JsonNode.class);
            JsonNode q=response==null?null:response.path("Global Quote");if(q==null||q.isMissingNode()||q.path("05. price").asText().isBlank())return Optional.empty();
            BigDecimal price=decimal(q,"05. price"),previous=decimal(q,"08. previous close"),change=decimal(q,"09. change"),changePct=new BigDecimal(q.path("10. change percent").asText("0").replace("%",""));
            LocalDate day=LocalDate.parse(q.path("07. latest trading day").asText(LocalDate.now(ZoneOffset.UTC).toString()));
            return Optional.of(new Quote(normalizedCurrency,price,previous,change,changePct,"ALPHA_VANTAGE",freshness,day.atStartOfDay(ZoneOffset.UTC)));
        }catch(Exception e){log.warn("Market quote unavailable for {}: {}",normalizedSymbol,e.getClass().getSimpleName());return Optional.empty();}
    }
    private BigDecimal decimal(JsonNode node,String field){String value=node.path(field).asText("0");return value.isBlank()?BigDecimal.ZERO:new BigDecimal(value);}
    private Map<String,String> parseMarkets(String configured){
        if(configured==null||configured.isBlank())return Map.of();
        return Arrays.stream(configured.split(",")).map(String::trim).filter(value->value.contains("="))
                .map(value->value.split("=",2)).filter(pair->pair.length==2&&normalize(pair[0])!=null&&normalize(pair[1])!=null)
                .collect(Collectors.toUnmodifiableMap(pair->normalize(pair[0]),pair->normalize(pair[1]),(first,ignored)->first));
    }
    private String normalize(String value){return value==null||value.isBlank()?null:value.trim().toUpperCase(Locale.ROOT);}
}
