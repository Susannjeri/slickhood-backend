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
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.estate.EstateAccessService;
import org.pms.silverocean.service.sales.SalesAccessService;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.service.lease.LeaseDao;
import org.pms.silverocean.service.lease.LeaseService;
import org.pms.silverocean.service.mustache.RenderService;
import org.pms.silverocean.service.notification.email.EmailService;
import org.pms.silverocean.service.sales.SaleStatus;
import org.pms.silverocean.service.sales.SalesService;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class LeaseDocumentService {
    private static final Pattern HTML_EVENT_HANDLER = Pattern.compile("\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);
    private final LeaseDocumentRepo documentRepo;
    private final LeaseDocumentTemplateRepo templateRepo;
    private final LeaseDao leaseDao;
    private final PropertyRepo propertyRepo;
    private final UnitRepo unitRepo;
    private final UserDao userDao;
    private final RenderService renderService;
    private final EmailService emailService;
    private final LeaseService leaseService;
    private final SaleTransactionRepo saleRepo;
    private final SalesService salesService;
    private final DocumentBrandingService brandingService;
    private final PropertyOwnershipRepo ownershipRepo;
    private final EstateAccessService estateAccess;
    private final SalesAccessService salesAccess;

    public LeaseDocumentService(LeaseDocumentRepo documentRepo, LeaseDocumentTemplateRepo templateRepo,
            LeaseDao leaseDao, PropertyRepo propertyRepo, UnitRepo unitRepo, UserDao userDao,
            RenderService renderService, EmailService emailService, LeaseService leaseService,
            SaleTransactionRepo saleRepo, SalesService salesService, DocumentBrandingService brandingService,
            PropertyOwnershipRepo ownershipRepo, EstateAccessService estateAccess, SalesAccessService salesAccess) {
        this.documentRepo = documentRepo;
        this.templateRepo = templateRepo;
        this.leaseDao = leaseDao;
        this.propertyRepo = propertyRepo;
        this.unitRepo = unitRepo;
        this.userDao = userDao;
        this.renderService = renderService;
        this.emailService = emailService;
        this.leaseService = leaseService;
        this.saleRepo = saleRepo;
        this.salesService = salesService;
        this.brandingService = brandingService;
        this.ownershipRepo = ownershipRepo;
        this.estateAccess = estateAccess;
        this.salesAccess = salesAccess;
    }

    @Transactional
    public LeaseDocumentDTO generate(GenerateLeaseDocumentRequest request) {
        long currentUserId = userDao.getUserId();
        LeaseDocumentType type = request.documentType();
        if (type.isLegacy()) throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        if ((type.requiresLease() && (request.saleId() != null || request.ownershipId() != null))
                || (type.isSaleDocument() && (request.leaseId() != null || request.ownershipId() != null))
                || (type.isEstateDocument() && (request.leaseId() != null || request.saleId() != null))) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        }
        Context context = type.requiresLease() ? leaseContext(request, currentUserId)
                : type.isSaleDocument() ? saleContext(request, currentUserId) : propertyContext(request, currentUserId);
        validateDocumentSequence(request, context);
        LeaseDocumentTemplate template = templateRepo.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(type)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.TEMPLATE_NOT_FOUND));
        if (!DocumentTemplateIntegrity.sha256(template.getBodyHtml()).equals(template.getContentSha256())
                || (!template.isLegalReviewRequired() && template.getLegalReviewedAt() == null)) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }

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
        model.put("hasAdditionalTerms", StringUtils.isNotBlank(request.reason()));
        model.put("legalReviewRequired", template.isLegalReviewRequired());
        model.put("templateVersion", template.getVersion());
        model.put("saleDocument", type.isSaleDocument());
        model.put("generatedDate", LocalDate.now(PMSUtils.getZoneId()).toString());
        model.put("issuerEmail", StringUtils.defaultString(context.issuer().getEmail(), "Not recorded"));
        model.put("issuerPhone", StringUtils.defaultString(context.issuer().getPhoneNumber(), "Not recorded"));
        model.put("issuerIdentification", StringUtils.defaultString(context.issuer().getIdentificationNumber(), "Not recorded"));
        model.put("recipientEmail", StringUtils.defaultString(context.recipient().getEmail(), "Not recorded"));
        model.put("recipientPhone", StringUtils.defaultString(context.recipient().getPhoneNumber(), "Not recorded"));
        model.put("recipientIdentification", StringUtils.defaultString(context.recipient().getIdentificationNumber(), "Not recorded"));
        model.put("propertyType", StringUtils.defaultString(context.property().getType(), "Not recorded"));
        if (context.lease() != null) addLeaseModel(model, context.lease());
        if (context.sale() != null) addSaleModel(model, context.sale());
        Users documentOwner = userDao.findById(context.property().getCreatedBy()).orElse(context.issuer());
        String ownerLogo = brandingService.dataUri(documentOwner.getId());
        model.put("hasOwnerLogo", StringUtils.isNotBlank(ownerLogo));
        model.put("ownerLogoDataUri", ownerLogo);
        model.put("documentOwnerName", StringUtils.defaultIfBlank(documentOwner.getOrganizationName(), documentOwner.getFullName()));

        LeaseDocument document = new LeaseDocument();
        document.setLeaseId(request.leaseId());
        document.setSaleId(request.saleId());
        document.setPropertyId(context.property().getId());
        document.setUnitId(context.unit() == null ? null : context.unit().getId());
        document.setTemplateId(template.getId());
        document.setTemplateVersion(template.getVersion());
        document.setDocumentType(type);
        document.setStatus(LeaseDocumentStatus.DRAFT);
        document.setName(template.getDisplayName());
        String body = template.getBodyHtml();
        if (type.isTenancyAgreement()) {
            // Factual lease schedule is snapshotted with the approved clauses, never read live at download time.
            String schedule = "<section><h2>Recorded lease schedule</h2><p>Pet policy: {{petsPolicy}}</p>"
                    + "<h3>Additional charges</h3>{{#leaseCharges}}<p>{{name}}: {{currency}} {{amount}} ({{period}})</p>{{/leaseCharges}}"
                    + "{{^leaseCharges}}<p>No additional charges recorded.</p>{{/leaseCharges}}</section>";
            body = body.contains("</body>") ? body.replace("</body>", schedule + "</body>") : body + schedule;
        }
        document.setRenderedHtml(renderService.renderInline(body, model));
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

    public Page<LeaseDocumentDTO> list(Pageable pageable) {
        return list(pageable, null, null, null);
    }

    public Page<LeaseDocumentDTO> list(Pageable pageable, Long leaseId, Long saleId, Long propertyId) {
        return list(pageable, leaseId, saleId, propertyId, null);
    }

    public Page<LeaseDocumentDTO> list(Pageable pageable, Long leaseId, Long saleId, Long propertyId, Long unitId) {
        Pageable bounded = PageRequest.of(Math.max(0, pageable.getPageNumber()), Math.min(100, Math.max(1, pageable.getPageSize())), pageable.getSort());
        return documentRepo.findAccessiblePage(userDao.getUserId(), leaseId, saleId, propertyId, unitId, bounded).map(d -> new LeaseDocumentDTO(d, userDao.getUserId()));
    }

    public void renderPdf(long id, ByteArrayOutputStream output) throws IOException {
        LeaseDocument document = accessible(id);
        renderService.toPdf(LeaseDocumentPdf.html(document), output);
    }

    @Transactional
    public LeaseDocumentDTO issue(long id) {
        LeaseDocument document = mutable(id);
        if (document.getIssuerUserId() != userDao.getUserId() || document.getStatus() != LeaseDocumentStatus.DRAFT) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        if (document.isLegalReviewRequired()) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        validateCurrentContext(document);
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
        LeaseDocument document = mutable(id);
        if (document.getRecipientUserId() != userDao.getUserId() || document.getStatus() != LeaseDocumentStatus.ISSUED) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        validateCurrentContext(document);
        document.setAcknowledgedAt(LocalDateTime.now());
        document.setStatus(LeaseDocumentStatus.ACKNOWLEDGED);
        return new LeaseDocumentDTO(documentRepo.save(document));
    }

    @Transactional
    public LeaseDocumentDTO reject(long id, RejectLeaseDocumentRequest request) {
        LeaseDocument document = mutable(id);
        if (document.getRecipientUserId() != userDao.getUserId()
                || (!document.getDocumentType().isTenancyAgreement() && !document.getDocumentType().isEstateDocument())
                || (document.getStatus() != LeaseDocumentStatus.ISSUED
                && document.getStatus() != LeaseDocumentStatus.ACKNOWLEDGED)
                || document.getIssuerSignedAt() != null || document.getRecipientSignedAt() != null) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        validateCurrentContext(document);
        document.setRecipientRejectionReason(request.reason().trim());
        document.setStatus(LeaseDocumentStatus.REJECTED);
        LeaseDocument saved = documentRepo.save(document);
        if (saved.getDocumentType().isTenancyAgreement()) {
            leaseService.rejectGovernedAgreement(saved.getLeaseId(), saved.getRecipientUserId());
        }
        return new LeaseDocumentDTO(saved);
    }

    @Transactional
    public LeaseDocumentDTO sign(long id) {
        LeaseDocument document = mutable(id);
        long userId = userDao.getUserId();
        if (document.getStatus() == LeaseDocumentStatus.SIGNED) return new LeaseDocumentDTO(document);
        if (document.getStatus() != LeaseDocumentStatus.ISSUED && document.getStatus() != LeaseDocumentStatus.ACKNOWLEDGED
                && document.getStatus() != LeaseDocumentStatus.PARTIALLY_SIGNED) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        if (document.isLegalReviewRequired()) throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        validateCurrentContext(document);
        if ((document.getDocumentType().isTenancyAgreement() || document.getDocumentType().isEstateDocument())
                && document.getIssuerUserId() == userId
                && document.getRecipientSignedAt() == null) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        LocalDateTime now = LocalDateTime.now();
        if (document.getIssuerUserId() == userId) {
            if (document.getIssuerSignedAt() != null) return new LeaseDocumentDTO(document);
            document.setIssuerSignedAt(now);
        } else if (document.getRecipientUserId() == userId) {
            if (document.getRecipientSignedAt() != null) return new LeaseDocumentDTO(document);
            document.setRecipientSignedAt(now);
        }
        else throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_NOT_FOUND);
        document.setStatus(document.getIssuerSignedAt() != null && document.getRecipientSignedAt() != null
                ? LeaseDocumentStatus.SIGNED : LeaseDocumentStatus.PARTIALLY_SIGNED);
        LeaseDocument saved = documentRepo.save(document);
        if (saved.getStatus() == LeaseDocumentStatus.PARTIALLY_SIGNED && saved.getDocumentType().isTenancyAgreement()
                && saved.getRecipientSignedAt() != null) {
            leaseService.recordGovernedTenantSignature(saved.getLeaseId(), saved.getRecipientUserId(), saved.getRecipientSignedAt());
        }
        if (saved.getStatus() == LeaseDocumentStatus.SIGNED && saved.getDocumentType().isTenancyAgreement()) {
            leaseService.activateFromGovernedAgreement(saved.getLeaseId(), saved.getIssuerUserId(), saved.getRecipientUserId(),
                    saved.getIssuerSignedAt(), saved.getRecipientSignedAt());
        }
        if (saved.getStatus() == LeaseDocumentStatus.SIGNED
                && saved.getDocumentType() == LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER) {
            salesService.acceptSignedOffer(saved.getSaleId(), saved.getId(), saved.getAmount());
        }
        return new LeaseDocumentDTO(saved);
    }

    @Transactional
    public LeaseDocumentDTO cancelDraft(long id) {
        LeaseDocument document = mutable(id);
        if (document.getIssuerUserId() != userDao.getUserId() || document.getStatus() != LeaseDocumentStatus.DRAFT)
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        document.setStatus(LeaseDocumentStatus.CANCELLED);
        return new LeaseDocumentDTO(documentRepo.save(document));
    }

    private LeaseDocument mutable(long id) {
        return documentRepo.findAccessibleForUpdate(id, userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_DOCUMENT_NOT_FOUND));
    }

    private void validateCurrentContext(LeaseDocument document) {
        if (document.getIssuerUserId() == document.getRecipientUserId())
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        if (document.getDocumentType().isSaleDocument()) {
            SaleTransaction sale = saleRepo.findByIdForUpdate(document.getSaleId())
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.SALE_NOT_FOUND));
            if (!java.util.Objects.equals(sale.getBuyerUserId(), document.getRecipientUserId())
                    || !java.util.Objects.equals(sale.getUnitId(), document.getUnitId()))
                throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
            if (document.getDocumentType() == LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER
                    && (document.getResponseDueDate() == null || document.getResponseDueDate().isBefore(LocalDate.now(PMSUtils.getZoneId()))
                    || sale.getStatus() != SaleStatus.OFFERED))
                throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
            if (document.getDocumentType() == LeaseDocumentType.PROPERTY_SALE_AGREEMENT && sale.getStatus() != SaleStatus.AGREEMENT)
                throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
            if (document.getAmount() == null || sale.getOfferAmount() == null || document.getAmount().compareTo(sale.getOfferAmount()) != 0
                    || !StringUtils.equalsIgnoreCase(document.getCurrency(), sale.getCurrency()))
                throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
            if (userDao.getUserId() == document.getIssuerUserId()) salesAccess.require(sale.getPropertyId(), Permission.CREATE_LEASE_DOCUMENT);
        } else if (document.getDocumentType().isTenancyAgreement()) {
            Lease lease = leaseDao.getLeaseForUpdate(document.getLeaseId())
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
            leaseService.checkDocumentLeaseAccess(lease);
            if (lease.isSigned() || !java.util.Objects.equals(lease.getMoveInDate(), document.getEffectiveDate())
                    || document.getAmount() == null || document.getAmount().compareTo(java.math.BigDecimal.valueOf(lease.getPrice())) != 0
                    || !StringUtils.equalsIgnoreCase(lease.getCurrency(), document.getCurrency()))
                throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        } else if (document.getDocumentType().isEstateDocument()) {
            PropertyOwnership current = ownershipRepo.findAllByPropertyIdAndActiveTrue(document.getPropertyId()).stream()
                    .filter(o -> o.getHomeownerUserId() == document.getRecipientUserId()
                            && java.util.Objects.equals(o.getUnitId(), document.getUnitId()))
                    .findFirst().flatMap(o -> ownershipRepo.findActiveForUpdate(o.getId()))
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.OWNERSHIP_NOT_FOUND));
            if (document.getEffectiveDate() == null || current.getOwnershipStart() == null
                    || document.getEffectiveDate().isBefore(current.getOwnershipStart())
                    || document.getCreatedOn() == null || current.getCreatedOn() == null
                    || document.getCreatedOn().isBefore(current.getCreatedOn()))
                throw new PMSCustomException(ResponseCode.OWNERSHIP_NOT_FOUND);
            // Ownership alone must not authorize an agreement for retired inventory.
            // The unit purpose, not the shared property's default category, governs this workflow.
            propertyRepo.findById(document.getPropertyId()).filter(org.pms.silverocean.database.pms.entities.Property::isActive)
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
            if (document.getUnitId() != null) unitRepo.findById(document.getUnitId())
                    .filter(u -> u.isActive() && u.getPropertyId() == document.getPropertyId()
                            && "SERVICE_CHARGE".equals(u.getLeaseMode()))
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
            if (userDao.getUserId() == document.getIssuerUserId()) estateAccess.require(document.getPropertyId(), Permission.CREATE_LEASE_DOCUMENT);
        }
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
        template.setContentSha256(DocumentTemplateIntegrity.sha256(request.bodyHtml()));
        template.setLegalReviewRequired(request.legalReviewRequired());
        if (!request.legalReviewRequired()) {
            template.setLegalReviewedAt(LocalDateTime.now());
            template.setLegalReviewedBy(userDao.getUserId());
        }
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
        lease = leaseDao.getLeaseForUpdate(lease.getId()).orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        leaseService.checkDocumentLeaseAccess(lease);
        UnitTenant tenancy = leaseDao.getUnitTenantByTenantId(lease.getTenantId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        Unit unit = unitRepo.findById(tenancy.getUnitId()).filter(Unit::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        Property property = propertyRepo.findById(unit.getPropertyId()).filter(Property::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        Users tenant = userDao.findById(tenancy.getUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        Users owner = userDao.findById(property.getCreatedBy()).orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        return request.documentType().isTenantInitiated()
                ? new Context(property, unit, tenant, owner, lease, null)
                : new Context(property, unit, userDao.getUserObject(), tenant, lease, null);
    }

    private Context propertyContext(GenerateLeaseDocumentRequest request, long userId) {
        if (request.propertyId() == null || request.recipientUserId() == null) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        }
        Property property = estateAccess.require(request.propertyId(), Permission.CREATE_LEASE_DOCUMENT);
        Users recipient = userDao.findById(request.recipientUserId())
                .filter(Users::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        List<PropertyOwnership> matches = ownershipRepo.findAllByPropertyIdAndActiveTrue(property.getId()).stream()
                .filter(o -> o.getHomeownerUserId() == recipient.getId())
                .filter(o -> request.ownershipId() == null || o.getId().equals(request.ownershipId())).toList();
        if (matches.size() != 1 || request.effectiveDate() == null || recipient.getId() == userId)
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        PropertyOwnership ownership = ownershipRepo.findActiveForUpdate(matches.getFirst().getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.OWNERSHIP_NOT_FOUND));
        if (request.effectiveDate().isBefore(ownership.getOwnershipStart())
                || documentRepo.existsCurrentEstateAgreement(property.getId(), ownership.getUnitId(), recipient.getId(), ownership.getOwnershipStart(), ownership.getCreatedOn()))
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        Unit unit = ownership.getUnitId() == null ? null : unitRepo.findById(ownership.getUnitId())
                .filter(u -> u.isActive() && u.getPropertyId() == property.getId() && "SERVICE_CHARGE".equals(u.getLeaseMode()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        return new Context(property, unit, userDao.getUserObject(), recipient, null, null);
    }

    private Context saleContext(GenerateLeaseDocumentRequest request, long userId) {
        if (request.saleId() == null) throw new PMSCustomException(ResponseCode.SALE_NOT_FOUND);
        PMSRole role = userDao.getActiveRole();
        SaleTransaction sale = saleRepo.findByIdForUpdate(request.saleId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SALE_NOT_FOUND));
        if (role == PMSRole.BUYER) throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        salesAccess.require(sale.getPropertyId(), Permission.CREATE_LEASE_DOCUMENT);
        if (sale.getBuyerUserId() == null) throw new PMSCustomException(ResponseCode.LOAD_USER_ERROR);
        Property property = propertyRepo.findById(sale.getPropertyId()).filter(Property::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        Unit unit = unitRepo.findById(sale.getUnitId()).filter(Unit::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        Users buyer = userDao.findById(sale.getBuyerUserId()).filter(Users::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        return new Context(property, unit, userDao.getUserObject(), buyer, null, sale);
    }

    private void validateDocumentSequence(GenerateLeaseDocumentRequest request, Context context) {
        if (context.sale() != null) {
            validateSaleDocument(request, context.sale());
            return;
        }
        if (context.lease() == null) return;
        LeaseDocumentType type = request.documentType();
        long leaseId = context.lease().getId();
        if (!"RENT".equals(context.lease().getLeaseMode())) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        if (documentRepo.existsOpen(leaseId, type)) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        if (type.isTenancyAgreement()) {
            if (context.lease().isSigned() || documentRepo.existsCurrentAgreement(leaseId) || request.effectiveDate() == null
                    || !request.effectiveDate().equals(context.lease().getMoveInDate())
                    || request.amount() == null
                    || request.amount().compareTo(java.math.BigDecimal.valueOf(context.lease().getPrice())) != 0
                    || !StringUtils.defaultIfBlank(request.currency(), context.lease().getCurrency())
                    .equalsIgnoreCase(context.lease().getCurrency())) {
                throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
            }
        }
    }

    private void validateSaleDocument(GenerateLeaseDocumentRequest request, SaleTransaction sale) {
        LeaseDocumentType type = request.documentType();
        documentRepo.expireSaleOffers(sale.getId(), LocalDate.now(PMSUtils.getZoneId()));
        if (documentRepo.existsOpenForSale(sale.getId(), type)) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        if (type == LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER) {
            if (sale.getStatus() != SaleStatus.OFFERED || sale.getOfferAmount() == null
                    || request.responseDueDate() == null || !request.responseDueDate().isAfter(LocalDate.now(PMSUtils.getZoneId()))
                    || request.amount() == null || request.amount().compareTo(sale.getOfferAmount()) != 0
                    || !StringUtils.defaultIfBlank(request.currency(), sale.getCurrency()).equalsIgnoreCase(sale.getCurrency())) {
                throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA_CONSTRAINT);
            }
        } else if (type == LeaseDocumentType.PROPERTY_SALE_AGREEMENT
                && (sale.getStatus() != SaleStatus.AGREEMENT || request.effectiveDate() == null
                || request.amount() == null || sale.getOfferAmount() == null || request.amount().compareTo(sale.getOfferAmount()) != 0
                || !StringUtils.defaultIfBlank(request.currency(), sale.getCurrency()).equalsIgnoreCase(sale.getCurrency())
                || !documentRepo.existsBySaleIdAndDocumentTypeAndStatusAndActiveTrue(sale.getId(),
                    LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER, LeaseDocumentStatus.SIGNED))) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
    }

    private void validateTemplate(String body) {
        String lower = body.toLowerCase();
        if (lower.contains("<script") || lower.contains("javascript:") || lower.contains("http://")
                || lower.contains("https://") || lower.contains("file:") || lower.contains("ftp:")
                || lower.contains("<iframe") || lower.contains("<object") || lower.contains("<embed")
                || lower.contains("<link") || lower.contains("@import") || lower.contains("url(")
                || lower.contains("srcdoc") || lower.contains("data:text/html") || lower.contains("{{{")
                || HTML_EVENT_HANDLER.matcher(body).find()) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        }
    }

    private void addLeaseModel(Map<String, Object> model, Lease lease) {
        model.put("leaseDate", value(lease.getLeaseDate()));
        model.put("moveInDate", value(lease.getMoveInDate()));
        model.put("moveOutDate", value(lease.getMoveOutDate()));
        model.put("rentDueDay", value(lease.getRentDueDayOfMonth()));
        model.put("leaseDurationMonths", value(lease.getLeaseDurationInMonths()));
        model.put("noticePeriodMonths", value(lease.getNoticePeriodInMonths()));
        model.put("depositReturnDays", value(lease.getDepositReturnDays()));
        model.put("repairThreshold", value(lease.getRepairThreshold()));
        model.put("entryNoticeDays", value(lease.getEntryNoticeDays()));
        model.put("selfRenewing", lease.isSelfRenew());
        model.put("petsPolicy", lease.getPetsPolicy() == null ? "Not recorded" : new String(lease.getPetsPolicy(), java.nio.charset.StandardCharsets.UTF_8));
        model.put("leaseCharges", leaseDao.getLeaseChargeByLeaseId(lease.getId()).stream().map(charge -> Map.of(
                "name", StringUtils.defaultIfBlank(charge.getChargeName(), "Charge " + charge.getChargeId()),
                "amount", java.math.BigDecimal.valueOf(charge.getAmount()).toPlainString(),
                "period", StringUtils.defaultIfBlank(charge.getPeriodId(), "Not recorded"))).toList());
    }

    private void addSaleModel(Map<String, Object> model, SaleTransaction sale) {
        model.put("askingPrice", sale.getAskingPrice() == null ? "Not recorded" : sale.getAskingPrice().toPlainString());
        model.put("offerAmount", sale.getOfferAmount() == null ? "Not recorded" : sale.getOfferAmount().toPlainString());
    }

    private String value(Object value) {
        return value == null ? "Not recorded" : value.toString();
    }

    private record Context(Property property, Unit unit, Users issuer, Users recipient, Lease lease, SaleTransaction sale) {}
}
