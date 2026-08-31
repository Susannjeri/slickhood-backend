package org.pms.silverocean.service.wealth;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.wealth.market.MarketQuoteProvider;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.util.Optional;

import static org.pms.silverocean.service.wealth.WealthModels.MarketQuoteView;
import static org.pms.silverocean.service.wealth.WealthModels.MarketDataStatus;

@Service @RequiredArgsConstructor
public class WealthMarketDataService {
    private final WealthAssetRepo assetRepo;private final WealthMarketQuoteRepo quoteRepo;private final WealthValuationRepo valuationRepo;
    private final UserDao userDao;private final MarketQuoteProvider provider;
    @Value("${wealth.market.minimum-refresh:PT15M}") private Duration minimumRefresh;
    @Value("${wealth.market.batch-size:50}") private int batchSize;
    private volatile ZonedDateTime lastRunAt;private volatile int lastProcessed;private volatile int lastFailures;private volatile String lastError;
    @Transactional public MarketQuoteView refresh(long assetId){
        WealthAsset asset=assetRepo.findByIdAndOwnerUserIdAndActiveTrue(assetId,userDao.getUserId()).orElseThrow(()->new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
        if(!"MARKET".equals(asset.getPricingMode())||asset.getInstrumentSymbol()==null||asset.getQuantity()==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        return refreshAsset(asset,false);
    }
    private MarketQuoteView refreshAsset(WealthAsset asset,boolean scheduled){
        Optional<WealthMarketQuote> cached=quoteRepo.findByExchangeCodeAndInstrumentSymbolAndActiveTrue(asset.getExchangeCode(),asset.getInstrumentSymbol());
        if(cached.isPresent()&&cached.get().getLastModifiedDate()!=null&&cached.get().getLastModifiedDate().isAfter(java.time.LocalDateTime.now().minus(minimumRefresh)))return view(asset,cached.get());
        Optional<MarketQuoteProvider.Quote> fetched=provider.quote(asset.getExchangeCode(),asset.getInstrumentSymbol(),asset.getCurrency());
        WealthMarketQuote quote;
        if(fetched.isPresent()){
            MarketQuoteProvider.Quote q=fetched.get();quote=cached.orElseGet(WealthMarketQuote::new);
            quote.setExchangeCode(asset.getExchangeCode());quote.setInstrumentSymbol(asset.getInstrumentSymbol());quote.setCurrency(q.currency());quote.setPrice(q.price());quote.setPreviousClose(q.previousClose());quote.setChangeAmount(q.change());quote.setChangePercent(q.changePercent());quote.setProvider(q.provider());quote.setQuoteAsOf(q.asOf());quote.setFreshness(q.freshness());quote.setActive(true);quote.setCreatedBy(asset.getOwnerUserId());quote=quoteRepo.save(quote);
            asset.setMarketPrice(q.price());asset.setCurrentValue(q.price().multiply(asset.getQuantity()).setScale(2,RoundingMode.HALF_UP));asset.setValuationDate(q.asOf().toLocalDate());asset.setQuoteProvider(q.provider());asset.setQuoteStatus(q.freshness());asset.setQuoteAsOf(q.asOf());assetRepo.save(asset);
            if(!valuationRepo.existsByAssetIdAndSourceAndValuationDateAndActiveTrue(asset.getId(),"MARKET_QUOTE",asset.getValuationDate())){WealthValuation v=new WealthValuation();v.setAssetId(asset.getId());v.setAmount(asset.getCurrentValue());v.setValuationDate(asset.getValuationDate());v.setSource("MARKET_QUOTE");v.setNotes("Automated market valuation");v.setCreatedBy(asset.getOwnerUserId());v.setActive(true);valuationRepo.save(v);}
        }else{
            quote=quoteRepo.findByExchangeCodeAndInstrumentSymbolAndActiveTrue(asset.getExchangeCode(),asset.getInstrumentSymbol()).orElse(null);asset.setQuoteStatus(quote==null?(provider.available()?"UNAVAILABLE":"NOT_CONFIGURED"):"STALE");assetRepo.save(asset);
            if(quote==null)return view(asset,null);
        }
        return view(asset,quote);
    }
    @Scheduled(fixedDelayString="${wealth.market.scheduler-delay-ms:900000}")
    public void refreshScheduled(){if(!provider.available())return;lastRunAt=ZonedDateTime.now();lastProcessed=0;lastFailures=0;lastError=null;var page=assetRepo.findAllByPricingModeAndActiveTrue("MARKET",PageRequest.of(0,Math.max(1,Math.min(batchSize,200)),Sort.by(Sort.Direction.ASC,"quoteAsOf")));for(WealthAsset asset:page){try{refreshAsset(asset,true);lastProcessed++;}catch(Exception e){lastFailures++;lastError=e.getClass().getSimpleName();}}}
    public MarketDataStatus status(){return new MarketDataStatus(provider.available(),lastRunAt==null?null:lastRunAt.toString(),lastProcessed,lastFailures,lastError);}
    private MarketQuoteView view(WealthAsset a,WealthMarketQuote q){return new MarketQuoteView(a.getId(),a.getInstrumentSymbol(),a.getExchangeCode(),a.getCurrency(),q==null?a.getMarketPrice():q.getPrice(),a.getQuantity(),a.getCurrentValue(),q==null?null:q.getChangeAmount(),q==null?null:q.getChangePercent(),q==null?a.getQuoteProvider():q.getProvider(),a.getQuoteStatus(),a.getQuoteAsOf()==null?null:a.getQuoteAsOf().toString());}
}
