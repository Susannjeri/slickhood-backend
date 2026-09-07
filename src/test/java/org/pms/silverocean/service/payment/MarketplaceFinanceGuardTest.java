package org.pms.silverocean.service.payment;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.pms.silverocean.service.PMSCustomException;
class MarketplaceFinanceGuardTest {
    private static final BigDecimal TOTAL=new BigDecimal("100");
    private static MarketplaceFinanceGuard.Entry entry(String status,String amount,String ref){
        return new MarketplaceFinanceGuard.Entry(status,new BigDecimal(amount),ref);
    }
    @Test void exactConfirmedReplayIsIdempotent(){
        assertTrue(MarketplaceFinanceGuard.validate(TOTAL,true,true,false,entry("CONFIRMED","100","REF"),entry("CONFIRMED","100.00","REF"),entry("NOT_REQUIRED","0",null)));
    }
    @Test void confirmedRecordCannotBeOverwritten(){
        assertThrows(PMSCustomException.class,()->MarketplaceFinanceGuard.validate(TOTAL,true,true,false,entry("FAILED","100","REF"),entry("CONFIRMED","100","REF"),entry("NOT_REQUIRED","0",null)));
    }
    @Test void refundCannotExceedRemainderAfterSettlement(){
        assertThrows(PMSCustomException.class,()->MarketplaceFinanceGuard.validate(TOTAL,true,true,true,entry("CONFIRMED","30","R"),entry("REQUESTED","30",null),entry("CONFIRMED","80","S")));
    }
    @Test void unpaidLegacyCompletionCannotBeSettled(){
        assertThrows(PMSCustomException.class,()->MarketplaceFinanceGuard.validate(TOTAL,false,true,false,entry("CONFIRMED","100","S"),entry("PENDING","0",null),entry("NOT_REQUIRED","0",null)));
    }
    @Test void incompleteJobCannotBeSettled(){
        assertThrows(PMSCustomException.class,()->MarketplaceFinanceGuard.validate(TOTAL,true,false,false,entry("CONFIRMED","100","S"),entry("PENDING","0",null),entry("NOT_REQUIRED","0",null)));
    }
    @Test void confirmedDecisionRequiresEvidenceReference(){
        assertThrows(PMSCustomException.class,()->MarketplaceFinanceGuard.validate(TOTAL,true,true,true,entry("CONFIRMED","20",""),entry("REQUESTED","20",null),entry("PENDING","0",null)));
    }
    @Test void validPartialRefundCanBeRecorded(){
        assertFalse(MarketplaceFinanceGuard.validate(TOTAL,true,true,true,entry("CONFIRMED","20","R"),entry("REQUESTED","20",null),entry("CONFIRMED","80","S")));
    }
}
