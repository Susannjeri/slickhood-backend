package org.pms.silverocean.service.sales;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.estate.EstateService;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.leasedocument.LeaseDocumentStatus;
import org.pms.silverocean.service.leasedocument.LeaseDocumentType;
import org.pms.silverocean.service.invites.InviteService;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

@Service
public class SalesService {
    private static final Map<SaleStatus, EnumSet<SaleStatus>> TRANSITIONS = Map.of(
            SaleStatus.LEAD, EnumSet.of(SaleStatus.VIEWING, SaleStatus.OFFERED, SaleStatus.CANCELLED),
            SaleStatus.VIEWING, EnumSet.of(SaleStatus.OFFERED, SaleStatus.CANCELLED),
            SaleStatus.OFFERED, EnumSet.of(SaleStatus.RESERVED, SaleStatus.CANCELLED),
            SaleStatus.RESERVED, EnumSet.of(SaleStatus.DUE_DILIGENCE, SaleStatus.CANCELLED),
            SaleStatus.DUE_DILIGENCE, EnumSet.of(SaleStatus.AGREEMENT, SaleStatus.CANCELLED),
            SaleStatus.AGREEMENT, EnumSet.of(SaleStatus.COMPLETION, SaleStatus.CANCELLED),
            SaleStatus.COMPLETION, EnumSet.of(SaleStatus.COMPLETED, SaleStatus.CANCELLED));

    private final SaleTransactionRepo sales;
    private final PropertyRepo properties;
    private final UnitRepo units;
    private final UserDao users;
    private final EstateService estates;
    private final SaleMilestoneRepo milestones;
    private final InviteService invites;
    private final NotificationService notifications;
    private final I18NService i18n;
    private final LeaseDocumentRepo documents;

    public SalesService(SaleTransactionRepo sales, PropertyRepo properties, UnitRepo units, UserDao users,
                        EstateService estates, SaleMilestoneRepo milestones, InviteService invites,
                        NotificationService notifications, I18NService i18n, LeaseDocumentRepo documents) {
        this.sales = sales; this.properties = properties; this.units = units; this.users = users;
        this.estates = estates; this.milestones = milestones; this.invites = invites;
        this.notifications = notifications; this.i18n = i18n;
        this.documents = documents;
    }

    @Transactional
    public SaleTransaction create(CreateSaleRequest request) {
        long actorId = users.getUserId();
        Property property = requireSaleProperty(request.propertyId(), actorId);
        if (property.getManagementMode() != PMSPropertyManagementMode.SALE) throw invalid();
        Users buyer = resolveBuyer(request);
        String buyerEmail = buyer != null ? buyer.getEmail().trim().toLowerCase(Locale.ROOT)
                : request.buyerEmail().trim().toLowerCase(Locale.ROOT);
        if (buyer != null && buyer.getId() == actorId) throw invalid();
        Unit unit = units.findAndLockById(request.unitId())
                .filter(candidate -> candidate.isActive() && candidate.getPropertyId() == property.getId()
                        && PMSLeaseMode.SALE.name().equals(candidate.getLeaseMode()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        if (unit.getCurrency() == null || !unit.getCurrency().equalsIgnoreCase(request.currency())) throw invalid();
        if (sales.existsByUnitIdAndActiveTrueAndStatusNot(unit.getId(), SaleStatus.CANCELLED))
            throw new PMSCustomException(ResponseCode.DATA_INTEGRITY_VIOLATION);
        SaleTransaction sale = new SaleTransaction();
        sale.setPropertyId(property.getId()); sale.setUnitId(unit.getId()); sale.setSalesAgentUserId(actorId);
        sale.setBuyerUserId(buyer == null ? null : buyer.getId()); sale.setInvitedBuyerEmail(buyerEmail);
        sale.setStatus(SaleStatus.LEAD); sale.setAskingPrice(request.askingPrice());
        sale.setCurrency(request.currency().trim().toUpperCase()); sale.setNotes(StringUtils.trimToNull(request.notes()));
        sale.setCreatedBy(actorId); sale.setActive(true);
        SaleTransaction saved = sales.save(sale);
        invites.createBuyerInvite(saved.getId(), buyerEmail);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<SaleView> list(Pageable pageable) {
        long userId = users.getUserId(); Pageable bounded = bounded(pageable);
        return switch (users.getActiveRole()) {
            case BUYER -> sales.findViewPageByBuyer(userId, bounded).map(SaleView::redactInternalNotes);
            case SUPER_ADMIN -> sales.findAllActiveViews(bounded);
            default -> users.hasPermission(Permission.VIEW_SALE_PIPELINE)
                    ? sales.findViewPageByPropertyAccess(userId, bounded) : Page.empty(bounded);
        };
    }

    @Transactional
    public SaleTransaction update(long id, UpdateSaleRequest request) {
        SaleTransaction sale = managedForUpdate(id);
        if (request.status() == SaleStatus.RESERVED) throw invalidTransition();
        if (request.status() == SaleStatus.OFFERED) {
            if (request.offerAmount() == null) throw invalid();
            sale.setOfferAmount(request.offerAmount());
        } else if (request.offerAmount() != null) throw invalid();
        if (request.status() == SaleStatus.CANCELLED && StringUtils.isBlank(request.notes())) throw invalid();
        requireMilestones(sale, request.status()); transition(sale, request.status());
        if (request.notes() != null) sale.setNotes(StringUtils.trimToNull(request.notes()));
        if (request.status() == SaleStatus.COMPLETED) {
            if (sale.getBuyerUserId() == null) throw invalidTransition();
            sale.setCompletedAt(LocalDateTime.now());
            estates.transferFromSale(sale.getPropertyId(), sale.getUnitId(), sale.getBuyerUserId(), sale.getId());
        }
        SaleTransaction updated = sales.save(sale);
        notifyBuyer(updated, request.status() == SaleStatus.CANCELLED ? request.notes() : null);
        return updated;
    }

    @Transactional
    public SaleMilestone addMilestone(long saleId, SaleMilestoneModels.Create request) {
        SaleTransaction sale = managedForUpdate(saleId);
        if (sale.getStatus() == SaleStatus.COMPLETED || sale.getStatus() == SaleStatus.CANCELLED) throw invalidTransition();
        if (request.status() == SaleMilestoneModels.Status.COMPLETED
                && milestones.existsBySaleIdAndMilestoneTypeAndStatus(saleId, request.type().name(), request.status().name()))
            throw new PMSCustomException(ResponseCode.DATA_INTEGRITY_VIOLATION);
        if (request.type() == SaleMilestoneModels.Type.ESCROW_FUNDED && request.status() == SaleMilestoneModels.Status.COMPLETED
                && (request.amount() == null || StringUtils.isBlank(request.externalReference()))) throw invalid();
        if (request.status() == SaleMilestoneModels.Status.COMPLETED
                && request.type() != SaleMilestoneModels.Type.ESCROW_FUNDED && request.evidenceDocumentId() == null) throw invalid();
        if (request.evidenceDocumentId() != null) {
            LeaseDocument evidence = documents.findByIdAndPropertyIdAndUnitIdAndActiveTrue(
                            request.evidenceDocumentId(), sale.getPropertyId(), sale.getUnitId())
                    .orElseThrow(this::invalid);
            boolean involvedParty = evidence.getIssuerUserId() == sale.getSalesAgentUserId()
                    || (sale.getBuyerUserId() != null && evidence.getRecipientUserId() == sale.getBuyerUserId());
            if (!involvedParty || !Objects.equals(evidence.getSaleId(), sale.getId())) throw invalid();
        }
        return milestones.save(new SaleMilestone(saleId, request.type().name(), request.status().name(), request.amount(),
                sale.getCurrency(), StringUtils.left(StringUtils.trimToNull(request.externalReference()), 120),
                request.evidenceDocumentId(), StringUtils.left(StringUtils.trimToNull(request.notes()), 1000),
                java.time.ZonedDateTime.now(PMSUtils.getZoneId()), users.getUserId()));
    }

    @Transactional(readOnly = true)
    public Page<SaleMilestone> milestones(long saleId, Pageable pageable) {
        long userId = users.getUserId();
        SaleTransaction sale = sales.findById(saleId).filter(SaleTransaction::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SALE_NOT_FOUND));
        boolean visible = users.getActiveRole() == PMSRole.SUPER_ADMIN || Objects.equals(sale.getBuyerUserId(), userId)
                || sales.findByIdAndPropertyAccess(saleId, userId).isPresent();
        if (!visible) throw new PMSCustomException(ResponseCode.SALE_NOT_FOUND);
        return milestones.findAllBySaleIdOrderByOccurredAtAsc(saleId, bounded(pageable));
    }

    @Transactional
    public SaleTransaction acceptOffer(long id) {
        SaleTransaction sale = sales.findByIdForUpdate(id)
                .filter(candidate -> Objects.equals(candidate.getBuyerUserId(), users.getUserId()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SALE_NOT_FOUND));
        if (!documents.existsBySaleIdAndDocumentTypeAndStatusAndActiveTrue(id,
                LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER, LeaseDocumentStatus.SIGNED)) {
            throw invalidTransition();
        }
        return reserve(sale);
    }

    @Transactional
    public SaleTransaction acceptSignedOffer(long saleId, long documentId, java.math.BigDecimal amount) {
        SaleTransaction sale = sales.findByIdForUpdate(saleId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SALE_NOT_FOUND));
        if (sale.getStatus() != SaleStatus.OFFERED || sale.getOfferAmount() == null
                || amount == null || sale.getOfferAmount().compareTo(amount) != 0) throw invalidTransition();
        LeaseDocument document = documents.findById(documentId).filter(LeaseDocument::isActive)
                .filter(d -> Objects.equals(d.getSaleId(), saleId))
                .filter(d -> d.getDocumentType() == LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER)
                .filter(d -> d.getStatus() == LeaseDocumentStatus.SIGNED)
                .orElseThrow(this::invalidTransition);
        if (!Objects.equals(sale.getBuyerUserId(), document.getRecipientUserId())) throw invalidTransition();
        return reserve(sale);
    }

    private SaleTransaction reserve(SaleTransaction sale) {
        transition(sale, SaleStatus.RESERVED);
        sale.setOfferAcceptedAt(LocalDateTime.now());
        SaleTransaction accepted = sales.save(sale);
        notifySalesAgent(accepted);
        return accepted;
    }

    private SaleTransaction managedForUpdate(long id) {
        SaleTransaction sale = sales.findByIdForUpdate(id)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SALE_NOT_FOUND));
        requireSaleProperty(sale.getPropertyId(), users.getUserId());
        return sale;
    }

    private void requireMilestones(SaleTransaction sale, SaleStatus next) {
        if (next == SaleStatus.AGREEMENT) require(sale, SaleMilestoneModels.Type.DUE_DILIGENCE_CHECK);
        if (next == SaleStatus.COMPLETION) { require(sale, SaleMilestoneModels.Type.AGREEMENT_SIGNED); require(sale, SaleMilestoneModels.Type.ESCROW_FUNDED); }
        if (next == SaleStatus.COMPLETED) { require(sale, SaleMilestoneModels.Type.TRANSFER_REGISTERED); require(sale, SaleMilestoneModels.Type.HANDOVER_COMPLETED); }
    }

    private void require(SaleTransaction sale, SaleMilestoneModels.Type type) {
        if (!milestones.existsBySaleIdAndMilestoneTypeAndStatus(sale.getId(), type.name(), SaleMilestoneModels.Status.COMPLETED.name()))
            throw invalidTransition();
    }

    private void transition(SaleTransaction sale, SaleStatus next) {
        if (!TRANSITIONS.getOrDefault(sale.getStatus(), EnumSet.noneOf(SaleStatus.class)).contains(next)) throw invalidTransition();
        sale.setStatus(next);
    }

    private Property requireSaleProperty(long propertyId, long userId) {
        PMSRole role = users.getActiveRole();
        return (role == PMSRole.LANDLORD || role == PMSRole.SALES_AGENT
                ? properties.findByIdAndCreatedByAndActiveTrue(propertyId, userId)
                : properties.findByIdAndManagerRole(propertyId, userId, role.name()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
    }

    private Users resolveBuyer(CreateSaleRequest request) {
        if (request.buyerUserId() == null && StringUtils.isBlank(request.buyerEmail())) throw invalid();
        Users byId = request.buyerUserId() == null ? null : users.findById(request.buyerUserId())
                .filter(Users::isActive).orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        Users byEmail = StringUtils.isBlank(request.buyerEmail()) ? null : users.findByEmail(request.buyerEmail().trim().toLowerCase(Locale.ROOT))
                .filter(Users::isActive).orElse(null);
        if (byId != null && byEmail != null && byId.getId() != byEmail.getId()) throw invalid();
        if (byId != null && !StringUtils.isBlank(request.buyerEmail())
                && !byId.getEmail().equalsIgnoreCase(request.buyerEmail().trim())) throw invalid();
        return byId != null ? byId : byEmail;
    }

    private void notifyBuyer(SaleTransaction sale, String detail) {
        String recipient = sale.getInvitedBuyerEmail();
        if (StringUtils.isBlank(recipient)) return;
        String name = sale.getBuyerUserId() == null ? "Buyer" : users.findById(sale.getBuyerUserId())
                .map(Users::getFullName).filter(StringUtils::isNotBlank).orElse("Buyer");
        queueStatus(recipient, name, sale, detail);
    }

    private void notifySalesAgent(SaleTransaction sale) {
        users.findById(sale.getSalesAgentUserId()).filter(Users::isActive)
                .filter(agent -> StringUtils.isNotBlank(agent.getEmail()))
                .ifPresent(agent -> queueStatus(agent.getEmail(), agent.getFullName(), sale, " The buyer accepted the offer."));
    }

    private void queueStatus(String recipient, String name, SaleTransaction sale, String detail) {
        String suffix = StringUtils.isBlank(detail) ? "" : " " + StringUtils.abbreviate(detail.trim(), 300);
        String body = String.format(i18n.getLocalizedMessage(NotificationType.SALE_STATUS_EMAIL.getBody()),
                StringUtils.defaultIfBlank(name, "Customer"), sale.getPropertyId(), sale.getUnitId(), sale.getStatus(), suffix);
        notifications.queueNotification(new NotificationDTO(body, recipient, NotificationType.SALE_STATUS_EMAIL));
    }

    private Pageable bounded(Pageable pageable) { return PageRequest.of(Math.max(0, pageable.getPageNumber()), Math.min(100, Math.max(1, pageable.getPageSize()))); }
    private PMSCustomException invalid() { return new PMSCustomException(ResponseCode.INVALID_FIELD_DATA); }
    private PMSCustomException invalidTransition() { return new PMSCustomException(ResponseCode.SALE_INVALID_TRANSITION); }
}
