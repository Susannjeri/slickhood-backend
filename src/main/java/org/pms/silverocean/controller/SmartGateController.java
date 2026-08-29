package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.visitor.VisitorAccessService;
import org.pms.silverocean.service.visitor.wrappers.AccessDecisionDTO;
import org.pms.silverocean.service.visitor.wrappers.GateDeviceDTO;
import org.pms.silverocean.service.visitor.wrappers.GateDeviceRegistrationRequest;
import org.pms.silverocean.service.visitor.wrappers.GateDeviceStatusRequest;
import org.pms.silverocean.service.visitor.wrappers.AccessEventDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/smart-gate")
@RequiredArgsConstructor
public class SmartGateController {
    private final VisitorAccessService accessService;

    @PostMapping("/devices")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_GATE_DEVICES)")
    public GateDeviceDTO register(@RequestBody @Valid GateDeviceRegistrationRequest request) {
        return accessService.registerDevice(request);
    }

    @GetMapping("/devices")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_GATE_DEVICES)")
    public List<GateDeviceDTO> list(@RequestParam long propertyId) {
        return accessService.listDevices(propertyId);
    }

    @PutMapping("/devices/{deviceCode}/status")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_GATE_DEVICES)")
    public GateDeviceDTO setStatus(@PathVariable String deviceCode, @RequestBody GateDeviceStatusRequest request) {
        return accessService.setDeviceEnabled(deviceCode, request.enabled());
    }

    @GetMapping("/events")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_GATE_EVENTS)")
    public Page<AccessEventDTO> events(@RequestParam long propertyId, Pageable pageable) {
        return accessService.listEvents(propertyId, pageable);
    }

    @PostMapping("/device/access-decision")
    public ResponseEntity<AccessDecisionDTO> decide(
            @RequestHeader("X-Gate-Device") String deviceCode,
            @RequestHeader("X-Gate-Timestamp") long timestamp,
            @RequestHeader("X-Gate-Nonce") String nonce,
            @RequestHeader("X-Gate-Signature") String signature,
            @RequestBody String rawBody) {
        try {
            return ResponseEntity.ok(accessService.decideFromDevice(deviceCode, timestamp, nonce, signature, rawBody));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AccessDecisionDTO(false,
                    ex.getMessage(), null, null, null, null, null, ZonedDateTime.now(ZoneId.of("UTC"))));
        }
    }
}
