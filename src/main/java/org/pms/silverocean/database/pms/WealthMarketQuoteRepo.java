package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.WealthMarketQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WealthMarketQuoteRepo extends JpaRepository<WealthMarketQuote,Long> {
    Optional<WealthMarketQuote> findByExchangeCodeAndInstrumentSymbolAndActiveTrue(String exchangeCode,String instrumentSymbol);
}
