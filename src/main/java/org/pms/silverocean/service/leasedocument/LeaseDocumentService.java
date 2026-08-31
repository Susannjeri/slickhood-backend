package org.pms.silverocean.service.leasedocument;

import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.lease.LeaseDao;
import org.pms.silverocean.service.lease.LeaseService;
import org.pms.silverocean.service.mustache.RenderService;
import org.pms.silverocean.service.notification.email.EmailService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaseDocumentService {
    private final LeaseDocumentRepo documentRepo;
    private final LeaseDocumentTemplateRepo templateRepo;
    private final LeaseDao leaseDao;
    private final PropertyRepo propertyRepo;
    private final UnitRepo unitRepo;
    private final UserDao userDao;
    private final RenderService renderService;
    private final EmailService emailService;
    private final LeaseService leaseService;

    public LeaseDocumentService(LeaseDocumentRepo documentRepo, LeaseDocumentTemplateRepo templateRepo,
            LeaseDao leaseDao, PropertyRepo propertyRepo, UnitRepo unitRepo, UserDao userDao,
            RenderService renderService, EmailService emailService, LeaseService leaseService) {
        this.documentRepo = documentRepo;
        this.templateRepo = templateRepo;
        this.leaseDao = leaseDao;
        this.propertyRepo = propertyRepo;
        this.unitRepo = unitRepo;
        this.userDao = userDao;
        this.renderService = renderService;
        this.emailService = emailService;
        this.leaseService = leaseService;
    }

    @Transactional
    public LeaseDocumentDTO generate(GenerateLeaseDocumentRequest request) {
        long currentUserId = userDao.getUserId();
        LeaseDocumentType type = request.documentType();
        Context context = type.requiresLease() ? leaseContext(request, currentUserId) : propertyContext(request, currentUserId);
        validateDocumentSequence(request, context);
        LeaseDocumentTemplate template = templateRepo.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(type)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.TEMPLATE_NOT_FOUND));

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("documentName", template.getDisplayName());
        model.put("propertyName", context.property().getName());
        model.put("propertyAddress", context.property().getAddress());
        model.put("unitRef", context.unit() == null ? "Not applicable" : context.unit().getRef());
        model.put("issuerName", context.issuer().getFullName());
        model.put("recipientName", context.recipient().getFullName());
        model.put("effectiveDate", request.effectiveDate() == null ? "To be agreed" : request.effectiveDate().toString());
        model.put("responseDueDate", request.responseDueDate() == null ? "Not applicable" : request.responseDueDate().toString());
        model.put("amount", request.amount() == null ? "Not applicable" : request.amount().toPlainString());
        model.put("currency", StringUtils.defaultIfBlank(request.currency(), context.property().getCurrency()));
        model.put("reason", StringUtils.defaultIfBlank(request.reason(), "As recorded in the related agreement and workflow."));
        model.put("legalReviewRequired", template.isLegalReviewRequired());
        model.put("templateVersion", template.getVersion());

        LeaseDocument document = new LeaseDocument();
        document.setLeaseId(request.leaseId());
        document.setPropertyId(context.property().getId());
        document.setUnitId(context.unit() == null ? null : context.unit().getId());
        document.setTemplateId(template.getId());
        document.setTemplateVersion(template.getVersion());
        document.setDocumentType(type);
        document.setStatus(LeaseDocumentStatus.DRAFT);
        document.setName(template.getDisplayName());
        document.setRenderedHtml(renderService.renderInline(template.getBodyHtml(), model));
        document.setIssuerUserId(context.issuer().getId());
        document.setRecipientUserId(context.recipient().getId());
        document.setEffectiveDate(request.effectiveDate());
        document.setResponseDueDate(request.responseDueDate());
        document.setAmount(request.amount());
        document.setCurrency(StringUtils.defaultIfBlank(request.currency(), context.property().getCurrency()));
        document.setReason(request.reason());
        document.setLegalReviewRequired(template.isLegalReviewRequired());
        document.setCreatedBy(currentUserId);
        document.setActive(true);
        return new LeaseDocumentDTO(documentRepo.save(document));
    }

    public List<LeaseDocumentDTO> list() {
        return documentRepo.findAllAccessible(userDao.getUserId()).stream().map(LeaseDocumentDTO::new).toList();
    }

    public void renderPdf(long id, ByteArrayOutputStream output) throws IOException {
        LeaseDocument document = accessible(id);
        renderService.toPdf(document.getRenderedHtml(), output);
    }

    @Transactional
    public LeaseDocumentDTO issue(long id) {
        LeaseDocument document = accessible(id);
        if (document.getIssuerUserId() != userDao.getUserId() || document.getStatus() != LeaseDocumentStatus.DRAFT) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        if (document.isLegalReviewRequired()) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        Users recipient = userDao.findById(document.getRecipientUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            renderService.toPdf(document.getRenderedHtml(), output);
            emailService.sendAttachment(recipient.getEmail(), "<p>A document has been issued to you in SlickHood.</p>",
                    document.getName(), output.toByteArray(), "slickhood-document-" + document.getId() + ".pdf");
        } catch (IOException | MessagingException e) {
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE, e);
        }
        document.setStatus(LeaseDocumentStatus.ISSUED);
        document.setDeliveryChannel("EMAIL_AND_IN_APP");
        document.setIssuedAt(LocalDateTime.now());
        return new LeaseDocumentDTO(documentRepo.save(document));
    }

    @Transactional
    public LeaseDocumentDTO acknowledge(long id) {
        LeaseDocument document = accessible(id);
        if (document.getRecipientUserId() != userDao.getUserId() || document.getStatus() != LeaseDocumentStatus.ISSUED) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        document.setAcknowledgedAt(LocalDateTime.now());
        document.setStatus(LeaseDocumentStatus.ACKNOWLEDGED);
        return new LeaseDocumentDTO(documentRepo.save(document));
    }

    @Transactional
    public LeaseDocumentDTO sign(long id) {
        LeaseDocument document = accessible(id);
        if (document.getStatus() != LeaseDocumentStatus.ISSUED && document.getStatus() != LeaseDocumentStatus.ACKNOWLEDGED
                && document.getStatus() != LeaseDocumentStatus.PARTIALLY_SIGNED) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        long userId = userDao.getUserId();
        LocalDateTime now = LocalDateTime.now();
        if (document.getIssuerUserId() == userId) document.setIssuerSignedAt(now);
        else if (document.getRecipientUserId() == userId) document.setRecipientSignedAt(now);
        else throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_NOT_FOUND);
        document.setStatus(document.getIssuerSignedAt() != null && document.getRecipientSignedAt() != null
                ? LeaseDocumentStatus.SIGNED : LeaseDocumentStatus.PARTIALLY_SIGNED);
        LeaseDocument saved = documentRepo.save(document);
        if (saved.getStatus() == LeaseDocumentStatus.SIGNED && saved.getDocumentType().isTenancyAgreement()) {
            leaseService.activateFromGovernedAgreement(saved.getLeaseId(), saved.getIssuerUserId(), saved.getRecipientUserId(),
                    saved.getIssuerSignedAt(), saved.getRecipientSignedAt());
        }
        return new LeaseDocumentDTO(saved);
    }

    @Transactional
    public LeaseDocumentTemplate createTemplateVersion(TemplateVersionRequest request) {
        validateTemplate(request.bodyHtml());
        int version = templateRepo.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(request.documentType())
                .map(t -> t.getVersion() + 1).orElse(1);
        LeaseDocumentTemplate template = new LeaseDocumentTemplate();
        template.setDocumentType(request.documentType());
        template.setDisplayName(request.displayName());
        template.setVersion(version);
        template.setBodyHtml(request.bodyHtml());
        template.setLegalReviewRequired(request.legalReviewRequired());
        template.setCreatedBy(userDao.getUserId());
        template.setActive(true);
        return templateRepo.save(template);
    }

    public List<LeaseDocumentTemplate> templates() {
        return templateRepo.findAllByActiveTrueOrderByDocumentTypeAscVersionDesc();
    }

    private LeaseDocument accessible(long id) {
        return documentRepo.findAccessible(id, userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_DOCUMENT_NOT_FOUND));
    }

    private Context leaseContext(GenerateLeaseDocumentRequest request, long userId) {
        if (request.leaseId() == null) throw new PMSCustomException(ResponseCode.LEASE_NOT_FOUND);
        PMSRole role = userDao.getActiveRole();
        if (request.documentType().isTenantInitiated() && role != PMSRole.TENANT) {
            throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        }
        if (!request.documentType().isTenantInitiated() && role == PMSRole.TENANT) {
            throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        }
        Lease lease = (role == PMSRole.TENANT
                ? leaseDao.getLeaseByIdAndTenantId(request.leaseId(), userId)
                : role == PMSRole.LANDLORD
                    ? leaseDao.getLeaseByIdAndOwner(request.leaseId(), userId)
                    : leaseDao.getLeaseByIdAndManagerRole(request.leaseId(), userId, role.name()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        UnitTenant tenancy = leaseDao.getUnitTenantByTenantId(lease.getTenantId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        Unit unit = unitRepo.findById(tenancy.getUnitId()).filter(Unit::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        Property property = propertyRepo.findById(unit.getPropertyId()).filter(Property::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        Users tenant = userDao.findById(tenancy.getUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        Users owner = userDao.findById(property.getCreatedBy()).orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        return request.documentType().isTenantInitiated()
                ? new Context(property, unit, tenant, owner, lease)
                : new Context(property, unit, userDao.getUserObject(), tenant, lease);
    }

    private Context propertyContext(GenerateLeaseDocumentRequest request, long userId) {
        if (request.propertyId() == null || request.recipientUserId() == null) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        }
        PMSRole role = userDao.getActiveRole();
        Property property = (role == PMSRole.LANDLORD
                ? propertyRepo.findByIdAndCreatedByAndActiveTrue(request.propertyId(), userId)
                : propertyRepo.findByIdAndManagerRole(request.propertyId(), userId, role.name()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        Users recipient = userDao.findById(request.recipientUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        return new Context(property, null, userDao.getUserObject(), recipient, null);
    }

    private void validateDocumentSequence(GenerateLeaseDocumentRequest request, Context context) {
        if (context.lease() == null) return;
        LeaseDocumentType type = request.documentType();
        long leaseId = context.lease().getId();
        if (!"RENT".equals(context.lease().getLeaseMode())) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        if (documentRepo.existsOpen(leaseId, type)) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        if (type == LeaseDocumentType.RENTAL_LETTER_OF_OFFER) {
            if (context.lease().isSigned() || request.responseDueDate() == null
                    || !request.responseDueDate().isAfter(java.time.LocalDate.now())
                    || request.amount() == null || request.amount().signum() <= 0
                    || request.amount().compareTo(java.math.BigDecimal.valueOf(context.lease().getPrice())) != 0
                    || !StringUtils.defaultIfBlank(request.currency(), context.lease().getCurrency())
                    .equalsIgnoreCase(context.lease().getCurrency())) {
                throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA_CONSTRAINT);
            }
        }
        if (type.isTenancyAgreement()) {
            if (!documentRepo.existsByLeaseIdAndDocumentTypeAndStatusAndActiveTrue(
                    leaseId, LeaseDocumentType.RENTAL_LETTER_OF_OFFER, LeaseDocumentStatus.SIGNED)
                    || request.effectiveDate() == null
                    || !request.effectiveDate().equals(context.lease().getMoveInDate())
                    || request.amount() == null
                    || request.amount().compareTo(java.math.BigDecimal.valueOf(context.lease().getPrice())) != 0
                    || !StringUtils.defaultIfBlank(request.currency(), context.lease().getCurrency())
                    .equalsIgnoreCase(context.lease().getCurrency())) {
                throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
            }
        }
    }

    private void validateTemplate(String body) {
        String lower = body.toLowerCase();
        if (lower.contains("<script") || lower.contains("javascript:") || lower.contains("http://")
                || lower.contains("https://") || lower.contains("{{{")) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        }
    }

    private record Context(Property property, Unit unit, Users issuer, Users recipient, Lease lease) {}
}
