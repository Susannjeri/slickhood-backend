package org.pms.silverocean.service.kyc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.KycMatrixReleaseRepo;
import org.pms.silverocean.database.pms.KycMatrixRequirementRepo;
import org.pms.silverocean.database.pms.ServiceCategoryRepo;
import org.pms.silverocean.database.pms.entities.KycMatrixRelease;
import org.pms.silverocean.database.pms.entities.KycMatrixRequirement;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycMatrixServiceTest {
    @Mock KycMatrixReleaseRepo releases;
    @Mock KycMatrixRequirementRepo requirements;
    @Mock UserDao users;
    @Mock AuditLogService audit;
    @Mock ServiceCategoryRepo categories;
    KycMatrixService service;

    @BeforeEach void setup(){service=new KycMatrixService(releases,requirements,users,audit,categories);}

    @Test void conditionalRequirementNeedsHumanReadableCondition(){
        KycMatrixRelease draft=release(2L,2,"DRAFT");when(releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("DRAFT")).thenReturn(Optional.of(draft));
        var request=new KycMatrixRequests.RequirementUpsert("SOKO_CATEGORY","PHARMACY","Pharmacy","PHARMACY_LICENSE","Pharmacy licence","CONDITIONAL","BOTH","PROFESSIONAL_CERTIFICATE",null,365,30,true);
        assertThrows(PMSCustomException.class,()->service.add(request));
    }

    @Test void previewReportsAddedChangedAndDeactivatedRows(){
        KycMatrixRelease live=release(1L,1,"PUBLISHED"),draft=release(2L,2,"DRAFT");when(releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("PUBLISHED")).thenReturn(Optional.of(live));when(releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("DRAFT")).thenReturn(Optional.of(draft));
        KycMatrixRequirement old=row("A","Old",true),changed=row("A","New",true),removed=row("B","Old",true),deactivated=row("B","Old",false),added=row("C","Added",true);
        when(requirements.findAllByReleaseIdOrderByScopeTypeAscScopeLabelAscRequirementLabelAsc(1L)).thenReturn(List.of(old,removed));when(requirements.findAllByReleaseIdOrderByScopeTypeAscScopeLabelAscRequirementLabelAsc(2L)).thenReturn(List.of(changed,deactivated,added));
        var preview=service.preview();assertEquals(1,preview.added());assertEquals(1,preview.changed());assertEquals(1,preview.deactivated());
    }

    private KycMatrixRelease release(long id,int version,String status){KycMatrixRelease release=new KycMatrixRelease();release.setId(id);release.setVersionNo(version);release.setStatus(status);release.setActive(true);return release;}
    private KycMatrixRequirement row(String code,String label,boolean active){KycMatrixRequirement row=new KycMatrixRequirement();row.setScopeType("SOKO_CATEGORY");row.setScopeKey("FOOD");row.setScopeLabel("Food");row.setRequirementCode(code);row.setRequirementLabel(label);row.setObligation("MANDATORY");row.setProfileScope("BOTH");row.setAcceptedDocumentTypes("BUSINESS_REGISTRATION_CERTIFICATE");row.setActive(active);return row;}
}
