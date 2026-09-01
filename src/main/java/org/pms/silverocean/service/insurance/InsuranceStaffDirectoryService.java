package org.pms.silverocean.service.insurance;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.UserRepo;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InsuranceStaffDirectoryService {
    private static final Set<String> INSURANCE_ROLES = Set.of(
            PMSRole.INSURANCE_ADVISER.getName(),
            PMSRole.INSURANCE_MANAGER.getName());

    private final UserRepo userRepo;

    public List<InsuranceModels.StaffView> activeStaff() {
        return userRepo.findActiveInsuranceStaff(INSURANCE_ROLES).stream()
                .map(row -> new InsuranceModels.StaffView(
                        row.getId(), row.getFullName(), row.getEmail(), row.getRoleName()))
                .toList();
    }

    public boolean isEligible(long userId) {
        return userRepo.countActiveInsuranceStaff(userId, INSURANCE_ROLES) > 0;
    }
}
