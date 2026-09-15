package org.pms.silverocean.database;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.pms.silverocean.database.pms.NotificationRepo;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.database.pms.entities.SMS;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real repository/concurrency checks; never accepts a production database URL. */
@EnabledIfEnvironmentVariable(named="SLICKHOOD_NOTIFICATION_MYSQL_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3416/notification_audit")
class NotificationRepositoryMySqlIT {
    static SessionFactory factory;
    @BeforeAll static void open() {
        String url=System.getenv("SLICKHOOD_NOTIFICATION_MYSQL_URL");
        assertEquals("jdbc:mysql://127.0.0.1:3416/notification_audit",url);
        var registry=new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url",url).applySetting("hibernate.connection.username","root")
                .applySetting("hibernate.connection.password","").applySetting("hibernate.hbm2ddl.auto","create-drop")
                .applySetting("hibernate.show_sql","false").build();
        factory=new MetadataSources(registry).addAnnotatedClass(Notification.class).addAnnotatedClass(SMS.class).buildMetadata().buildSessionFactory();
    }
    @AfterAll static void close(){if(factory!=null)factory.close();}
    private <T>T tx(Function<EntityManager,T> work){try(EntityManager em=factory.createEntityManager()){em.getTransaction().begin();try{T result=work.apply(em);em.getTransaction().commit();return result;}catch(RuntimeException error){em.getTransaction().rollback();throw error;}}}
    private long notification(String recipient,String type){return tx(em->{Notification n=new Notification();n.setActive(true);n.setRecipient(recipient);n.setType(type);n.setChannel("SMS");n.setRetry(true);n.setUpdatedOn(LocalDateTime.now().minusHours(1));n.setMessage(new byte[]{1});em.persist(n);em.flush();return n.getId();});}
    private NotificationRepo repo(EntityManager em){return new JpaRepositoryFactory(em).getRepository(NotificationRepo.class);}

    @Test void pairedEmailAndInboxRecordsCountAsOneBusinessAlert(){
        String recipient="paired-buyer@example.test";
        tx(em->{for(String channel:List.of("EMAIL","IN_APP")){Notification n=new Notification();n.setActive(true);n.setRecipient(recipient);n.setType("ORDER_UPDATE");n.setChannel(channel);n.setBusinessEventKey("fixture-paired-key");n.setDeliveryKey("fixture-paired-"+channel);n.setMessage(new byte[]{1});em.persist(n);}em.flush();var repo=repo(em);assertEquals(1,repo.countUnreadForRecipients(List.of(recipient)));var list=repo.findAllForRecipients(PageRequest.of(0,10),List.of(recipient));assertEquals(1,list.getTotalElements());assertEquals("IN_APP",list.getContent().getFirst().getChannel());assertEquals(1,repo.markRecipientRead(list.getContent().getFirst().getId(),List.of(recipient),LocalDateTime.now()));assertEquals(0,repo.countUnreadForRecipients(List.of(recipient)));return null;});
    }
    @Test void adminMonitorUsesOnlyTheLatestActiveReceiptPerNotification(){
        long id=notification("admin-receipts@example.test","PAYMENT_SUCCESS_SMS");
        tx(em->{for(String status:List.of("pending","delivered")){SMS s=new SMS();s.setNotificationId(id);s.setStatus(status);s.setActive(true);em.persist(s);}em.flush();var page=repo(em).findByRecipientContainingOrTypeContainingOrCreatedOnContaining(PageRequest.of(0,10),"admin-receipts@example.test","admin-receipts@example.test","admin-receipts@example.test");assertEquals(1,page.getTotalElements());assertEquals("delivered",page.getContent().getFirst().getStatus());return null;});
    }
    @Test void receiptChecksAreClaimedOnlyOncePerLease(){
        tx(em->{SMS s=new SMS();s.setActive(true);s.setChannel("TEXTSMS");s.setThirdPartyId("durable-receipt-id");s.setNextReceiptCheckAt(LocalDateTime.now().minusMinutes(1));em.persist(s);em.flush();var repo=new JpaRepositoryFactory(em).getRepository(org.pms.silverocean.database.pms.SMSRepo.class);var now=LocalDateTime.now();assertEquals(1,repo.claimReceiptCheck(s.getId(),now,now.plusMinutes(5),12));assertEquals(0,repo.claimReceiptCheck(s.getId(),now,now.plusMinutes(5),12));em.clear();assertEquals(1,em.find(SMS.class,s.getId()).getReceiptCheckAttempts());return null;});
    }

    @Test void listCountAndReadAllExcludeSecurityChallenges(){
        String recipient="isolated-buyer@example.test";
        long visible=notification(recipient,"PAYMENT_RECEIPT_EMAIL");
        var secretIds=List.of("EMAIL_OTP","OTP_SMS","NEW_LOGIN_OTP","SOKO_DELIVERY_RECOVERY_EMAIL","SOKO_DELIVERY_CODE_EMAIL").stream().map(type->notification(recipient,type)).toList();
        tx(em->{var repo=repo(em);var recipients=List.of(recipient);assertEquals(1,repo.countUnreadForRecipients(recipients));assertEquals(visible,repo.findAllForRecipients(PageRequest.of(0,20),recipients).getContent().getFirst().getId());
            for(long id:secretIds)assertEquals(0,repo.markRecipientRead(id,recipients,LocalDateTime.now()));
            assertEquals(0,repo.markRecipientRead(visible,List.of("other@example.test"),LocalDateTime.now()));assertEquals(1,repo.markRecipientRead(visible,recipients,LocalDateTime.now()));assertEquals(0,repo.countUnreadForRecipients(recipients));return null;});
    }

    @Test void concurrentReadAndDeliveryUpdatesPreserveBothStates()throws Exception{
        long id=notification("concurrent@example.test","PAYMENT_SUCCESS_SMS");var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{Future<?> read=pool.submit(()->{ready.countDown();await(start);tx(em->{assertEquals(1,repo(em).markRecipientRead(id,List.of("concurrent@example.test"),LocalDateTime.now()));return null;});});
            Future<?> delivered=pool.submit(()->{ready.countDown();await(start);tx(em->{assertEquals(1,repo(em).confirmDelivered(id,LocalDateTime.now()));return null;});});
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();read.get(20,TimeUnit.SECONDS);delivered.get(20,TimeUnit.SECONDS);
            tx(em->{Notification n=em.find(Notification.class,id);assertNotNull(n.getViewedOn());assertTrue(n.isDelivered());assertFalse(n.isRetry());return null;});
        }finally{start.countDown();pool.shutdownNow();}
    }
    private static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Concurrent test did not start");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}}

    @Test void acceptedProviderReceiptsPreventAutomaticResubmission(){
        long id=notification("accepted@example.test","PAYMENT_SUCCESS_SMS");
        tx(em->{SMS receipt=new SMS();receipt.setActive(true);receipt.setChannel("TEXTSMS");receipt.setNotificationId(id);receipt.setStatus("200");receipt.setThirdPartyId("fixture-provider-receipt");em.persist(receipt);em.flush();var repo=repo(em);assertFalse(repo.findRetryCandidates("SMS",LocalDateTime.now(),5,PageRequest.of(0,100)).contains(id));assertEquals(0,repo.claimRetry(id,LocalDateTime.now(),LocalDateTime.now(),5));return null;});
    }
}
