package org.pms.silverocean.service.estate;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.EstateServiceChargeRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.architecture.events.DomainEventHandler;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class ServiceChargeReminderHandler implements DomainEventHandler {
    private final ObjectMapper objectMapper;
    private final EstateServiceChargeRepo charges;
    private final InvoiceDao invoices;
    private final UnitRepo units;
    private final UserDao users;
    private final I18NService i18n;
    private final NotificationService notifications;

    @Override public String eventType() { return ServiceChargeReminderEvent.TYPE; }

    @Override
    public void handle(DomainEventOutbox event) throws Exception {
        ServiceChargeReminderEvent requested = objectMapper.readValue(event.getPayload(), ServiceChargeReminderEvent.class);
        var charge = charges.findById(requested.chargeId()).filter(item -> item.isActive()).orElse(null);
        if (charge == null) return;
        var invoice = invoices.getInvoiceById(charge.getInvoiceId()).filter(item -> item.isActive() && !item.isPaid()).orElse(null);
        if (invoice == null) return;
        var homeowner = users.findById(charge.getHomeownerUserId()).filter(item -> item.isActive()).orElse(null);
        if (homeowner == null || homeowner.getEmail() == null || homeowner.getEmail().isBlank()) return;
        var unit = units.findById(charge.getUnitId()).orElse(null);
        String unitRef = unit == null ? Long.toString(charge.getUnitId()) : unit.getRef();
        NotificationType type = requested.phase() == ServiceChargeReminderEvent.Phase.OVERDUE
                ? NotificationType.SERVICE_CHARGE_OVERDUE_EMAIL : NotificationType.SERVICE_CHARGE_REMINDER_EMAIL;
        String amount = BigDecimal.valueOf(invoice.getPendingAmount()).setScale(2, RoundingMode.HALF_UP).toPlainString();
        String body = String.format(i18n.getLocalizedMessage(type.getBody()), homeowner.getFullName(), amount,
                invoice.getCurrency(), unitRef, charge.getDueDate(), invoice.getRef());
        notifications.queueNotification(new NotificationDTO(body, homeowner.getEmail(), type));
    }
}
