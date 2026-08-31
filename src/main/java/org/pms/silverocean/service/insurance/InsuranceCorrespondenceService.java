package org.pms.silverocean.service.insurance;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.InsuranceCompanyRepo;
import org.pms.silverocean.database.pms.InsuranceEmailExchangeRepo;
import org.pms.silverocean.database.pms.InsuranceCaseRepo;
import org.pms.silverocean.database.pms.InsuranceClaimRepo;
import org.pms.silverocean.database.pms.InsurancePolicyRepo;
import org.pms.silverocean.database.pms.entities.InsuranceCompany;
import org.pms.silverocean.database.pms.entities.InsuranceEmailExchange;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.pms.silverocean.service.insurance.InsuranceModels.*;

@Service
@RequiredArgsConstructor
public class InsuranceCorrespondenceService {
    private final InsuranceCompanyRepo companyRepo;
    private final InsuranceEmailExchangeRepo exchangeRepo;
    private final InsuranceCaseRepo caseRepo;
    private final InsuranceClaimRepo claimRepo;
    private final InsurancePolicyRepo policyRepo;
    private final EncryptionService encryptionService;
    private final InsuranceEmailSender emailSender;
    private final DomainEventOutboxPublisher outboxPublisher;
    private final UserDao userDao;
    @Value("${app.insurance.mail.from:${INSURANCE_MAIL_FROM:info@silverwoodinsurance.com}}") private String senderAddress;

    @Transactional("pmsDBTransactionManager")
    public EmailExchangeView queue(InsurerEmailRequest request) {
        InsuranceCompany company = companyRepo.findByCodeIgnoreCaseAndActiveTrue(request.companyCode())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
        String recipient = recipient(company, request.messageType());
        if (recipient == null || recipient.isBlank()) throw new PMSCustomException(ResponseCode.INVALID_EMAIL);

        String caseReference = normalizeReference(request.caseReference());
        validateReference(caseReference, request.messageType());
        String correlationId = UUID.randomUUID().toString();
        String subject = "[SILVERWOOD " + caseReference + "] " + request.subject().trim();
        String body = request.body() + "<hr><p><strong>Silverwood reference:</strong> " + caseReference +
                "<br><strong>Correlation ID:</strong> " + correlationId + "</p>";

        InsuranceEmailExchange exchange = new InsuranceEmailExchange();
        exchange.setCompanyId(company.getId()); exchange.setCaseReference(caseReference);
        exchange.setCorrelationId(correlationId); exchange.setMessageType(request.messageType());
        exchange.setDirection("OUTBOUND"); exchange.setStatus("QUEUED");
        exchange.setSenderAddress(senderAddress); exchange.setRecipientAddress(recipient);
        exchange.setSubject(subject); exchange.setEncryptedBody(encryptionService.encrypt(body));
        exchange.setBodyHash(hash(body)); exchange.setCreatedBy(userDao.getUserId()); exchange.setActive(true);
        exchange = exchangeRepo.save(exchange);
        outboxPublisher.publish(InsuranceEmailRequestedEvent.TYPE, "INSURANCE_EMAIL", exchange.getId().toString(),
                "insurance-email:" + exchange.getId(), new InsuranceEmailRequestedEvent(exchange.getId()));
        return view(company, exchange);
    }

    public void sendQueued(long exchangeId) throws Exception {
        InsuranceEmailExchange exchange = exchangeRepo.findById(exchangeId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
        if ("SENT".equals(exchange.getStatus())) return;
        try {
            String body = encryptionService.decrypt(exchange.getEncryptedBody()).decryptedValue();
            exchange.setExternalMessageId(emailSender.send(exchange.getRecipientAddress(), exchange.getSubject(), body));
            exchange.setStatus("SENT"); exchange.setSentAt(LocalDateTime.now()); exchange.setLastError(null);
            exchangeRepo.save(exchange);
        } catch (Exception error) {
            exchange.setStatus("FAILED"); exchange.setLastError(limit(error.getMessage(), 1000)); exchangeRepo.save(exchange);
            throw error;
        }
    }

    @Transactional("pmsDBTransactionManager")
    public EmailExchangeView recordResponse(InsurerEmailResponse response) {
        return recordResponse(response, false);
    }

    @Transactional("pmsDBTransactionManager")
    public EmailExchangeView recordMailboxResponse(InsurerEmailResponse response) {
        return recordResponse(response, true);
    }

    private EmailExchangeView recordResponse(InsurerEmailResponse response, boolean trustedMailbox) {
        InsuranceEmailExchange outbound = exchangeRepo.findByCorrelationId(response.correlationId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
        InsuranceCompany company = companyRepo.findById(outbound.getCompanyId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
        InsuranceEmailExchange inbound = new InsuranceEmailExchange();
        inbound.setCompanyId(company.getId()); inbound.setCaseReference(outbound.getCaseReference());
        inbound.setCorrelationId(UUID.randomUUID().toString()); inbound.setInReplyTo(outbound.getCorrelationId());
        String externalId = trimToNull(response.externalMessageId());
        if (externalId != null && exchangeRepo.existsByCompanyIdAndExternalMessageId(company.getId(), externalId)) return view(company, outbound);
        boolean senderMatches = response.fromAddress().trim().equalsIgnoreCase(outbound.getRecipientAddress());
        inbound.setMessageType(outbound.getMessageType()); inbound.setDirection("INBOUND"); inbound.setStatus(trustedMailbox && senderMatches ? "RECEIVED_VERIFIED" : "RECEIVED_UNVERIFIED");
        inbound.setSenderAddress(response.fromAddress().trim()); inbound.setRecipientAddress(senderAddress);
        inbound.setSubject(response.subject().trim()); inbound.setEncryptedBody(encryptionService.encrypt(response.body()));
        inbound.setBodyHash(hash(response.body())); inbound.setExternalMessageId(externalId);
        inbound.setReceivedAt(LocalDateTime.now()); inbound.setCreatedBy(userDao.getUserId()); inbound.setActive(true);
        return view(company, exchangeRepo.save(inbound));
    }

    public List<EmailExchangeView> history(String caseReference) {
        return exchangeRepo.findByCaseReferenceOrderByCreatedOnAsc(normalizeReference(caseReference)).stream().map(exchange -> {
            InsuranceCompany company = companyRepo.findById(exchange.getCompanyId())
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));
            return view(company, exchange);
        }).toList();
    }

    private String recipient(InsuranceCompany company, String messageType) {
        return switch (messageType) {
            case "QUOTATION_REQUEST" -> company.getQuotationEmail();
            case "CLAIM_NOTIFICATION" -> company.getClaimsEmail();
            case "RENEWAL_REQUEST" -> company.getRenewalsEmail();
            default -> throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        };
    }

    private void validateReference(String reference, String messageType) {
        boolean valid = switch (messageType) {
            case "QUOTATION_REQUEST" -> caseRepo.existsByReferenceAndActiveTrue(reference);
            case "CLAIM_NOTIFICATION" -> claimRepo.existsByReferenceAndActiveTrue(reference);
            case "RENEWAL_REQUEST" -> policyRepo.existsByPolicyNumberAndActiveTrue(reference);
            default -> false;
        };
        if (!valid) throw new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND);
    }

    private EmailExchangeView view(InsuranceCompany company, InsuranceEmailExchange e) {
        return new EmailExchangeView(e.getId(), company.getCode(), company.getName(), e.getCaseReference(),
                e.getCorrelationId(), e.getMessageType(), e.getDirection(), e.getStatus(), e.getSenderAddress(),
                e.getRecipientAddress(), e.getSubject(), e.getBodyHash(), e.getExternalMessageId(), e.getInReplyTo(),
                e.getSentAt(), e.getReceivedAt(), e.getLastError());
    }

    private String normalizeReference(String value) {
        String normalized = value.trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "-");
        if (normalized.isBlank() || normalized.length() > 80) throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        return normalized;
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Unable to hash insurance correspondence", e); }
    }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String limit(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }
}
