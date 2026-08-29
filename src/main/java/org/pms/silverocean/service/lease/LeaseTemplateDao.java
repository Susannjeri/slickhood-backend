package org.pms.silverocean.service.lease;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.LeaseTemplateRepo;
import org.pms.silverocean.database.pms.entities.LeaseTemplate;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.pms.silverocean.service.lease.LeaseService.DEFAULT_PREFIX;

@Service
public class LeaseTemplateDao {
    private final LeaseTemplateRepo leaseTemplateRepo;
    private final AuditLogService auditLogService;

    public LeaseTemplateDao(LeaseTemplateRepo leaseTemplateRepo, AuditLogService auditLogService) {
        this.leaseTemplateRepo = leaseTemplateRepo;
        this.auditLogService = auditLogService;
    }

    public Page<LeaseTemplate> getLeaseTemplatesByCreatedBy(Pageable pageable, long userId) {
        return leaseTemplateRepo.findLeaseTemplateByCreatedByAndActiveTrue(pageable, userId,
                DEFAULT_PREFIX + PMSLeaseMode.RENT.name(), DEFAULT_PREFIX + PMSLeaseMode.SALE.name());
    }

    public Page<LeaseTemplate> getLeaseTemplatesByCreatedByAndLeaseMode(Pageable pageable, long userId, PMSLeaseMode leaseMode) {
        return leaseTemplateRepo.findLeaseTemplateByCreatedByAndActiveTrueAndLeaseMode(pageable, userId,
                DEFAULT_PREFIX + PMSLeaseMode.RENT.name(), DEFAULT_PREFIX + PMSLeaseMode.SALE.name(), leaseMode.name());
    }

    public void saveTemplate(LeaseTemplate leaseTemplate, String action) {
        if (leaseTemplate.getName().equals(DEFAULT_PREFIX + PMSLeaseMode.SALE.name()) || leaseTemplate.getName().equals(DEFAULT_PREFIX + PMSLeaseMode.RENT.name())) {
            throw new PMSCustomException(ResponseCode.INVALID_TEMPLATE_NAME);
        }
        leaseTemplateRepo.save(leaseTemplate);
        auditLogService.createAuditLog(leaseTemplate, action);
    }

    public Optional<LeaseTemplate> getTemplateById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return leaseTemplateRepo.findByIdAndActiveTrue(id);
    }

    public Optional<LeaseTemplate> getTemplateByIdAndCreatedBy(long id, long createdBy) {
        return leaseTemplateRepo.findByIdAndCreatedByAndActiveTrue(id, createdBy);
    }

    public Optional<LeaseTemplate> findByIdAndStaffOrOwner(long templateId, long userId) {
        return leaseTemplateRepo.findByIdAndStaffOrOwner(templateId, userId);
    }

    public Optional<LeaseTemplate> getTemplateByName(String name) {
        return leaseTemplateRepo.findByNameAndActiveTrue(name);
    }


    public Optional<LeaseTemplate> getTemplateByNameAndCreatedBy(String name, long userId) {
        return leaseTemplateRepo.findByNameAndCreatedByAndActiveTrue(name, userId);
    }


    public void createDefault(PMSLeaseMode mode) {
        LeaseTemplate template = new LeaseTemplate();

        template.setName(DEFAULT_PREFIX + mode.name());
        template.setLeaseMode(mode.name());

        Map<String, Object> defaults = mode.defaultTemplateData();

        template.setSelfRenew(Boolean.TRUE.equals(defaults.get("selfRenewable")));
        if (PMSLeaseMode.RENT.equals(mode)) {
            template.setLeaseDurationInMonths((int) defaults.getOrDefault("lease_duration", 0));
            template.setNoticePeriodInMonths((int) defaults.getOrDefault("notice_period", 0));
            template.setDepositReturnDays((int) defaults.getOrDefault("deposit_return_days", 0));
            template.setRentDueDayOfMonth((int) defaults.getOrDefault("rent_due_date", 1));
            template.setEntryNoticeDays((int) defaults.getOrDefault("entry_notice_days", 24));
            template.setRepairThreshold((double) defaults.get("repair_threshold"));
        }


        template.setPetsPolicy(defaults.get("pets_policy").toString().getBytes(StandardCharsets.UTF_8));

        template.setActive(true);

        leaseTemplateRepo.save(template);
    }
}
