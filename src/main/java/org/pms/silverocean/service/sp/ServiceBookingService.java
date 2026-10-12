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
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.property.UnitDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.ComplaintCaseDao;
import org.pms.silverocean.service.sp.dao.ServiceBookingDao;
import org.pms.silverocean.service.sp.dao.ServiceRatingDao;
import org.pms.silverocean.service.sp.enums.BookingStatus;
import org.pms.silverocean.service.sp.enums.ProviderServiceStatus;
import org.pms.silverocean.service.sp.wrappers.CreateBookingRequest;
import org.pms.silverocean.service.sp.wrappers.ServiceBookingDTO;
import org.pms.silverocean.service.sp.wrappers.MarketplaceFinanceRequest;
import org.pms.silverocean.service.sp.wrappers.MarketplaceFinanceQueueDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
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
    private static final java.time.Duration CUSTOMER_CONFIRMATION_WINDOW = java.time.Duration.ofHours(48);
    private static final int AUTO_FINALIZATION_BATCH_SIZE = 100;
    private static final int AUTO_FINALIZATION_MAX_BATCHES = 10;
    private static final BigDecimal MAX_BOOKING_TOTAL = new BigDecimal("99999999.99");
    private static final java.util.Set<org.pms.silverocean.service.sp.enums.PricingUnit> BOOKABLE_PRICING_UNITS =
            java.util.Set.of(org.pms.silverocean.service.sp.enums.PricingUnit.PER_HOUR,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_DAY,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_WEEK,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_MONTH,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_JOB,
                    org.pms.silverocean.service.sp.enums.PricingUnit.FIXED);
    private final ServiceBookingDao bookingDao;
    private final ProviderServiceDao serviceDao;
    private final ProviderProfileDao profileDao;
    private final UserDao userDao;
    private final NotificationService notificationService;
    private final org.pms.silverocean.service.notification.BusinessNotificationService businessAlerts;
    private final I18NService i18NService;
    private final AccountDao accountDao;
    private final PaymentPlatformFactory paymentPlatformFactory;
    private final InvoiceDao invoiceDao;
    private final UnitDao unitDao;
    private final ServiceRatingDao ratingDao;
    private final ComplaintCaseDao complaintDao;
    private final org.pms.silverocean.service.subscription.SubscriptionEntitlementService subscriptionEntitlements;
    private final ServiceFinanceOperationService financeOperations;

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ServiceBookingDTO createBooking(CreateBookingRequest request) {
        long userId = userDao.getUserId();
        String requestId=org.apache.commons.lang3.StringUtils.trimToNull(request.idempotencyKey());
        if(requestId!=null){
            // Serialise retries per buyer so two concurrent requests cannot both
            // cross the pre-insert idempotency check.
            userDao.findActiveByIdForUpdate(userId).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
            var existing=bookingDao.findByCreatedByAndClientRequestId(userId,requestId);
            if(existing.isPresent()){
                assertIdempotentPayload(existing.get(),request);
                return toDTO(existing.get());
            }
        }
        var service = serviceDao.findByIdForUpdate(request.serviceId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        if (!service.isActive() || !ProviderServiceStatus.LISTED.name().equals(service.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND);
        }
        if (!BOOKABLE_PRICING_UNITS.contains(service.getPricingUnit())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND);
        }
        BigDecimal unitQuantity = normalizedUnitQuantity(service.getPricingUnit(), request.unitQuantity());
        ProviderReadiness readiness = requireProviderReadiness(service);
        ProviderProfile profile = readiness.profile();
        subscriptionEntitlements.requireActiveProductForOwner(profile.getUserId(),
                org.pms.silverocean.service.subscription.enums.SubscriptionProduct.SERVICES);
        if (request.scheduledAt() == null || !request.scheduledAt()
                .atZone(ZoneId.of("Africa/Nairobi")).isAfter(ZonedDateTime.now())) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        ServiceBooking booking = new ServiceBooking();
        booking.setServiceId(request.serviceId());
        booking.setServiceTitleSnapshot(org.apache.commons.lang3.StringUtils.defaultIfBlank(
                service.getTitle(), service.getCategoryName()));
        booking.setServiceCategorySnapshot(service.getCategoryName());
        booking.setServiceDescriptionSnapshot(service.getDescription());
        booking.setProviderBusinessNameSnapshot(profile.getBusinessName());
        booking.setClientRequestId(requestId);
        if (java.util.Objects.equals(service.getCreatedBy(), userId)) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        ZonedDateTime scheduledLocal = request.scheduledAt().atZone(ZoneId.of("Africa/Nairobi"));
        booking.setScheduledAt(scheduledLocal.withZoneSameInstant(ZoneId.of("UTC")));
        booking.setScheduledEndAt(scheduledEndAt(scheduledLocal, service.getPricingUnit(), unitQuantity));
        booking.setNotes(request.notes());
        BigDecimal unitRate = org.pms.silverocean.service.payment.money.MonetaryPolicy.positive(service.getAmount());
        String bookingCurrency = org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(service.getCurrency());
        requireExpectedQuote(request, unitRate, bookingCurrency, service.getPricingUnit());
        BigDecimal quotedAmount = org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(
                unitRate.multiply(unitQuantity));
        if (quotedAmount.compareTo(MAX_BOOKING_TOTAL) > 0) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "The service total is above the supported transaction limit.");
        }
        booking.setPricingUnitRate(unitRate);
        booking.setUnitQuantity(unitQuantity);
        booking.setQuotedAmount(quotedAmount);
        booking.setCurrency(bookingCurrency);
        booking.setPricingUnit(service.getPricingUnit());
        applyLocation(booking, request, userId);
        booking.setStatus(BookingStatus.PENDING.name());
        booking.setPaymentStatus("NOT_INVOICED");
        booking.setRefundStatus("NOT_REQUIRED");
        booking.setSettlementStatus("PENDING");
        booking.setActive(true);
        booking.setCreatedBy(userId);
        try { bookingDao.save(booking, Permission.CREATE_SP_BOOKING); }
        catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        notifyProgress(booking);
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
        if (booking.getScheduledAt() == null || !booking.getScheduledAt().isAfter(utcNow())) {
            booking.setStatus(BookingStatus.CANCELLED.name());
            booking.setCancellationReason("The requested service time passed before the provider confirmed it.");
            bookingDao.save(booking, "SYSTEM_SP_BOOKING_REQUEST_EXPIRED");
            notifyProgress(booking);
            return;
        }
        ProviderService providerService = serviceDao.findById(booking.getServiceId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        ProviderProfile profile = profileDao.findById(providerService.getProfileId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        if (profile.getPaymentAccountId() == null) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
        }
        var account = accountDao.getAccountByIdAndCreatedBy(profile.getPaymentAccountId(), profile.getUserId());
        if (!account.isActive() || !account.isVerified() || account.getCategory() != AccountCategory.MERCHANT
                || account.getChannel() == null || account.getChannel() == org.pms.silverocean.service.payment.wrappers.PaymentChannel.FLUTTER_WAVE
                || !paymentPlatformFactory.isAvailableForNewPayments(account.getChannel())) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
        }
        if (!providerService.isActive() || !java.util.Set.of(ProviderServiceStatus.LISTED.name(), ProviderServiceStatus.HIDDEN.name()).contains(providerService.getStatus())
                || !profile.isActive() || !"ACTIVE".equals(profile.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND);
        }
        PMSInvoice invoice = createMarketplaceInvoice(booking, providerService, profile.getUserId(), account.getId());
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
        notifyProgress(booking);
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
        if (evidenceReference == null || evidenceReference.isBlank() || evidenceReference.length() > 500) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        ZonedDateTime providerCompletedAt = utcNow();
        booking.setStatus(BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name());
        booking.setCompletionEvidenceReference(evidenceReference.trim());
        booking.setCompletedAt(providerCompletedAt);
        booking.setDisputeDeadlineAt(providerCompletedAt.plus(CUSTOMER_CONFIRMATION_WINDOW));
        bookingDao.save(booking, Permission.COMPLETE_SP_BOOKING);
        sendBookingNotification(booking, NotificationType.SP_BOOKING_COMPLETED_EMAIL);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void acceptCompletion(long bookingId) {
        long customerId = userDao.getUserId();
        ServiceBooking booking = bookingDao.findByIdAndCustomerOrProviderForUpdate(bookingId, customerId)
                .filter(candidate -> java.util.Objects.equals(candidate.getCreatedBy(), customerId))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));
        if (!BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name().equals(booking.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        if (complaintDao.existsOpenComplaintForBooking(bookingId)) {
            throw new PMSCustomException(ResponseCode.SP_COMPLAINT_ALREADY_EXISTS);
        }
        finalizeCompletion(booking, utcNow(), true, "CUSTOMER_CONFIRMED_SP_BOOKING_COMPLETION");
    }

    @Scheduled(fixedDelayString = "${services.booking-auto-finalization-scan-ms:300000}")
    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void autoFinalizeExpiredCustomerConfirmations() {
        ZonedDateTime now = utcNow();
        Pageable batch = PageRequest.of(0, AUTO_FINALIZATION_BATCH_SIZE,
                Sort.by(Sort.Direction.ASC, "disputeDeadlineAt", "id"));
        for (int scan = 0; scan < AUTO_FINALIZATION_MAX_BATCHES; scan++) {
            List<Long> bookingIds = bookingDao.findIdsReadyForAutoFinalization(now, batch);
            for (Long bookingId : bookingIds) {
                bookingDao.findByIdForUpdate(bookingId).ifPresent(booking -> {
                    if (!BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name().equals(booking.getStatus())
                            || booking.getDisputeDeadlineAt() == null
                            || booking.getDisputeDeadlineAt().isAfter(now)
                            || complaintDao.existsOpenComplaintForBooking(booking.getId())) {
                        return;
                    }
                    finalizeCompletion(booking, now, false, "SYSTEM_AUTO_FINALIZED_SP_BOOKING_COMPLETION");
                });
            }
            if (bookingIds.size() < AUTO_FINALIZATION_BATCH_SIZE) {
                break;
            }
        }
    }

    @Scheduled(fixedDelayString = "${services.booking-request-expiry-scan-ms:300000}")
    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void expireStaleUnpaidRequests() {
        ZonedDateTime now = utcNow();
        Pageable batch = PageRequest.of(0, AUTO_FINALIZATION_BATCH_SIZE,
                Sort.by(Sort.Direction.ASC, "scheduledAt", "id"));
        for (int scan = 0; scan < AUTO_FINALIZATION_MAX_BATCHES; scan++) {
            List<Long> bookingIds = bookingDao.findIdsReadyForRequestExpiry(now, batch);
            for (Long bookingId : bookingIds) {
                bookingDao.findByIdForUpdate(bookingId).ifPresent(booking -> {
                    if (!java.util.Set.of(BookingStatus.PENDING.name(), BookingStatus.AWAITING_PAYMENT.name())
                            .contains(booking.getStatus()) || booking.getScheduledAt() == null
                            || booking.getScheduledAt().isAfter(now)) {
                        return;
                    }
                    booking.setStatus(BookingStatus.CANCELLED.name());
                    booking.setCancellationReason("The requested service time passed before payment was completed.");
                    applyCancellationFinance(booking);
                    bookingDao.save(booking, "SYSTEM_SP_BOOKING_REQUEST_EXPIRED");
                    notifyProgress(booking);
                });
            }
            if (bookingIds.size() < AUTO_FINALIZATION_BATCH_SIZE) {
                break;
            }
        }
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void cancelBooking(long bookingId, String reason) {
        long actorUserId = userDao.getUserId();
        ServiceBooking booking = bookingDao.findByIdAndCustomerOrProviderForUpdate(bookingId, actorUserId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));
        if (BookingStatus.COMPLETED.name().equals(booking.getStatus())
                || BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name().equals(booking.getStatus())
                || BookingStatus.CANCELLED.name().equals(booking.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        if (reason == null || reason.isBlank()) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        boolean customerCancellation = java.util.Objects.equals(booking.getCreatedBy(), actorUserId);
        if (customerCancellation && !customerMayCancelBeforePayment(booking)) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "A paid or started service cannot be cancelled by the customer. Use the complaint process for help.");
        }
        booking.setStatus(BookingStatus.CANCELLED.name());
        booking.setCancellationReason(reason.trim());
        applyCancellationFinance(booking);
        bookingDao.save(booking, Permission.CANCEL_SP_BOOKING);
        sendBookingNotification(booking, NotificationType.SP_BOOKING_CANCELLED_EMAIL);
    }

    private boolean customerMayCancelBeforePayment(ServiceBooking booking) {
        if (!java.util.Set.of(BookingStatus.PENDING.name(), BookingStatus.AWAITING_PAYMENT.name())
                .contains(booking.getStatus())) {
            return false;
        }
        String paymentStatus = org.apache.commons.lang3.StringUtils.defaultIfBlank(
                booking.getPaymentStatus(), "NOT_INVOICED");
        if (!java.util.Set.of("NOT_INVOICED", "UNPAID").contains(paymentStatus)) {
            return false;
        }
        if (org.apache.commons.lang3.StringUtils.isBlank(booking.getInvoiceRef())) {
            return true;
        }
        return invoiceDao.getInvoiceByRefForUpdate(booking.getInvoiceRef())
                .map(invoice -> {
                    BigDecimal total = invoice.moneyAmount().max(BigDecimal.ZERO);
                    BigDecimal pending = invoice.moneyPendingAmount().max(BigDecimal.ZERO).min(total);
                    return !invoice.isPaid() && total.subtract(pending).signum() == 0;
                })
                // An invoice reference without its invoice cannot be proved unpaid.
                .orElse(false);
    }

    private void applyCancellationFinance(ServiceBooking booking) {
        BigDecimal collectedAmount = voidInvoiceAndReturnCollectedAmount(booking);
        if (collectedAmount.signum() <= 0 && "PAID".equals(booking.getPaymentStatus())) {
            collectedAmount = booking.getQuotedAmount() == null ? BigDecimal.ZERO : booking.getQuotedAmount();
        }
        if (collectedAmount.signum() > 0) {
            BigDecimal quotedAmount = booking.getQuotedAmount() == null ? collectedAmount : booking.getQuotedAmount();
            booking.setPaymentStatus(collectedAmount.compareTo(quotedAmount) >= 0 ? "PAID" : "PARTIALLY_PAID");
            booking.setRefundStatus("REQUESTED");
            booking.setRefundedAmount(collectedAmount.min(quotedAmount));
        }
    }

    private BigDecimal voidInvoiceAndReturnCollectedAmount(ServiceBooking booking) {
        if (org.apache.commons.lang3.StringUtils.isBlank(booking.getInvoiceRef())) return BigDecimal.ZERO;
        PMSInvoice invoice = invoiceDao.getInvoiceByRefForUpdate(booking.getInvoiceRef()).orElse(null);
        if (invoice == null) return BigDecimal.ZERO;
        BigDecimal total = invoice.moneyAmount().max(BigDecimal.ZERO);
        BigDecimal pending = invoice.moneyPendingAmount().max(BigDecimal.ZERO).min(total);
        BigDecimal collected = total.subtract(pending);
        if (invoice.isPaid() || pending.signum() <= 0) {
            collected = total;
        }
        if (!invoice.isPaid()) {
            invoice.setActive(false);
            invoice.setTransactionInProgress(false);
            invoiceDao.saveInvoice(invoice);
        }
        return collected;
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
                ProviderService providerService = serviceDao.findAnyByIdForUpdate(booking.getServiceId()).orElse(null);
                ProviderProfile profile = providerService == null ? null
                        : profileDao.findByIdForUpdate(providerService.getProfileId()).orElse(null);
                Users owner = profile == null ? null
                        : userDao.findActiveByIdForUpdate(profile.getUserId()).orElse(null);
                var account = profile == null || booking.getPaymentAccountId() == null ? null
                        : accountDao.findAnyAccountForUpdate(booking.getPaymentAccountId()).orElse(null);
                if (!canAcceptMarketplacePayment(booking, providerService, profile, owner, account)) {
                    booking.setStatus(BookingStatus.CANCELLED.name());
                    booking.setRefundStatus("REQUESTED");
                    booking.setRefundedAmount(booking.getQuotedAmount());
                    bookingDao.save(booking, "SYSTEM_SP_ENFORCEMENT_PAYMENT_REFUND_REQUESTED");
                    notifyProgress(booking);
                    return;
                }
                booking.setStatus(BookingStatus.PAID.name());
                bookingDao.save(booking, "SYSTEM_SP_BOOKING_PAYMENT_CONFIRMED");
                notifyProgress(booking);
            } else if (BookingStatus.CANCELLED.name().equals(booking.getStatus())
                    && "UNPAID".equals(booking.getPaymentStatus())) {
                booking.setPaymentStatus("PAID");
                booking.setProviderReference(providerReference);
                booking.setRefundStatus("REQUESTED");
                booking.setRefundedAmount(booking.getQuotedAmount());
                bookingDao.save(booking, "SYSTEM_SP_LATE_PAYMENT_REFUND_REQUESTED");
                notifyProgress(booking);
            }
        });
    }

    private boolean canAcceptMarketplacePayment(ServiceBooking booking, ProviderService service,
                                                 ProviderProfile profile,
                                                 Users owner,
                                                 org.pms.silverocean.database.pms.entities.PaymentAccount account) {
        return service != null && service.isActive()
                && java.util.Set.of(ProviderServiceStatus.LISTED.name(), ProviderServiceStatus.HIDDEN.name()).contains(service.getStatus())
                && profile != null && profile.isActive() && "ACTIVE".equals(profile.getStatus())
                && activeVerifiedOwner(owner)
                && supportedMerchantAccount(account, profile.getUserId())
                && java.util.Objects.equals(account.getId(), booking.getPaymentAccountId())
                && java.util.Objects.equals(account.getChannel().name(), booking.getPaymentChannel())
                && booking.getScheduledAt() != null && booking.getScheduledAt().isAfter(utcNow());
    }

    private ProviderReadiness requireProviderReadiness(ProviderService service) {
        ProviderProfile profile = profileDao.findByIdForUpdate(service.getProfileId())
                .filter(candidate -> candidate.isActive() && "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        Users owner = userDao.findActiveByIdForUpdate(profile.getUserId())
                .filter(this::activeVerifiedOwner)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        if (profile.getPaymentAccountId() == null) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND);
        }
        var account = accountDao.findAnyAccountForUpdate(profile.getPaymentAccountId())
                .filter(candidate -> supportedMerchantAccount(candidate, profile.getUserId())
                        && paymentPlatformFactory.isAvailableForNewPayments(candidate.getChannel()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        return new ProviderReadiness(profile, owner, account);
    }

    private boolean activeVerifiedOwner(Users owner) {
        return owner != null && owner.isActive() && owner.isVerified()
                && org.pms.silverocean.service.kyc.AccountStatus.ACTIVE.name().equals(owner.getAccountStatus());
    }

    private boolean supportedMerchantAccount(org.pms.silverocean.database.pms.entities.PaymentAccount account,
                                             long ownerUserId) {
        return account != null && account.isActive() && account.isVerified()
                && account.getCategory() == AccountCategory.MERCHANT
                && account.getChannel() != null
                && account.getChannel() != org.pms.silverocean.service.payment.wrappers.PaymentChannel.FLUTTER_WAVE
                && java.util.Objects.equals(account.getCreatedBy(), ownerUserId);
    }

    private void requireSettlementDestination(ServiceBooking booking) {
        ProviderService listing = serviceDao.findAnyByIdForUpdate(booking.getServiceId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        ProviderProfile profile = profileDao.findByIdForUpdate(listing.getProfileId())
                .filter(candidate -> candidate.isActive() && "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        userDao.findActiveByIdForUpdate(profile.getUserId())
                .filter(this::activeVerifiedOwner)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.KYC_ACCOUNT_RESTRICTED));
        if (booking.getPaymentAccountId() == null) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
        }
        accountDao.findAnyAccountForUpdate(booking.getPaymentAccountId())
                .filter(candidate -> supportedMerchantAccount(candidate, profile.getUserId()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND));
    }

    private record ProviderReadiness(ProviderProfile profile, Users owner,
                                     org.pms.silverocean.database.pms.entities.PaymentAccount account) {}

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ServiceBookingDTO updateFinance(long bookingId, MarketplaceFinanceRequest request) {
        if (!userDao.hasRole(PMSRole.FINANCE)) {
            throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);
        }
        ServiceBooking booking = bookingDao.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));
        String reference = request.providerReference() == null ? null : request.providerReference().trim();
        boolean refund = request.type() == MarketplaceFinanceRequest.FinanceType.REFUND;
        if (!refund && complaintDao.existsOpenComplaintForBooking(bookingId)) {
            throw new PMSCustomException(ResponseCode.SP_COMPLAINT_ALREADY_EXISTS);
        }
        if (!refund && (booking.getDisputeDeadlineAt() == null
                || booking.getDisputeDeadlineAt().isAfter(ZonedDateTime.now(ZoneId.of("UTC"))))) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Settlement remains on hold until the customer complaint period has ended.");
        }
        var previous = new org.pms.silverocean.service.payment.MarketplaceFinanceGuard.Entry(
                refund ? booking.getRefundStatus() : booking.getSettlementStatus(),
                refund ? booking.getRefundedAmount() : booking.getSettledAmount(),
                refund ? booking.getRefundReference() : booking.getSettlementReference());
        var other = new org.pms.silverocean.service.payment.MarketplaceFinanceGuard.Entry(
                refund ? booking.getSettlementStatus() : booking.getRefundStatus(),
                refund ? booking.getSettledAmount() : booking.getRefundedAmount(), null);
        boolean partiallyPaidRefund = refund && "PARTIALLY_PAID".equals(booking.getPaymentStatus());
        BigDecimal financeLimit = partiallyPaidRefund ? booking.getRefundedAmount() : booking.getQuotedAmount();
        boolean exactBookingReplay = org.pms.silverocean.service.payment.MarketplaceFinanceGuard.validate(
                financeLimit, "PAID".equals(booking.getPaymentStatus()) || partiallyPaidRefund,
                BookingStatus.COMPLETED.name().equals(booking.getStatus()), refund,
                new org.pms.silverocean.service.payment.MarketplaceFinanceGuard.Entry(request.status().name(), request.amount(), reference),
                previous, other);
        if (request.status() == MarketplaceFinanceRequest.FinanceStatus.CONFIRMED && (reference == null || reference.isBlank())) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
        }
        if (!refund && request.status() == MarketplaceFinanceRequest.FinanceStatus.CONFIRMED) {
            requireSettlementDestination(booking);
        }
        if (request.status() == MarketplaceFinanceRequest.FinanceStatus.CONFIRMED) {
            financeOperations.recordConfirmed(booking, resolveProviderUserId(booking), request.type(),
                    request.amount(), reference, userDao.getUserId());
        }
        if (exactBookingReplay) return toDTO(booking);
        if (request.type() == MarketplaceFinanceRequest.FinanceType.REFUND) {
            BigDecimal refundableAmount = partiallyPaidRefund ? booking.getRefundedAmount() : booking.getQuotedAmount();
            boolean refundableStatus = "PAID".equals(booking.getPaymentStatus()) || partiallyPaidRefund;
            if (!refundableStatus || refundableAmount == null
                    || request.amount().compareTo(refundableAmount) > 0
                    || (partiallyPaidRefund && request.amount().compareTo(refundableAmount) != 0)) {
                throw new PMSCustomException(ResponseCode.SP_BOOKING_INVALID_STATUS);
            }
            booking.setRefundStatus(request.status().name());
            booking.setRefundedAmount(request.amount());
            booking.setRefundReference(reference);
            if(request.status()==MarketplaceFinanceRequest.FinanceStatus.CONFIRMED
                    && request.amount().compareTo(refundableAmount)==0){booking.setPaymentStatus("REFUNDED");if(!BookingStatus.COMPLETED.name().equals(booking.getStatus()))booking.setStatus(BookingStatus.CANCELLED.name());}
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
        notifyFinanceRecord(booking, refund);
        return toDTO(booking);
    }

    private long resolveProviderUserId(ServiceBooking booking) {
        ProviderService listing = serviceDao.findById(booking.getServiceId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        return profileDao.findById(listing.getProfileId())
                .map(ProviderProfile::getUserId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
    }

    public Page<MarketplaceFinanceQueueDTO> listFinanceQueue(Pageable pageable,
            MarketplaceFinanceRequest.FinanceType type,
            MarketplaceFinanceRequest.FinanceStatus status) {
        if (!userDao.hasRole(PMSRole.FINANCE)) {
            throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);
        }
        List<String> statuses = status == null
                ? List.of(MarketplaceFinanceRequest.FinanceStatus.REQUESTED.name(),
                        MarketplaceFinanceRequest.FinanceStatus.PROCESSING.name(),
                        MarketplaceFinanceRequest.FinanceStatus.FAILED.name())
                : List.of(status.name());
        Pageable bounded = PageRequest.of(Math.max(0, pageable.getPageNumber()),
                Math.min(100, Math.max(1, pageable.getPageSize())));
        return hydrateFinanceQueue(bookingDao.findFinanceQueue(bounded, type == null ? null : type.name(), statuses));
    }

    private void applyLocation(ServiceBooking booking, CreateBookingRequest request, long userId) {
        String address=org.apache.commons.lang3.StringUtils.trimToNull(request.serviceAddress());
        if (address == null && request.unitId() == null) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Enter the address where the service is required or select a linked unit.");
        }
        booking.setServiceAddress(address == null ? "Linked unit " + request.unitId() : address);
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

    private void finalizeCompletion(ServiceBooking booking, ZonedDateTime finalizedAt,
                                    boolean customerAccepted, String auditAction) {
        booking.setStatus(BookingStatus.COMPLETED.name());
        if (booking.getCompletedAt() == null) {
            booking.setCompletedAt(finalizedAt);
        }
        if (customerAccepted || booking.getDisputeDeadlineAt() == null) {
            // Explicit acceptance ends the confirmation/complaint window early.
            booking.setDisputeDeadlineAt(finalizedAt);
        }
        boolean settlementReleased = releaseSettlement(booking);
        bookingDao.save(booking, auditAction);
        notifyProgress(booking);
        if (settlementReleased) {
            notifyFinanceRecord(booking, false);
        }
    }

    private boolean releaseSettlement(ServiceBooking booking) {
        String settlementStatus = org.apache.commons.lang3.StringUtils.defaultIfBlank(
                booking.getSettlementStatus(), "PENDING");
        String refundStatus = org.apache.commons.lang3.StringUtils.defaultIfBlank(
                booking.getRefundStatus(), "NOT_REQUIRED");
        if (!"PAID".equals(booking.getPaymentStatus())
                || !"PENDING".equals(settlementStatus)
                || java.util.Set.of("REQUESTED", "PROCESSING", "FAILED").contains(refundStatus)
                || booking.getQuotedAmount() == null) {
            return false;
        }
        BigDecimal confirmedRefund = "CONFIRMED".equals(refundStatus) && booking.getRefundedAmount() != null
                ? booking.getRefundedAmount() : BigDecimal.ZERO;
        BigDecimal releasableAmount = booking.getQuotedAmount().subtract(confirmedRefund);
        if (releasableAmount.signum() <= 0) {
            return false;
        }
        booking.setSettlementStatus(MarketplaceFinanceRequest.FinanceStatus.REQUESTED.name());
        booking.setSettledAmount(releasableAmount);
        booking.setSettlementReference(null);
        return true;
    }

    private ZonedDateTime utcNow() {
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }

    private void assertIdempotentPayload(ServiceBooking existing, CreateBookingRequest request) {
        ZonedDateTime requestedAt=request.scheduledAt().atZone(ZoneId.of("Africa/Nairobi")).withZoneSameInstant(ZoneId.of("UTC"));
        boolean matches=existing.getServiceId()==request.serviceId()
                && java.util.Objects.equals(existing.getScheduledAt(),requestedAt)
                && java.util.Objects.equals(org.apache.commons.lang3.StringUtils.trimToNull(existing.getNotes()),org.apache.commons.lang3.StringUtils.trimToNull(request.notes()))
                && java.util.Objects.equals(existing.getPropertyId(),request.propertyId())
                && java.util.Objects.equals(existing.getUnitId(),request.unitId())
                && normalizedStoredQuantity(existing).compareTo(
                        normalizedUnitQuantity(existing.getPricingUnit(), request.unitQuantity())) == 0
                && expectedQuoteMatches(request, existing.getPricingUnitRate(), existing.getCurrency(),
                        existing.getPricingUnit())
                && java.util.Objects.equals(org.apache.commons.lang3.StringUtils.trimToNull(existing.getServiceAddress()),
                    org.apache.commons.lang3.StringUtils.defaultIfBlank(org.apache.commons.lang3.StringUtils.trimToNull(request.serviceAddress()),
                            request.unitId()==null?null:"Linked unit "+request.unitId()));
        if(!matches)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                "This booking request key was already used for different details. Refresh the form and try again.");
    }

    private BigDecimal normalizedUnitQuantity(org.pms.silverocean.service.sp.enums.PricingUnit pricingUnit,
                                               BigDecimal requestedQuantity) {
        boolean fixedPrice = java.util.Set.of(org.pms.silverocean.service.sp.enums.PricingUnit.PER_JOB,
                org.pms.silverocean.service.sp.enums.PricingUnit.FIXED).contains(pricingUnit);
        if (requestedQuantity == null) {
            if (fixedPrice) return BigDecimal.ONE;
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Enter how many hours, days, weeks or months you need.");
        }
        BigDecimal quantity = requestedQuantity.stripTrailingZeros();
        if (quantity.signum() <= 0 || quantity.compareTo(new BigDecimal("999")) > 0
                || quantity.scale() > 0) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA, "Enter a valid service quantity.");
        }
        if (fixedPrice && quantity.compareTo(BigDecimal.ONE) != 0) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Per job and fixed-price services must use a quantity of one.");
        }
        return quantity;
    }

    private ZonedDateTime scheduledEndAt(ZonedDateTime scheduledLocal,
                                         org.pms.silverocean.service.sp.enums.PricingUnit pricingUnit,
                                         BigDecimal unitQuantity) {
        long quantity = unitQuantity.longValueExact();
        ZonedDateTime end = switch (pricingUnit) {
            case PER_HOUR -> scheduledLocal.plusHours(quantity);
            case PER_DAY -> scheduledLocal.plusDays(quantity);
            case PER_WEEK -> scheduledLocal.plusWeeks(quantity);
            case PER_MONTH -> scheduledLocal.plusMonths(quantity);
            default -> null;
        };
        return end == null ? null : end.withZoneSameInstant(ZoneId.of("UTC"));
    }

    private BigDecimal normalizedStoredQuantity(ServiceBooking booking) {
        return booking.getUnitQuantity() == null
                ? BigDecimal.ONE : booking.getUnitQuantity().stripTrailingZeros();
    }

    private void requireExpectedQuote(CreateBookingRequest request, BigDecimal unitRate, String currency,
                                      org.pms.silverocean.service.sp.enums.PricingUnit pricingUnit) {
        if (!expectedQuoteMatches(request, unitRate, currency, pricingUnit)) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "The service price or price type changed. Refresh the service and confirm the new quote.");
        }
    }

    private boolean expectedQuoteMatches(CreateBookingRequest request, BigDecimal unitRate, String currency,
                                         org.pms.silverocean.service.sp.enums.PricingUnit pricingUnit) {
        if (request.expectedUnitRate() == null || request.expectedCurrency() == null
                || request.expectedPricingUnit() == null) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Refresh the service before booking so its complete quote can be confirmed.");
        }
        try {
            BigDecimal expectedRate = org.pms.silverocean.service.payment.money.MonetaryPolicy.positive(
                    request.expectedUnitRate());
            String expectedCurrency = org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(
                    request.expectedCurrency());
            return unitRate != null
                    && org.pms.silverocean.service.payment.money.MonetaryPolicy.amount(unitRate)
                    .compareTo(expectedRate) == 0
                    && expectedCurrency.equals(currency)
                    && request.expectedPricingUnit() == pricingUnit;
        } catch (IllegalArgumentException invalidQuote) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Refresh the service before booking so its current quote can be confirmed.");
        }
    }

    private PMSInvoice createMarketplaceInvoice(ServiceBooking booking, ProviderService service, long providerUserId,
                                                long paymentAccountId) {
        Users customer = userDao.findById(booking.getCreatedBy())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
        String serviceName = org.apache.commons.lang3.StringUtils.defaultIfBlank(service.getTitle(),
                service.getCategoryName() == null ? "Service booking" : service.getCategoryName());
        String escapedName = HtmlUtils.htmlEscape(serviceName);
        BigDecimal quantity = normalizedStoredQuantity(booking);
        String pricingSummary = quantity.toPlainString() + " × "
                + booking.getCurrency() + " " + booking.getPricingUnitRate() + " "
                + booking.getPricingUnit().name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        PMSInvoice invoice = new PMSInvoice();
        invoice.setUnitId(booking.getUnitId() == null ? 0 : booking.getUnitId());
        invoice.setPropertyId(booking.getPropertyId() == null ? 0 : booking.getPropertyId());
        invoice.setDescription(("Marketplace service: " + serviceName + " (" + pricingSummary + ")")
                .getBytes(StandardCharsets.UTF_8));
        invoice.setHtmlDescription(("<tr><td><span>" + escapedName + "</span><br><small>"
                + HtmlUtils.htmlEscape(pricingSummary) + "</small></td><td class='amount-col'>"
                + booking.getQuotedAmount() + "</td></tr>").getBytes(StandardCharsets.UTF_8));
        invoice.setMoneyAmount(booking.getQuotedAmount());
        invoice.setMoneyPendingAmount(booking.getQuotedAmount());
        invoice.setCurrency(org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(booking.getCurrency()));
        invoice.setBilledUserId(booking.getCreatedBy());
        invoice.setPayToUserId(providerUserId);
        invoice.setPaymentAccountId(paymentAccountId);
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
        Map<Long,org.pms.silverocean.database.pms.entities.ComplaintCase> complaints=complaintDao.findActiveByBookingIds(page.stream().map(ServiceBooking::getId).toList()).stream().collect(Collectors.toMap(org.pms.silverocean.database.pms.entities.ComplaintCase::getBookingId,Function.identity()));
        long viewerId=userDao.getUserId();
        List<ServiceBookingDTO> content=page.stream().map(booking->{ProviderService service=services.get(booking.getServiceId());ProviderProfile profile=service==null?null:profiles.get(service.getProfileId());Users user=users.get(booking.getCreatedBy());String currentName=service==null?null:org.apache.commons.lang3.StringUtils.defaultIfBlank(service.getTitle(),service.getCategoryName());String name=org.apache.commons.lang3.StringUtils.defaultIfBlank(booking.getServiceTitleSnapshot(),currentName);String currentProvider=profile==null?null:profile.getBusinessName();String providerName=org.apache.commons.lang3.StringUtils.defaultIfBlank(booking.getProviderBusinessNameSnapshot(),currentProvider);return new ServiceBookingDTO(booking,name,providerName,user==null?null:user.getFullName(),viewerId).withRated(java.util.Objects.equals(booking.getCreatedBy(),viewerId)&&ratingDao.existsByBookingIdAndRater(booking.getId(),viewerId)).withDispute(complaints.get(booking.getId()));}).toList();
        return new PageImpl<>(content,page.getPageable(),page.getTotalElements());
    }

    private Page<MarketplaceFinanceQueueDTO> hydrateFinanceQueue(Page<ServiceBooking> page) {
        if (page.isEmpty()) return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        Map<Long,ProviderService> services=serviceDao.findAllById(page.stream().map(ServiceBooking::getServiceId).distinct().toList()).stream().collect(Collectors.toMap(ProviderService::getId,Function.identity()));
        Map<Long,ProviderProfile> profiles=profileDao.findAllById(services.values().stream().map(ProviderService::getProfileId).distinct().toList()).stream().collect(Collectors.toMap(ProviderProfile::getId,Function.identity()));
        Map<Long,Users> customers=userDao.findAllById(page.stream().map(ServiceBooking::getCreatedBy).distinct().toList()).stream().collect(Collectors.toMap(Users::getId,Function.identity()));
        java.util.Set<Long> disputed=complaintDao.findActiveByBookingIds(page.stream().map(ServiceBooking::getId).toList()).stream()
                .filter(complaint -> java.util.Set.of("OPEN","UNDER_REVIEW","ESCALATED").contains(complaint.getStatus()))
                .map(org.pms.silverocean.database.pms.entities.ComplaintCase::getBookingId)
                .collect(java.util.stream.Collectors.toSet());
        ZonedDateTime now=utcNow();
        List<MarketplaceFinanceQueueDTO> content=page.stream().map(booking->{
            ProviderService listing=services.get(booking.getServiceId());
            ProviderProfile provider=listing==null?null:profiles.get(listing.getProfileId());
            Users customer=customers.get(booking.getCreatedBy());
            boolean hold=disputed.contains(booking.getId()) || (booking.getDisputeDeadlineAt()!=null && booking.getDisputeDeadlineAt().isAfter(now));
            return new MarketplaceFinanceQueueDTO(booking.getId(),booking.getInvoiceRef(),
                    org.apache.commons.lang3.StringUtils.defaultIfBlank(booking.getServiceTitleSnapshot(),
                            listing==null?null:org.apache.commons.lang3.StringUtils.defaultIfBlank(listing.getTitle(),listing.getCategoryName())),
                    org.apache.commons.lang3.StringUtils.defaultIfBlank(booking.getProviderBusinessNameSnapshot(),
                            provider==null?null:provider.getBusinessName()),provider==null?null:provider.getUserId(),
                    customer==null?null:customer.getFullName(),booking.getQuotedAmount(),booking.getCurrency(),
                    booking.getPaymentStatus(),booking.getPaymentAccountId(),booking.getPaymentChannel(),
                    booking.getRefundStatus(),booking.getRefundedAmount(),booking.getRefundReference(),
                    booking.getSettlementStatus(),booking.getSettledAmount(),booking.getSettlementReference(),
                    hold,booking.getDisputeDeadlineAt(),booking.getCreatedOn());
        }).toList();
        return new PageImpl<>(content,page.getPageable(),page.getTotalElements());
    }

    private ServiceBookingDTO toDTO(ServiceBooking booking, ProviderService service) {
        String currentServiceName = service != null ? org.apache.commons.lang3.StringUtils.defaultIfBlank(service.getTitle(), service.getCategoryName()) : null;
        String serviceName = org.apache.commons.lang3.StringUtils.defaultIfBlank(
                booking.getServiceTitleSnapshot(), currentServiceName);
        String currentProviderName = service != null
                ? profileDao.findById(service.getProfileId()).map(ProviderProfile::getBusinessName).orElse(null)
                : null;
        String providerName = org.apache.commons.lang3.StringUtils.defaultIfBlank(
                booking.getProviderBusinessNameSnapshot(), currentProviderName);
        String bookedByName = userDao.findById(booking.getCreatedBy())
                .map(Users::getFullName).orElse(null);
        long viewerId=userDao.getUserId();
        return new ServiceBookingDTO(booking, serviceName, providerName, bookedByName, viewerId)
                .withRated(java.util.Objects.equals(booking.getCreatedBy(),viewerId)
                        && ratingDao.existsByBookingIdAndRater(booking.getId(),viewerId))
                .withDispute(complaintDao.findActiveByBookingId(booking.getId()).orElse(null));
    }

    private void sendBookingNotification(ServiceBooking booking, NotificationType type) {
        userDao.findById(booking.getCreatedBy()).ifPresent(user -> {
            String message=String.format(i18NService.getLocalizedMessage(type.getBody()),booking.getServiceId(),
                    booking.getScheduledAt()!=null?booking.getScheduledAt().toString():"",
                    booking.getCancellationReason()!=null?HtmlUtils.htmlEscape(booking.getCancellationReason()):"");
            notificationService.queueEmailAndInAppOnce(bookingEventKey(booking),user.getEmail(),type,message,
                    "SERVICE_BOOKING_STATUS",bookingMessage(booking),buyerBookingLink(booking));
        });
        notifyProvider(booking);
    }
    private String bookingEventKey(ServiceBooking booking){
        return "service-booking:"+booking.getId()+":"+booking.getStatus()+":"+booking.getPaymentStatus()+":"+booking.getRefundStatus();
    }
    private void notifyFinanceRecord(ServiceBooking booking,boolean refund){
        String key="service-finance:"+booking.getId()+":"+(refund?"refund":"settlement")+":"+(refund?booking.getRefundStatus():booking.getSettlementStatus())+":"+(refund?booking.getRefundedAmount():booking.getSettledAmount());
        String message=refund?"The refund record for service booking #"+booking.getId()+" was updated.":"The receiving-payment record for service booking #"+booking.getId()+" was updated.";
        if(refund)businessAlerts.publish(booking.getCreatedBy(),key,"SERVICE_BOOKING_STATUS",message,buyerBookingLink(booking));
        serviceDao.findById(booking.getServiceId()).flatMap(service->profileDao.findById(service.getProfileId())).filter(ProviderProfile::isActive)
                .filter(profile->!refund||!java.util.Objects.equals(profile.getUserId(),booking.getCreatedBy()))
                .ifPresent(profile->businessAlerts.publish(profile.getUserId(),key,"SERVICE_BOOKING_STATUS",message,providerBookingLink(booking)));
    }
    private String bookingMessage(ServiceBooking booking){
        return "Service booking "+booking.getId()+": "+booking.getStatus().replace('_',' ').toLowerCase(java.util.Locale.ROOT)+".";
    }
    private void notifyProgress(ServiceBooking booking){
        businessAlerts.publish(booking.getCreatedBy(),bookingEventKey(booking),"SERVICE_BOOKING_STATUS",bookingMessage(booking),buyerBookingLink(booking));
        notifyProvider(booking);
    }
    private void notifyProvider(ServiceBooking booking){
        serviceDao.findById(booking.getServiceId()).flatMap(service->profileDao.findById(service.getProfileId()))
                .filter(ProviderProfile::isActive).filter(profile->!java.util.Objects.equals(profile.getUserId(),booking.getCreatedBy()))
                .ifPresent(profile->businessAlerts.publish(profile.getUserId(),bookingEventKey(booking),"SERVICE_BOOKING_STATUS",bookingMessage(booking),providerBookingLink(booking)));
    }
    private String buyerBookingLink(ServiceBooking booking){return "/dashboard/marketplace?tab=bookings&bookingId="+booking.getId();}
    private String providerBookingLink(ServiceBooking booking){return "/dashboard/marketplace?tab=bookings&bookingId="+booking.getId();}
}
