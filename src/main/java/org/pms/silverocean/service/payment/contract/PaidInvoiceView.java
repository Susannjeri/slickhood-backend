package org.pms.silverocean.service.payment.contract;

public record PaidInvoiceView(long id,String ref,String subscriptionPlanCode,boolean paid,
                              double pendingAmount,long billedUserId) {
    public long getId(){return id;}public String getRef(){return ref;}public String getSubscriptionPlanCode(){return subscriptionPlanCode;}
    public boolean isPaid(){return paid;}public double getPendingAmount(){return pendingAmount;}public long getBilledUserId(){return billedUserId;}
}
