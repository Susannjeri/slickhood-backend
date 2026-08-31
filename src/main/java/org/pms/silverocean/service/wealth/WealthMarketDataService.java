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

import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.pms.silverocean.service.wealth.WealthModels.MarketQuoteView;

@Service @RequiredArgsConstructor
public class WealthMarketDataService {
    private final WealthAssetRepo assetRepo;private final WealthMarketQuoteRepo quoteRepo;private final WealthValuationRepo valuationRepo;
    private final UserDao userDao;private final MarketQuoteProvider provider;
    @Transactional public MarketQuoteView refresh(long assetId){
        WealthAsset asset=assetRepo.findByIdAndOwnerUserIdAndActiveTrue(assetId,userDao.getUserId()).orElseThrow(()->new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
        if(!"MARKET".equals(asset.getPricingMode())||asset.getInstrumentSymbol()==null||asset.getQuantity()==null)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        Optional<MarketQuoteProvider.Quote> fetched=provider.quote(asset.getExchangeCode(),asset.getInstrumentSymbol(),asset.getCurrency());
        WealthMarketQuote quote;
        if(fetched.isPresent()){
            MarketQuoteProvider.Quote q=fetched.get();quote=quoteRepo.findByExchangeCodeAndInstrumentSymbolAndActiveTrue(asset.getExchangeCode(),asset.getInstrumentSymbol()).orElseGet(WealthMarketQuote::new);
            quote.setExchangeCode(asset.getExchangeCode());quote.setInstrumentSymbol(asset.getInstrumentSymbol());quote.setCurrency(q.currency());quote.setPrice(q.price());quote.setPreviousClose(q.previousClose());quote.setChangeAmount(q.change());quote.setChangePercent(q.changePercent());quote.setProvider(q.provider());quote.setQuoteAsOf(q.asOf());quote.setFreshness(q.freshness());quote.setActive(true);quote.setCreatedBy(asset.getOwnerUserId());quote=quoteRepo.save(quote);
            asset.setMarketPrice(q.price());asset.setCurrentValue(q.price().multiply(asset.getQuantity()).setScale(2,RoundingMode.HALF_UP));asset.setValuationDate(q.asOf().toLocalDate());asset.setQuoteProvider(q.provider());asset.setQuoteStatus(q.freshness());asset.setQuoteAsOf(q.asOf());assetRepo.save(asset);
            if(!valuationRepo.existsByAssetIdAndSourceAndValuationDateAndActiveTrue(asset.getId(),"MARKET_QUOTE",asset.getValuationDate())){WealthValuation v=new WealthValuation();v.setAssetId(asset.getId());v.setAmount(asset.getCurrentValue());v.setValuationDate(asset.getValuationDate());v.setSource("MARKET_QUOTE");v.setNotes("Automated market valuation");v.setCreatedBy(asset.getOwnerUserId());v.setActive(true);valuationRepo.save(v);}
        }else{
            quote=quoteRepo.findByExchangeCodeAndInstrumentSymbolAndActiveTrue(asset.getExchangeCode(),asset.getInstrumentSymbol()).orElse(null);asset.setQuoteStatus(quote==null?(provider.available()?"UNAVAILABLE":"NOT_CONFIGURED"):"STALE");assetRepo.save(asset);
            if(quote==null)return view(asset,null);
        }
        return view(asset,quote);
    }
    private MarketQuoteView view(WealthAsset a,WealthMarketQuote q){return new MarketQuoteView(a.getId(),a.getInstrumentSymbol(),a.getExchangeCode(),a.getCurrency(),q==null?a.getMarketPrice():q.getPrice(),a.getQuantity(),a.getCurrentValue(),q==null?null:q.getChangeAmount(),q==null?null:q.getChangePercent(),q==null?a.getQuoteProvider():q.getProvider(),a.getQuoteStatus(),a.getQuoteAsOf()==null?null:a.getQuoteAsOf().toString());}
}
