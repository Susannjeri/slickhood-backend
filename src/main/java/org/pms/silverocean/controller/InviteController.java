package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.InviteLinkDTO;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.controller.wrappers.ShareInviteDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.invites.InviteDTO;
import org.pms.silverocean.service.invites.InviteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@RestController
@RequestMapping("/invite")
@Validated
public class InviteController {
    private final InviteService inviteService;
    private final I18NService i18NService;

    public InviteController(InviteService inviteService, I18NService i18NService) {
        this.inviteService = inviteService;
        this.i18NService = i18NService;
    }

    @GetMapping("/types")
    public ResponseEntity<ResponseDTO> getInviteTypes() {
        return ResponseEntity.ok(inviteService.getSupportedInviteTypes());
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_INVITE)")
    public ResponseEntity<ResponseDTO> createInviteLink(@Valid @RequestBody InviteLinkDTO inviteLinkDTO) throws URISyntaxException {
        String link = inviteService.createInviteLink(inviteLinkDTO.inviteType(), inviteLinkDTO.entityId());
        return ResponseEntity.created(new URI(link)).body(new ResponseDTO(true, ResponseCode.INVITE_CREATED_SUCCESSFULLY.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.INVITE_CREATED_SUCCESSFULLY), link));
    }

    @PostMapping("/share")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).SHARE_INVITE)")
    public ResponseEntity<ResponseDTO> shareInviteLink(@Valid @RequestBody ShareInviteDTO shareInviteDTO) {
        inviteService.sendInvite(shareInviteDTO.inviteId(), shareInviteDTO.recipient(), shareInviteDTO.notificationChannel());
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.LINK_SENT_TO_RECIPIENT.getCode(), i18NService.getLocalizedMessage(ResponseCode.LINK_SENT_TO_RECIPIENT)));

    }

    @GetMapping("/validate")
    public ResponseEntity<ResponseDTO> validateInviteToken(@RequestParam String token) {
        ResponseDTO response = inviteService.validateToken(token);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/update")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).UPDATE_INVITE)")
    public ResponseEntity<ResponseDTO> updateInviteLink(@RequestParam long id, @RequestParam boolean active)  {
        inviteService.updateInvite(id, active);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.INVITE_UPDATED_SUCCESSFULLY.getCode(), i18NService.getLocalizedMessage(ResponseCode.INVITE_UPDATED_SUCCESSFULLY)));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_INVITE_LIST)")
    public ResponseEntity<ResponseDTO> listInviteLink(Pageable pageable, @RequestParam Optional<Long> unitId) {
        Page<InviteDTO> invites = inviteService.getUserInvites(pageable, unitId.orElse(null));
        ResponseDTO body = new ResponseDTO(true, ResponseCode.USER_INVITE_LINKS.getCode(), i18NService.getLocalizedMessage(ResponseCode.USER_INVITE_LINKS), invites.getContent());
        body.setSize(invites.getSize());
        body.setTotalPages(invites.getTotalPages());
        body.setTotalElements(invites.getTotalElements());
        return ResponseEntity.ok(body);
    }

}
