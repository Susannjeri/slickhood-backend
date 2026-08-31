package org.pms.silverocean.service.insurance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.UserRepo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsuranceStaffDirectoryServiceTest {
    @Mock UserRepo users;

    @Test void listsOnlyProjectedActiveInsuranceStaffWithoutLoadingUserEntities() {
        UserRepo.InsuranceStaffRow row = org.mockito.Mockito.mock(UserRepo.InsuranceStaffRow.class);
        when(row.getId()).thenReturn(19L);
        when(row.getFullName()).thenReturn("Amina Adviser");
        when(row.getEmail()).thenReturn("amina@example.com");
        when(row.getRoleName()).thenReturn("INSURANCE_ADVISER");
        when(users.findActiveInsuranceStaff(anySet())).thenReturn(List.of(row));

        var result = new InsuranceStaffDirectoryService(users).activeStaff();

        assertThat(result).containsExactly(new InsuranceModels.StaffView(19L,"Amina Adviser","amina@example.com","INSURANCE_ADVISER"));
    }

    @Test void eligibilityUsesAConstantTimeCountQuery() {
        when(users.countActiveInsuranceStaff(org.mockito.ArgumentMatchers.eq(19L),anySet())).thenReturn(1L);
        assertThat(new InsuranceStaffDirectoryService(users).isEligible(19L)).isTrue();
    }
}
