package org.pms.silverocean.service.kyc;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.KycMatrixReleaseRepo;
import org.pms.silverocean.database.pms.KycMatrixRequirementRepo;
import org.pms.silverocean.database.pms.ServiceCategoryRepo;
import org.pms.silverocean.database.pms.entities.KycMatrixRelease;
import org.pms.silverocean.database.pms.entities.KycMatrixRequirement;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.sp.enums.DocumentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KycMatrixService {
    private final KycMatrixReleaseRepo releases;
    private final KycMatrixRequirementRepo requirements;
    private final UserDao users;
    private final AuditLogService audit;
    private final ServiceCategoryRepo serviceCategories;

    public record MatrixView(KycMatrixRelease release, List<KycMatrixRequirement> requirements) {}
    public record Preview(MatrixView published, MatrixView draft, int added, int changed, int deactivated) {}

    public MatrixView draft() { return view(requiredRelease("DRAFT")); }
    public MatrixView published() { return view(requiredRelease("PUBLISHED")); }
    public List<KycMatrixRelease> history() { return releases.findAllByOrderByVersionNoDesc(); }

    public Preview preview() {
        MatrixView live = published(), draft = draft();
        var oldByKey = live.requirements().stream().collect(Collectors.toMap(this::key, r -> r));
        int added = 0, changed = 0, deactivated = 0;
        for (KycMatrixRequirement row : draft.requirements()) {
            KycMatrixRequirement old = oldByKey.get(key(row));
            if (old == null && row.isActive()) added++;
            else if (old != null && old.isActive() && !row.isActive()) deactivated++;
            else if (old != null && !signature(old).equals(signature(row))) changed++;
        }
        return new Preview(live, draft, added, changed, deactivated);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public KycMatrixRequirement add(KycMatrixRequests.RequirementUpsert request) {
        KycMatrixRequirement row = new KycMatrixRequirement();
        row.setReleaseId(requiredRelease("DRAFT").getId());
        row.setCreatedBy(users.getUserId());
        apply(row, request);
        row = requirements.save(row);
        audit.createAuditLog(row, "KYC_MATRIX_DRAFT_ADD");
        return row;
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public KycMatrixRequirement update(long id, KycMatrixRequests.RequirementUpsert request) {
        KycMatrixRelease draft = requiredRelease("DRAFT");
        KycMatrixRequirement row = requirements.findByIdAndReleaseId(id, draft.getId()).orElseThrow(this::notFound);
        apply(row, request);
        row = requirements.save(row);
        audit.createAuditLog(row, "KYC_MATRIX_DRAFT_UPDATE");
        return row;
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public KycMatrixRequirement deactivate(long id) {
        KycMatrixRelease draft = requiredRelease("DRAFT");
        KycMatrixRequirement row = requirements.findByIdAndReleaseId(id, draft.getId()).orElseThrow(this::notFound);
        row.setActive(false);
        row = requirements.save(row);
        audit.createAuditLog(row, "KYC_MATRIX_DRAFT_DEACTIVATE");
        return row;
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public MatrixView publish(KycMatrixRequests.Publish request) {
        KycMatrixRelease draft = requiredRelease("DRAFT");
        KycMatrixRelease live = requiredRelease("PUBLISHED");
        if (requirements.findAllByReleaseIdOrderByScopeTypeAscScopeLabelAscRequirementLabelAsc(draft.getId()).stream()
                .filter(KycMatrixRequirement::isActive).noneMatch(r -> "MANDATORY".equals(r.getObligation()))) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA, "The KYC matrix must retain at least one mandatory requirement.");
        }
        Long actor = users.getUserId();
        syncServiceCategories(draft, actor);
        live.setStatus("SUPERSEDED"); live.setActive(false); releases.save(live);
        draft.setStatus("PUBLISHED"); draft.setChangeSummary(request.changeSummary().trim());
        draft.setPublishedAt(ZonedDateTime.now(ZoneId.of("UTC"))); draft.setPublishedBy(actor); releases.save(draft);
        audit.createAuditLog(draft, "KYC_MATRIX_PUBLISH", request.changeSummary(), true);

        KycMatrixRelease next = new KycMatrixRelease();
        next.setVersionNo(draft.getVersionNo() + 1); next.setStatus("DRAFT"); next.setActive(true);
        next.setCreatedBy(actor); next.setChangeSummary("Working copy of version " + draft.getVersionNo() + ".");
        next = releases.save(next);
        for (KycMatrixRequirement source : requirements.findAllByReleaseIdOrderByScopeTypeAscScopeLabelAscRequirementLabelAsc(draft.getId())) {
            KycMatrixRequirement copy = copy(source, next.getId(), actor); requirements.save(copy);
        }
        return view(draft);
    }

    public List<KycMatrixRequirement> activeFor(String scopeType, String scopeKey) {
        KycMatrixRelease live = requiredRelease("PUBLISHED");
        return requirements.findAllByReleaseIdAndActiveTrueAndScopeTypeAndScopeKeyOrderByRequirementLabel(
                live.getId(), cleanScopeType(scopeType), cleanKey(scopeKey));
    }

    private MatrixView view(KycMatrixRelease release) {
        return new MatrixView(release, requirements.findAllByReleaseIdOrderByScopeTypeAscScopeLabelAscRequirementLabelAsc(release.getId()));
    }
    private KycMatrixRelease requiredRelease(String status) { return releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc(status).orElseThrow(this::notFound); }
    private void apply(KycMatrixRequirement row, KycMatrixRequests.RequirementUpsert request) {
        row.setScopeType(cleanScopeType(request.scopeType())); row.setScopeKey(cleanKey(request.scopeKey()));
        row.setScopeLabel(request.scopeLabel().trim()); row.setRequirementCode(request.requirementCode().trim().toUpperCase(Locale.ROOT));
        row.setRequirementLabel(request.requirementLabel().trim()); row.setObligation(request.obligation()); row.setProfileScope(request.profileScope());
        row.setAcceptedDocumentTypes(validateTypes(request.acceptedDocumentTypes()));
        row.setConditionDescription(StringUtils.trimToNull(request.conditionDescription())); row.setValidityDays(request.validityDays());
        row.setRenewalLeadDays(request.renewalLeadDays()); row.setActive(request.active());
        if ("CONDITIONAL".equals(row.getObligation()) && StringUtils.isBlank(row.getConditionDescription()))
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA, "Conditional requirements need a clear condition.");
        if (row.getValidityDays() != null && row.getRenewalLeadDays() != null && row.getRenewalLeadDays() >= row.getValidityDays())
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA, "Renewal notice must be shorter than the validity period.");
    }
    private String validateTypes(String raw) {
        Set<String> allowed = Arrays.stream(KycDocumentType.values()).map(Enum::name).collect(Collectors.toSet());
        allowed.addAll(Arrays.stream(DocumentType.values()).map(Enum::name).toList());
        List<String> types = Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isBlank()).map(s -> s.toUpperCase(Locale.ROOT)).distinct().toList();
        if (types.isEmpty() || !allowed.containsAll(types)) throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA, "Choose supported KYC document types.");
        return String.join(",", types);
    }
    private String cleanScopeType(String value) { String result = value.trim().toUpperCase(Locale.ROOT); if (!Set.of("COMMON","PROVIDER_TYPE","SERVICE_CATEGORY","SOKO_CATEGORY").contains(result)) throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA); return result; }
    private String cleanKey(String value) { String result = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_"); if (result.isBlank() || result.length() > 120) throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA); return result; }
    private String key(KycMatrixRequirement r) { return r.getScopeType() + "|" + r.getScopeKey() + "|" + r.getRequirementCode(); }
    private String signature(KycMatrixRequirement r) { return String.join("|", r.getRequirementLabel(), r.getObligation(), r.getProfileScope(), r.getAcceptedDocumentTypes(), StringUtils.defaultString(r.getConditionDescription()), String.valueOf(r.getValidityDays()), String.valueOf(r.getRenewalLeadDays()), String.valueOf(r.isActive())); }
    private KycMatrixRequirement copy(KycMatrixRequirement s, long releaseId, Long actor) { KycMatrixRequirement r = new KycMatrixRequirement(); r.setReleaseId(releaseId); r.setCreatedBy(actor); r.setScopeType(s.getScopeType()); r.setScopeKey(s.getScopeKey()); r.setScopeLabel(s.getScopeLabel()); r.setRequirementCode(s.getRequirementCode()); r.setRequirementLabel(s.getRequirementLabel()); r.setObligation(s.getObligation()); r.setProfileScope(s.getProfileScope()); r.setAcceptedDocumentTypes(s.getAcceptedDocumentTypes()); r.setConditionDescription(s.getConditionDescription()); r.setValidityDays(s.getValidityDays()); r.setRenewalLeadDays(s.getRenewalLeadDays()); r.setActive(s.isActive()); return r; }
    private void syncServiceCategories(KycMatrixRelease draft,Long actor){
        var rows=requirements.findAllByReleaseIdOrderByScopeTypeAscScopeLabelAscRequirementLabelAsc(draft.getId()).stream().filter(r->"SERVICE_CATEGORY".equals(r.getScopeType())).toList();
        for(var group:rows.stream().collect(Collectors.groupingBy(KycMatrixRequirement::getScopeKey)).values()){
            KycMatrixRequirement first=group.get(0);
            var existing=serviceCategories.findByNameAndActive(first.getScopeLabel(),true);
            if(existing.isEmpty()&&first.getScopeKey().matches("\\d+"))existing=serviceCategories.findById(Long.parseLong(first.getScopeKey()));
            var category=existing.orElseGet(org.pms.silverocean.database.pms.entities.ServiceCategory::new);
            category.setName(first.getScopeLabel());if(category.getDescription()==null)category.setDescription("Configured through the KYC requirements matrix.");category.setActive(group.stream().anyMatch(KycMatrixRequirement::isActive));if(category.getCreatedBy()==null)category.setCreatedBy(actor);
            Set<DocumentType> docs=group.stream().filter(KycMatrixRequirement::isActive).filter(r->"MANDATORY".equals(r.getObligation())).flatMap(r->Arrays.stream(r.getAcceptedDocumentTypes().split(","))).map(String::trim).filter(t->{try{DocumentType.valueOf(t);return true;}catch(Exception ignored){return false;}}).map(DocumentType::valueOf).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            category.setRequiredDocumentTypes(docs);serviceCategories.save(category);
        }
    }
    private PMSCustomException notFound() { return new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND); }
}
