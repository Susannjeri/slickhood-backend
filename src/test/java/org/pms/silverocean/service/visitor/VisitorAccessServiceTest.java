package org.pms.silverocean.service.visitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.GateDeviceRepo;
import org.pms.silverocean.database.pms.GateRequestNonceRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.VisitorAccessEventRepo;
import org.pms.silverocean.database.pms.VisitorRepo;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.database.pms.entities.Visitor;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.pms.silverocean.service.property.PMSUnitTypes;
import org.pms.silverocean.service.property.wrappers.DbUnitDTO;
import org.pms.silverocean.service.visitor.enums.VisitType;
import org.pms.silverocean.service.visitor.enums.VisitorCategory;
import org.pms.silverocean.service.visitor.enums.VisitorStatus;
import org.pms.silverocean.service.visitor.wrappers.RegisterVisitRequest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class VisitorAccessServiceTest {
    @Mock VisitorRepo visitorRepo;
    @Mock VisitorDao visitorDao;
    @Mock UserDao userDao;
    @Mock UnitRepo unitRepo;
    @Mock PropertyRepo propertyRepo;
    @Mock GateDeviceRepo gateDeviceRepo;
    @Mock GateRequestNonceRepo nonceRepo;
    @Mock VisitorAccessEventRepo eventRepo;
    private VisitorAccessService service;

    @BeforeEach
    void setUp() {
        service = new VisitorAccessService(visitorRepo, visitorDao, userDao, unitRepo, propertyRepo,
                gateDeviceRepo, nonceRepo, eventRepo, new ObjectMapper());
    }

    @Test
    void expectedDriveInIssuesOneTimeCredentialAndApprovedAccessWindow() {
        Users host = new Users(); host.setId(7L);
        DbUnitDTO unit = new DbUnitDTO(20L, "A-12", PMSUnitTypes.APARTMENT_UNIT, PMSPropertyType.APARTMENT_BLOCK,
                80d, "RENT", 1d, "KES", true, false, null, null, null, 0, 10L, null);
        Property property = new Property(); property.setId(20L); property.setName("Acacia Court");
        when(userDao.getUserObject()).thenReturn(host);
        when(unitRepo.findByIdAndStaffOrOwnerOrTenant(10L, 7L)).thenReturn(Optional.of(unit));
        when(propertyRepo.findById(20L)).thenReturn(Optional.of(property));
        doAnswer(invocation -> { ((Visitor) invocation.getArgument(0)).setId(99L); return null; })
                .when(visitorDao).save(org.mockito.ArgumentMatchers.any(Visitor.class), anyString());

        RegisterVisitRequest request = new RegisterVisitRequest("Amina Noor", "0712345678", VisitType.DRIVE_IN,
                VisitorCategory.GUEST, 10L, null, LocalDateTime.now().plusHours(3), null, "Family visit",
                "KDA 123A", "P4", null, null, 1, false);
        var result = service.registerExpected(request);

        ArgumentCaptor<Visitor> visitor = ArgumentCaptor.forClass(Visitor.class);
        verify(visitorDao).save(visitor.capture(), anyString());
        assertEquals(VisitorStatus.APPROVED.name(), visitor.getValue().getStatus());
        assertEquals(VisitType.DRIVE_IN.name(), visitor.getValue().getVisitType());
        assertEquals("KDA123A", visitor.getValue().getVehiclePlate());
        assertFalse(result.accessCode().isBlank());
        assertEquals(64, visitor.getValue().getCredentialHash().length());
    }
}
