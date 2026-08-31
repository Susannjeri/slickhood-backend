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
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.database.pms.entities.Visitor;
import org.pms.silverocean.database.pms.entities.GateDevice;
import org.pms.silverocean.database.pms.entities.VisitorAccessEvent;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.pms.silverocean.service.property.PMSUnitTypes;
import org.pms.silverocean.service.property.wrappers.DbUnitDTO;
import org.pms.silverocean.service.visitor.enums.VisitType;
import org.pms.silverocean.service.visitor.enums.VisitorCategory;
import org.pms.silverocean.service.visitor.enums.VisitorStatus;
import org.pms.silverocean.service.visitor.wrappers.RegisterVisitRequest;
import org.pms.silverocean.service.visitor.wrappers.VisitorDecisionRequest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock NotificationService notificationService;
    @Mock ConfigService configService;
    private VisitorAccessService service;

    @BeforeEach
    void setUp() {
        service = new VisitorAccessService(visitorRepo, visitorDao, userDao, unitRepo, propertyRepo,
                gateDeviceRepo, nonceRepo, eventRepo, new ObjectMapper(), notificationService, configService);
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

    @Test
    void signedGateRequestCannotBypassPendingApproval() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        GateDevice device = new GateDevice();
        device.setId(4L); device.setPropertyId(20L); device.setDisplayName("North gate");
        device.setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        when(gateDeviceRepo.findByDeviceCodeAndEnabledTrueAndActiveTrue("gate-test")).thenReturn(Optional.of(device));
        when(eventRepo.findByCorrelationId("entry-1001")).thenReturn(Optional.empty());

        Visitor pending = new Visitor();
        pending.setId(99L); pending.setPropertyId(20L); pending.setStatus(VisitorStatus.PENDING_APPROVAL.name());
        pending.setValidFrom(ZonedDateTime.now(ZoneId.of("UTC")).minusMinutes(5));
        pending.setValidUntil(ZonedDateTime.now(ZoneId.of("UTC")).plusMinutes(30));
        pending.setMaxEntries(1); pending.setActive(true);
        when(visitorRepo.findByCredentialHashForUpdate(anyString())).thenReturn(Optional.of(pending));

        long timestamp = java.time.Instant.now().getEpochSecond();
        String nonce = "unique-nonce-123456789";
        String body = "{\"accessCode\":\"" + "A".repeat(43) + "\",\"direction\":\"ENTRY\",\"correlationId\":\"entry-1001\"}";
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update((timestamp + "\n" + nonce + "\n" + body).getBytes(StandardCharsets.UTF_8));

        var result = service.decideFromDevice("gate-test", timestamp, nonce,
                Base64.getEncoder().encodeToString(signer.sign()), body);

        assertFalse(result.granted());
        assertEquals("VISIT_NOT_APPROVED", result.reasonCode());
        ArgumentCaptor<VisitorAccessEvent> event = ArgumentCaptor.forClass(VisitorAccessEvent.class);
        verify(eventRepo).save(event.capture());
        assertEquals("DENIED", event.getValue().getOutcome());
    }

    @Test
    void gateRejectsOversizedReplayNonceBeforeCryptographicWork() {
        GateDevice device = new GateDevice(); device.setId(4L); device.setPropertyId(20L);
        when(gateDeviceRepo.findByDeviceCodeAndEnabledTrueAndActiveTrue("gate-test")).thenReturn(Optional.of(device));
        assertThrows(IllegalArgumentException.class, () -> service.decideFromDevice("gate-test",
                java.time.Instant.now().getEpochSecond(), "n".repeat(101), "signature", "{}"));
    }

    @Test
    void hostDenialRequiresAndPersistsAnAuditReason() {
        Users host = new Users(); host.setId(7L);
        Visitor pending = new Visitor(); pending.setId(99L); pending.setStatus(VisitorStatus.PENDING_APPROVAL.name());
        pending.setCategory(VisitorCategory.GUEST.name()); pending.setPhoneNumber("+254700000000");
        when(userDao.getUserId()).thenReturn(7L);
        when(visitorRepo.findByIdAndHostUserId(99L, 7L)).thenReturn(Optional.of(pending));

        assertThrows(org.pms.silverocean.service.PMSCustomException.class,
                () -> service.decide(99L, new VisitorDecisionRequest(VisitorDecisionRequest.Decision.DENY, " ")));
        var result = service.decide(99L, new VisitorDecisionRequest(VisitorDecisionRequest.Decision.DENY, "Host could not verify the visitor"));

        assertEquals(VisitorStatus.DENIED.name(), result.visit().status());
        assertEquals("Host could not verify the visitor", pending.getDecisionReason());
    }
}
