package org.pms.silverocean.service.sp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.database.pms.entities.ServiceBooking;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.property.UnitDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.ServiceBookingDao;
import org.pms.silverocean.service.sp.enums.BookingStatus;
import org.pms.silverocean.service.sp.enums.ProviderServiceStatus;
import org.pms.silverocean.service.sp.wrappers.CreateBookingRequest;
import org.pms.silverocean.service.sp.wrappers.ServiceBookingDTO;
import org.pms.silverocean.service.sp.wrappers.MarketplaceFinanceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.util.HtmlUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class ServiceBookingService {
    private final ServiceBookingDao bookingDao;
    private final ProviderServiceDao serviceDao;
    private final ProviderProfileDao profileDao;
    private final UserDao userDao;
    private final NotificationService notificationService;
    private final I18NService i18NService;
    private final AccountDao accountDao;
    private final InvoiceDao invoiceDao;
    private final UnitDao unitDao;

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ServiceBookingDTO createBooking(CreateBookingRequest request) {
        long userId = userDao.getUserId();
        var service = serviceDao.findById(request.serviceId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        if (!ProviderServiceStatus.LISTED.name().equals(service.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND);
        }
        ServiceBooking booking = new ServiceBooking();
        booking.setServiceId(request.serviceId());
        if (java.util.Objects.equals(service.getCreatedBy(), userId)) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        booking.setScheduledAt(request.scheduledAt().atZone(ZoneId.of("Africa/Nairobi")).withZoneSameInstant(ZoneId.of("UTC")));
        booking.setNotes(request.notes());
        booking.setQuotedAmount(service.getAmount());
        booking.setCurrency(service.getCurrency());
        booking.setPricingUnit(service.getPricingUnit());
        applyLocation(booking, request, userId);
        booking.setStatus(BookingStatus.PENDING.name());
        booking.setPaymentStatus("NOT_INVOICED");
        booking.setRefundStatus("NOT_REQUIRED");
        booking.setSettlementStatus("PENDING");
        booking.setActive(true);
        booking.setCreatedBy(userId);
        bookingDao.save(booking, Permission.CREATE_SP_BOOKING);
        return toDTO(booking, service);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void confirmBooking(long bookingId) {
        long userId = userDao.getUserId();
        ServiceBooking booking = bookingDao.findByIdAndServiceCreatedByForUpdate(bookingId, userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));
        if (!BookingStatus.PENDING.name().equals(booking.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        ProviderService providerService = serviceDao.findById(booking.getServiceId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        ProviderProfile profile = profileDao.findById(providerService.getProfileId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        if (profile.getPaymentAccountId() == null) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
        }
        var account = accountDao.getAccountByIdAndCreatedBy(profile.getPaymentAccountId(), profile.getUserId());
        if (!account.isVerified() || account.getCategory() != AccountCategory.MERCHANT || account.getChannel() == null) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
        }
        PMSInvoice invoice = createMarketplaceInvoice(booking, providerService, profile.getUserId());
        booking.setPaymentAccountId(account.getId());
        booking.setPaymentChannel(account.getChannel().name());
        booking.setInvoiceRef(invoice.getRef());
        booking.setPaymentStatus("UNPAID");
        booking.setStatus(BookingStatus.AWAITING_PAYMENT.name());
        bookingDao.save(booking, Permission.CONFIRM_SP_BOOKING);
        sendBookingNotification(booking, NotificationType.SP_BOOKING_CONFIRMED_EMAIL);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void startBooking(long bookingId) {
        ServiceBooking booking = bookingDao.findByIdAndServiceCreatedByForUpdate(bookingId, userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));
        if (!BookingStatus.PAID.name().equals(booking.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        booking.setStatus(BookingStatus.IN_PROGRESS.name());
        booking.setStartedAt(ZonedDateTime.now(ZoneId.of("UTC")));
        bookingDao.save(booking, Permission.COMPLETE_SP_BOOKING);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void completeBooking(long bookingId, String evidenceReference) {
        ServiceBooking booking = bookingDao.findByIdAndServiceCreatedByForUpdate(bookingId, userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));
        boolean currentFlow = BookingStatus.IN_PROGRESS.name().equals(booking.getStatus());
        boolean legacyFlow = BookingStatus.CONFIRMED.name().equals(booking.getStatus()) && booking.getInvoiceRef() == null;
        if (!currentFlow && !legacyFlow) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        booking.setStatus(BookingStatus.COMPLETED.name());
        booking.setCompletionEvidenceReference(evidenceReference.trim());
        booking.setCompletedAt(ZonedDateTime.now(ZoneId.of("UTC")));
        bookingDao.save(booking, Permission.COMPLETE_SP_BOOKING);
        sendBookingNotification(booking, NotificationType.SP_BOOKING_COMPLETED_EMAIL);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void cancelBooking(long bookingId, String reason) {
        ServiceBooking booking = bookingDao.findByIdAndCustomerOrProviderForUpdate(bookingId, userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));
        if (BookingStatus.COMPLETED.name().equals(booking.getStatus()) || BookingStatus.CANCELLED.name().equals(booking.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        booking.setStatus(BookingStatus.CANCELLED.name());
        if (reason == null || reason.isBlank()) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        booking.setCancellationReason(reason.trim());
        if ("PAID".equals(booking.getPaymentStatus())) {
            booking.setRefundStatus("REQUESTED");
        }
        bookingDao.save(booking, Permission.CANCEL_SP_BOOKING);
        sendBookingNotification(booking, NotificationType.SP_BOOKING_CANCELLED_EMAIL);
    }

    public Page<ServiceBookingDTO> listBookingsForService(long serviceId, Pageable pageable) {
        ProviderService service = serviceDao.findById(serviceId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        if (!java.util.Objects.equals(service.getCreatedBy(), userDao.getUserId())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND);
        }
        return hydrate(bookingDao.findByServiceId(bounded(pageable), serviceId));
    }

    public Page<ServiceBookingDTO> listMyBookings(Pageable pageable) {
        long userId = userDao.getUserId();
        return hydrate(bookingDao.findByCreatedByOrBookedServiceProvider(bounded(pageable), userId));
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void completePaidInvoice(String invoiceRef, String providerReference) {
        bookingDao.findByInvoiceRefForUpdate(invoiceRef).ifPresent(booking -> {
            if (BookingStatus.AWAITING_PAYMENT.name().equals(booking.getStatus())) {
                booking.setPaymentStatus("PAID");
                booking.setProviderReference(providerReference);
                booking.setStatus(BookingStatus.PAID.name());
                bookingDao.save(booking, "SYSTEM_SP_BOOKING_PAYMENT_CONFIRMED");
            } else if (BookingStatus.CANCELLED.name().equals(booking.getStatus()) && !"PAID".equals(booking.getPaymentStatus())) {
                booking.setPaymentStatus("PAID");
                booking.setProviderReference(providerReference);
                booking.setRefundStatus("REQUESTED");
                bookingDao.save(booking, "SYSTEM_SP_LATE_PAYMENT_REFUND_REQUESTED");
            }
        });
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ServiceBookingDTO updateFinance(long bookingId, MarketplaceFinanceRequest request) {
        if (!userDao.hasRole(PMSRole.FINANCE) && !userDao.hasRole(PMSRole.SUPER_ADMIN)) {
            throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);
        }
        ServiceBooking booking = bookingDao.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));
        String reference = request.providerReference() == null ? null : request.providerReference().trim();
        if (request.status() == MarketplaceFinanceRequest.FinanceStatus.CONFIRMED && (reference == null || reference.isBlank())) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        if (request.type() == MarketplaceFinanceRequest.FinanceType.REFUND) {
            if (!"PAID".equals(booking.getPaymentStatus()) || request.amount().compareTo(booking.getQuotedAmount()) > 0) {
                throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
            }
            booking.setRefundStatus(request.status().name());
            booking.setRefundedAmount(request.amount());
            booking.setRefundReference(reference);
        } else {
            BigDecimal refunded = booking.getRefundedAmount() == null ? BigDecimal.ZERO : booking.getRefundedAmount();
            BigDecimal maximumSettlement = booking.getQuotedAmount().subtract(refunded);
            if (!BookingStatus.COMPLETED.name().equals(booking.getStatus()) || request.amount().compareTo(maximumSettlement) > 0) {
                throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
            }
            booking.setSettlementStatus(request.status().name());
            booking.setSettledAmount(request.amount());
            booking.setSettlementReference(reference);
        }
        bookingDao.save(booking, "SP_BOOKING_FINANCE_" + request.type().name() + "_" + request.status().name());
        return toDTO(booking);
    }

    private void applyLocation(ServiceBooking booking, CreateBookingRequest request, long userId) {
        if (request.unitId() != null) {
            var unit = unitDao.findByIdAndStaffOrOwnerOrTenant(request.unitId(), userId)
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
            if (request.propertyId() != null && !request.propertyId().equals(unit.propertyId())) {
                throw new PMSCustomException(ResponseCode.UNIT_NOT_FOUND);
            }
            booking.setUnitId(request.unitId());
            booking.setPropertyId(unit.propertyId());
        } else if (request.propertyId() != null) {
            throw new PMSCustomException(ResponseCode.UNIT_NOT_FOUND);
        }
    }

    private PMSInvoice createMarketplaceInvoice(ServiceBooking booking, ProviderService service, long providerUserId) {
        Users customer = userDao.findById(booking.getCreatedBy())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
        String serviceName = service.getCategoryName() == null ? "Service booking" : service.getCategoryName();
        String escapedName = HtmlUtils.htmlEscape(serviceName);
        PMSInvoice invoice = new PMSInvoice();
        invoice.setUnitId(booking.getUnitId() == null ? 0 : booking.getUnitId());
        invoice.setPropertyId(booking.getPropertyId() == null ? 0 : booking.getPropertyId());
        invoice.setDescription(("Marketplace service: " + serviceName).getBytes(StandardCharsets.UTF_8));
        invoice.setHtmlDescription(("<tr><td><span>" + escapedName + "</span></td><td class='amount-col'>" + booking.getQuotedAmount() + "</td></tr>").getBytes(StandardCharsets.UTF_8));
        invoice.setAmount(booking.getQuotedAmount().doubleValue());
        invoice.setPendingAmount(booking.getQuotedAmount().doubleValue());
        invoice.setCurrency(booking.getCurrency());
        invoice.setBilledUserId(booking.getCreatedBy());
        invoice.setPayToUserId(providerUserId);
        invoice.setCustomerPhoneNumber(customer.getPhoneNumber());
        invoice.setCustomerEmail(customer.getEmail());
        invoice.setBillingType("SERVICE_MARKETPLACE");
        invoice.setDueDate(booking.getScheduledAt().withZoneSameInstant(ZoneId.of("Africa/Nairobi")).toLocalDate());
        invoice.setActive(true);
        invoiceDao.createInvoice(invoice);
        return invoice;
    }

    private ServiceBookingDTO toDTO(ServiceBooking booking) {
        var service = serviceDao.findById(booking.getServiceId()).orElse(null);
        return toDTO(booking, service);
    }

    private Pageable bounded(Pageable pageable) {
        Sort sort=pageable.getSort().isSorted()?pageable.getSort():Sort.by(Sort.Direction.DESC,"createdOn");
        return PageRequest.of(Math.max(0,pageable.getPageNumber()),Math.min(100,Math.max(1,pageable.getPageSize())),sort);
    }

    private Page<ServiceBookingDTO> hydrate(Page<ServiceBooking> page) {
        if(page.isEmpty())return new PageImpl<>(List.of(),page.getPageable(),page.getTotalElements());
        Map<Long,ProviderService> services=serviceDao.findAllById(page.stream().map(ServiceBooking::getServiceId).distinct().toList()).stream().collect(Collectors.toMap(ProviderService::getId,Function.identity()));
        Map<Long,ProviderProfile> profiles=profileDao.findAllById(services.values().stream().map(ProviderService::getProfileId).distinct().toList()).stream().collect(Collectors.toMap(ProviderProfile::getId,Function.identity()));
        Map<Long,Users> users=userDao.findAllById(page.stream().map(ServiceBooking::getCreatedBy).distinct().toList()).stream().collect(Collectors.toMap(Users::getId,Function.identity()));
        List<ServiceBookingDTO> content=page.stream().map(booking->{ProviderService service=services.get(booking.getServiceId());ProviderProfile profile=service==null?null:profiles.get(service.getProfileId());Users user=users.get(booking.getCreatedBy());return new ServiceBookingDTO(booking,service==null?null:service.getCategoryName(),profile==null?null:profile.getBusinessName(),user==null?null:user.getFullName());}).toList();
        return new PageImpl<>(content,page.getPageable(),page.getTotalElements());
    }

    private ServiceBookingDTO toDTO(ServiceBooking booking, ProviderService service) {
        String serviceName = service != null ? service.getCategoryName() : null;
        String providerName = service != null
                ? profileDao.findById(service.getProfileId()).map(ProviderProfile::getBusinessName).orElse(null)
                : null;
        String bookedByName = userDao.findById(booking.getCreatedBy())
                .map(Users::getFullName).orElse(null);
        return new ServiceBookingDTO(booking, serviceName, providerName, bookedByName);
    }

    private void sendBookingNotification(ServiceBooking booking, NotificationType type) {
        try {
            userDao.findById(booking.getCreatedBy()).ifPresent(user -> {
                String message = String.format(
                        i18NService.getLocalizedMessage(type.getBody()),
                        booking.getServiceId(),
                        booking.getScheduledAt() != null ? booking.getScheduledAt().toString() : "",
                        booking.getCancellationReason() != null ? booking.getCancellationReason() : ""
                );
                notificationService.sendNotification(new NotificationDTO(message, user.getEmail(), type));
            });
        } catch (Exception e) {
            log.warn("Failed to send booking notification for booking {}", booking.getId(), e);
        }
    }
}
