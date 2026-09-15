package org.pms.silverocean.service.notification.sms;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.SMSRepo;
import org.pms.silverocean.database.pms.entities.SMS;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderReceiptTest {
    SMSRepo repo=mock(SMSRepo.class);SMSDao dao=new SMSDao(repo);
    SMS receipt(String provider){SMS s=new SMS();s.setChannel(provider);s.setNotificationId(7L);s.setActive(true);return s;}
    @Test void textSmsSuccessCannotRegressToFailure(){SMS s=receipt("TEXTSMS");when(repo.lockProviderMessage("id","TEXTSMS")).thenReturn(Set.of(s));assertEquals(7L,dao.recordProviderReceipt("TEXTSMS","id","200","DeliveredToTerminal","1",null).orElseThrow());dao.recordProviderReceipt("TEXTSMS","id","0","Max Retries fetching DLR","0",null);assertEquals("DeliveredToTerminal",s.getDescription());verify(repo,times(1)).save(s);}
    @Test void africaSuccessCannotRegressAndFailedCanRecover(){SMS s=receipt("Africastalking");when(repo.lockProviderMessage("id","Africastalking")).thenReturn(Set.of(s));assertTrue(dao.recordProviderReceipt("Africastalking","id","Failed","Failure","1","127.0.0.1").isEmpty());assertEquals(7L,dao.recordProviderReceipt("Africastalking","id","Success","Delivered","1","127.0.0.1").orElseThrow());dao.recordProviderReceipt("Africastalking","id","Failed","Failure","1",null);assertEquals("Success",s.getStatus());verify(repo,times(2)).save(s);}
    @Test void unknownOrAmbiguousReceiptsDoNotConfirmDelivery(){assertTrue(dao.recordProviderReceipt("TEXTSMS","missing","200","DeliveredToTerminal","1",null).isEmpty());when(repo.lockProviderMessage("id","TEXTSMS")).thenReturn(Set.of(receipt("TEXTSMS"),receipt("TEXTSMS")));assertTrue(dao.recordProviderReceipt("TEXTSMS","id","200","DeliveredToTerminal","1",null).isEmpty());verify(repo,never()).save(any());}
    @Test void malformedOrWrongProviderReceiptsAreIgnored(){assertTrue(dao.recordProviderReceipt("WHATS_APP","id","200","DeliveredToTerminal","1",null).isEmpty());assertTrue(dao.recordProviderReceipt("TEXTSMS","","200","DeliveredToTerminal","1",null).isEmpty());verifyNoInteractions(repo);}
}
