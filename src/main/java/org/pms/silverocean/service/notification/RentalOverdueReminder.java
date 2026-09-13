package org.pms.silverocean.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.DomainEventOutboxRepo;
import org.pms.silverocean.database.pms.PMSInvoiceRepo;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.architecture.events.DomainEventHandler;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.payment.latefee.LateFeePolicyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Explicit rollout switch prevents an unreviewed historical debt notification blast. */
@Service @RequiredArgsConstructor
public class RentalOverdueReminder implements DomainEventHandler {
    public static final String TYPE = "RENTAL_OVERDUE_REMINDER";
    private final PMSInvoiceRepo invoices;
    private final DomainEventOutboxRepo outbox;
    private final DomainEventOutboxPublisher publisher;
    private final UserDao users;
    private final NotificationService notifications;
    private final I18NService i18n;
    private final ObjectMapper mapper;
    private final LateFeePolicyService lateFees;
    @Value("${pms.receivables.reminder.enabled:${pms.rental.reminder.enabled:false}}") private boolean enabled;
    @Value("${pms.rental.reminder.repeat-days:7}") private int repeatDays = 7;
    @Value("${pms.rental.reminder.max-occurrences:12}") private int maxOccurrences = 12;

    @Scheduled(cron = "${pms.rental.reminder.cron:0 15 8 * * *}", zone = "Africa/Nairobi")
    public void schedule() {
        if (!enabled) return;
        LocalDate today = LocalDate.now(PMSUtils.getZoneId());
        long cursor = 0;
        for (int batch = 0; batch < 100; batch++) {
            var candidates = invoices.findReceivableReminderCandidates(today, List.of("RENTAL", "SALE"), cursor,
                    PageRequest.of(0, 100));
            for (var invoice : candidates) {
                long occurrence = (ChronoUnit.DAYS.between(invoice.getDueDate(), today) - 1) / Math.max(1, repeatDays);
                if (occurrence < Math.max(1, maxOccurrences)) {
                    String key = TYPE + ":" + invoice.getId() + ":" + invoice.getDueDate() + ":" + occurrence;
                    publisher.publish(TYPE, "INVOICE", invoice.getId().toString(), key,
                            new Reminder(invoice.getId(), invoice.getDueDate().toString()));
                }
                cursor = invoice.getId();
            }
            if (candidates.size() < 100) return;
        }
    }

    @Override public String eventType() { return TYPE; }

    @Override @Transactional("pmsDBTransactionManager")
    public void handle(DomainEventOutbox supplied) throws Exception {
        var event = outbox.lockForNotification(supplied.getId()).orElseThrow();
        if ("PROCESSED".equals(event.getStatus())) return;
        Reminder reminder = mapper.readValue(event.getPayload(), Reminder.class);
        var invoice = invoices.findByIdForUpdate(reminder.invoiceId()).orElse(null);
        LocalDate today = LocalDate.now(PMSUtils.getZoneId());
        if (invoice != null && invoice.isActive() && !invoice.isPaid() && invoice.getPendingAmount() > 0
                && ("RENTAL".equals(invoice.getBillingType()) || "SALE".equals(invoice.getBillingType()))
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(today) && invoice.getDueDate().toString().equals(reminder.dueDate())) {
            lateFees.assess(invoice.getId());
            var tenant = users.findById(invoice.getBilledUserId()).filter(user -> user.isActive()).orElse(null);
            if (tenant != null && tenant.getEmail() != null && !tenant.getEmail().isBlank()) {
                var type = "SALE".equals(invoice.getBillingType())
                        ? NotificationType.SALE_PAYMENT_OVERDUE_EMAIL
                        : NotificationType.RENT_PAYMENT_REMINDER_EMAIL;
                String amount = BigDecimal.valueOf(invoice.getPendingAmount()).setScale(2, RoundingMode.HALF_UP).toPlainString();
                String body = String.format(i18n.getLocalizedMessage(type.getBody()), escape(tenant.getFullName()),
                        escape(invoice.getRef()), amount, escape(invoice.getCurrency()), invoice.getDueDate());
                String label = "SALE".equals(invoice.getBillingType()) ? "property sale" : "rent";
                notifications.queueEmailAndInApp(tenant.getEmail(), type, body,
                        invoice.getBillingType() + "_PAYMENT_OVERDUE",
                        "Your " + label + " invoice " + invoice.getRef() + " has " + invoice.getCurrency() + " "
                                + amount + " outstanding. It was due on " + invoice.getDueDate()
                                + ". Open /dashboard/invoices to review or pay it.");
            }
            String currentAmount = BigDecimal.valueOf(invoice.getPendingAmount())
                    .setScale(2, RoundingMode.HALF_UP).toPlainString();
            notifyPayee(invoice, currentAmount);
        }
        // Queue creation and acknowledgement commit together, avoiding duplicates on worker restart.
        event.setStatus("PROCESSED"); event.setProcessedAt(LocalDateTime.now()); event.setProcessingStartedAt(null);
        outbox.save(event);
    }
    private void notifyPayee(org.pms.silverocean.database.pms.entities.PMSInvoice invoice, String amount) {
        if (invoice.getPayToUserId() <= 0 || invoice.getPayToUserId() == invoice.getBilledUserId()) return;
        users.findById(invoice.getPayToUserId()).filter(user -> user.isActive())
                .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
                .ifPresent(payee -> {
                    var type = NotificationType.RECEIVABLE_OVERDUE_EMAIL;
                    String body = String.format(i18n.getLocalizedMessage(type.getBody()), escape(invoice.getRef()), amount,
                            escape(invoice.getCurrency()), invoice.getDueDate());
                    notifications.queueEmailAndInApp(payee.getEmail(), type, body,
                            invoice.getBillingType() + "_RECEIVABLE_OVERDUE",
                            "Customer invoice " + invoice.getRef() + " has " + invoice.getCurrency() + " " + amount
                                    + " outstanding since " + invoice.getDueDate()
                                    + ". Open /dashboard/invoices to review it. Any breach or termination notice requires your separate review.");
                });
    }
    private static String escape(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value); }
    public record Reminder(long invoiceId, String dueDate) {}
}
