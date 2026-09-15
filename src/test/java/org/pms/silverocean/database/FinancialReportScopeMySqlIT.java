package org.pms.silverocean.database;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.sales.SaleStatus;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the exact repository HQL on isolated MySQL; only this disposable local schema is accepted. */
@EnabledIfEnvironmentVariable(named="SLICKHOOD_REPORT_MYSQL_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3416/report_scope_audit")
class FinancialReportScopeMySqlIT {
    static SessionFactory factory;
    final ZonedDateTime start=ZonedDateTime.now().minusDays(1),end=ZonedDateTime.now().plusDays(1);
    @BeforeAll static void open() {
        assertEquals("jdbc:mysql://127.0.0.1:3416/report_scope_audit",System.getenv("SLICKHOOD_REPORT_MYSQL_URL"));
        var registry=new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url",System.getenv("SLICKHOOD_REPORT_MYSQL_URL"))
                .applySetting("hibernate.connection.username","root").applySetting("hibernate.connection.password","")
                .applySetting("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.hbm2ddl.halt_on_error","true")
                .applySetting("hibernate.hbm2ddl.auto","create-drop").build();
        var metadata=new MetadataSources(registry);
        for(Class<?> type:List.of(Property.class,PropertyManager.class,PMSInvoice.class,PMSPayment.class,
                FinancialJournal.class,FinancialLedgerLine.class,SaleTransaction.class,EstateServiceCharge.class))
            metadata.addAnnotatedClass(type);
        factory=metadata.buildMetadata().buildSessionFactory();
    }
    @AfterAll static void close(){if(factory!=null)factory.close();}
    private <T>T tx(Function<EntityManager,T> work){try(EntityManager em=factory.createEntityManager()){
        em.getTransaction().begin();try{T value=work.apply(em);em.getTransaction().commit();return value;}
        catch(RuntimeException error){em.getTransaction().rollback();throw error;}}}
    private String hql(Class<?> repo,String method){return Arrays.stream(repo.getMethods()).filter(m->m.getName().equals(method))
            .findFirst().orElseThrow().getAnnotation(Query.class).value();}
    private <T>List<T> query(EntityManager em,Class<?> repo,String method,Class<T> entity,boolean privileged,
                           boolean restricted,List<Long> properties,Long assignment,String role,int limit){
        var query=em.createQuery(hql(repo,method),entity).setParameter("userId",41L).setParameter("privileged",privileged)
                .setParameter("restricted",restricted).setParameter("propertyIds",properties)
                .setParameter("start",start).setParameter("end",end).setMaxResults(limit);
        if(query.getParameters().stream().anyMatch(p->"assignmentId".equals(p.getName())))
            query.setParameter("assignmentId",assignment).setParameter("roleName",role);
        return query.getResultList();
    }
    private Property property(EntityManager em,long owner){Property p=new Property();p.setCreatedBy(owner);p.setActive(true);em.persist(p);em.flush();return p;}
    private void assignment(EntityManager em,Property property,long invite,String role,boolean active){
        PropertyManager pm=new PropertyManager();pm.setUserId(41L);pm.setPropertyId(property.getId());pm.setInviteId(invite);
        pm.setRoleName(role);pm.setActive(active);em.persist(pm);}
    private PMSInvoice invoice(EntityManager em,long property,long billed,long payee,boolean platform){
        PMSInvoice i=new PMSInvoice();i.setPropertyId(property);i.setBilledUserId(billed);i.setPayToUserId(payee);
        i.setRef("FIXTURE-"+UUID.randomUUID());i.setAmount(100);i.setPendingAmount(100);i.setActive(true);
        i.setCurrency("KES");if(platform)i.setSubscriptionPlanCode("FIXTURE_PLAN");em.persist(i);em.flush();return i;}
    private PMSPayment payment(EntityManager em,PMSInvoice invoice){PMSPayment p=new PMSPayment();p.setBillReference(invoice.getRef());p.setAmount(100d);em.persist(p);em.flush();return p;}
    private FinancialLedgerLine line(EntityManager em,Long property,long user,PMSInvoice invoice){
        FinancialJournal j=new FinancialJournal("FIXTURE:"+UUID.randomUUID(),"INVOICE_ISSUED",invoice==null?"LEASE":"INVOICE",
                invoice==null?"1":invoice.getId().toString(),null,ZonedDateTime.now());em.persist(j);em.flush();
        FinancialLedgerLine l=new FinancialLedgerLine(j.getId(),1,"ACCOUNTS_RECEIVABLE",user,property,null,"KES",BigDecimal.TEN,BigDecimal.ZERO,"fixture");
        em.persist(l);em.flush();return l;}

    @Test void invoiceAndPaymentIsolationHappensBeforeRowLimitEvenForSamePayer(){tx(em->{
        var a=property(em,99L);var b=property(em,100L);
        var own=invoice(em,a.getId(),41,99,false);var ownPayment=payment(em,own);
        var outside=invoice(em,b.getId(),41,100,false);payment(em,outside);
        var allowed=List.of(a.getId());
        assertEquals(List.of(own),query(em,PMSInvoiceRepo.class,"findForScopedReport",PMSInvoice.class,false,true,allowed,null,"",1));
        assertEquals(List.of(ownPayment),query(em,PMSPaymentRepo.class,"findForScopedReport",PMSPayment.class,false,true,allowed,null,"",1));
        own.setActive(false);em.flush();
        assertTrue(query(em,PMSPaymentRepo.class,"findForScopedReport",PMSPayment.class,false,true,allowed,null,"",20).isEmpty());
        return null;});}

    @Test void selectedStatementDoesNotUseOwnLinesOrAssignmentsFromAnotherWorkspace(){tx(em->{
        var a=property(em,99);var b=property(em,100);assignment(em,a,-9,"PROPERTY_ACCOUNTANT",true);assignment(em,b,-10,"PROPERTY_ACCOUNTANT",true);
        var own=line(em,a.getId(),200,null);line(em,b.getId(),41,null);
        assertEquals(List.of(own),query(em,FinancialLedgerLineRepo.class,"findForScopedStatement",FinancialLedgerLine.class,
                false,true,List.of(a.getId()),-9L,"PROPERTY_ACCOUNTANT",1));
        assertTrue(query(em,FinancialLedgerLineRepo.class,"findForScopedStatement",FinancialLedgerLine.class,
                false,true,List.of(a.getId()),-10L,"PROPERTY_ACCOUNTANT",20).isEmpty());
        assertTrue(query(em,FinancialLedgerLineRepo.class,"findForScopedStatement",FinancialLedgerLine.class,
                false,true,List.of(-1L),-9L,"PROPERTY_ACCOUNTANT",20).isEmpty());return null;});}

    @Test void platformStatementOnlyIncludesVerifiedSubscriptionInvoiceSources(){tx(em->{
        var p=property(em,99);var subscription=invoice(em,0,300,1,true);var rent=invoice(em,p.getId(),301,99,false);
        var platform=line(em,null,300,subscription);line(em,p.getId(),301,rent);line(em,null,1,null);
        var rows=query(em,FinancialLedgerLineRepo.class,"findForScopedStatement",FinancialLedgerLine.class,
                true,false,List.of(-1L),null,"",100);
        assertTrue(rows.contains(platform));assertTrue(rows.stream().allMatch(l->l.getPropertyId()==null));
        assertTrue(rows.stream().noneMatch(l->l.getUserId()==301L||l.getUserId()==1L));return null;});}

    @Test void scopePropertyQueryRequiresOwnerAndActiveMatchingAssignment(){tx(em->{
        var good=property(em,99);var wrongOwner=property(em,100);var wrongRole=property(em,99);var inactive=property(em,99);
        var archived=property(em,99);archived.setActive(false);assignment(em,archived,-9,"PROPERTY_ACCOUNTANT",true);
        assignment(em,good,-9,"PROPERTY_ACCOUNTANT",true);assignment(em,wrongOwner,-9,"PROPERTY_ACCOUNTANT",true);
        assignment(em,wrongRole,-9,"GUARD",true);assignment(em,inactive,-9,"PROPERTY_ACCOUNTANT",false);em.flush();
        var rows=em.createQuery(hql(PropertyRepo.class,"findFinancialReportPropertyIds"),Long.class)
                .setParameter("ownerId",99L).setParameter("userId",41L).setParameter("roleName","PROPERTY_ACCOUNTANT")
                .setParameter("assignmentId",-9L).getResultList();
        assertTrue(rows.contains(good.getId()));assertFalse(rows.contains(wrongOwner.getId()));
        assertFalse(rows.contains(wrongRole.getId()));assertFalse(rows.contains(inactive.getId()));assertFalse(rows.contains(archived.getId()));
        var ownerRows=em.createQuery(hql(PropertyRepo.class,"findFinancialReportPropertyIds"),Long.class)
                .setParameter("ownerId",99L).setParameter("userId",99L).setParameter("roleName","LANDLORD")
                .setParameter("assignmentId",null).getResultList();
        assertTrue(ownerRows.contains(archived.getId()));assertFalse(ownerRows.contains(wrongOwner.getId()));return null;});}

    @Test void assignmentsNeverExpandExistingInvoiceAndPaymentPayerPayeeRights(){tx(em->{
        var p=property(em,99);assignment(em,p,-9,"PROPERTY_ACCOUNTANT",true);
        var other=invoice(em,p.getId(),300,99,false);payment(em,other);
        assertTrue(query(em,PMSInvoiceRepo.class,"findForScopedReport",PMSInvoice.class,false,true,List.of(p.getId()),-9L,"PROPERTY_ACCOUNTANT",20).isEmpty());
        assertTrue(query(em,PMSPaymentRepo.class,"findForScopedReport",PMSPayment.class,false,true,List.of(p.getId()),-9L,"PROPERTY_ACCOUNTANT",20).isEmpty());return null;});}

    @Test void salesAndEstateQueriesCannotFollowAssignmentsOutsideSelectedWorkspace(){tx(em->{
        var a=property(em,99);var b=property(em,100);assignment(em,a,-9,"PROPERTY_ACCOUNTANT",true);assignment(em,b,-10,"PROPERTY_ACCOUNTANT",true);
        List<SaleTransaction> sales=new ArrayList<>();List<EstateServiceCharge> charges=new ArrayList<>();
        for(var p:List.of(a,b)){
            SaleTransaction s=new SaleTransaction();s.setPropertyId(p.getId());s.setSalesAgentUserId(99);s.setBuyerUserId(300L);
            s.setAskingPrice(BigDecimal.TEN);s.setStatus(SaleStatus.OFFERED);s.setActive(true);em.persist(s);sales.add(s);
            EstateServiceCharge c=new EstateServiceCharge();c.setPropertyId(p.getId());c.setHomeownerUserId(300);
            c.setAmount(BigDecimal.TEN);c.setDueDate(LocalDate.now());c.setActive(true);em.persist(c);charges.add(c);
        }em.flush();
        assertEquals(List.of(sales.getFirst()),query(em,SaleTransactionRepo.class,"findForScopedReport",SaleTransaction.class,
                false,true,List.of(a.getId()),-9L,"PROPERTY_ACCOUNTANT",1));
        assertEquals(List.of(charges.getFirst()),query(em,EstateServiceChargeRepo.class,"findForScopedReport",EstateServiceCharge.class,
                false,true,List.of(a.getId()),-9L,"PROPERTY_ACCOUNTANT",1));
        assertTrue(query(em,SaleTransactionRepo.class,"findForScopedReport",SaleTransaction.class,
                true,false,List.of(-1L),null,"",20).isEmpty());
        assertTrue(query(em,EstateServiceChargeRepo.class,"findForScopedReport",EstateServiceCharge.class,
                true,false,List.of(-1L),null,"",20).isEmpty());return null;});}
}
