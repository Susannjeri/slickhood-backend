package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.teamaccess.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/team-access") @RequiredArgsConstructor
public class TeamAccessController {
    private final TeamAccessService service;
    private final TeamRoleDefinitionService roleDefinitions;
    private final I18NService i18n;

    @GetMapping public ResponseEntity<ResponseDTO> current() { return ok(service.current()); }
    @PostMapping("/invitations") public ResponseEntity<ResponseDTO> invite(@Valid @RequestBody TeamAccessModels.InviteRequest request) { return ok(service.invite(request)); }
    @PostMapping("/invitations/{id}/resend") public ResponseEntity<ResponseDTO> resend(@PathVariable long id) { return ok(service.resend(id)); }
    @DeleteMapping("/invitations/{id}") public ResponseEntity<ResponseDTO> revokeInvite(@PathVariable long id) { service.revokeInvitation(id); return ok(null); }
    @GetMapping("/invitations/inspect") public ResponseEntity<ResponseDTO> inspect(@RequestParam String token) { return ok(service.inspect(token)); }
    @PostMapping("/invitations/accept") public ResponseEntity<ResponseDTO> accept(@RequestParam String token) { return ok(service.accept(token)); }
    @PatchMapping("/members/{id}/scope") public ResponseEntity<ResponseDTO> scope(@PathVariable long id,@Valid @RequestBody TeamAccessModels.ScopeUpdate request) { return ok(service.updateScope(id,request)); }
    @PostMapping("/members/{id}/suspend") public ResponseEntity<ResponseDTO> suspend(@PathVariable long id) { return ok(service.suspend(id)); }
    @PostMapping("/members/{id}/resume") public ResponseEntity<ResponseDTO> resume(@PathVariable long id) { return ok(service.resume(id)); }
    @DeleteMapping("/members/{id}") public ResponseEntity<ResponseDTO> revoke(@PathVariable long id) { return ok(service.revokeMember(id)); }

    @GetMapping("/role-definitions") public ResponseEntity<ResponseDTO> roleDefinitions() { return ok(roleDefinitions.list()); }
    @PostMapping("/role-definitions") public ResponseEntity<ResponseDTO> createRole(@Valid @RequestBody TeamAccessModels.RoleDefinitionRequest request) { return ok(roleDefinitions.create(request)); }
    @PutMapping("/role-definitions/{id}") public ResponseEntity<ResponseDTO> updateRole(@PathVariable long id,@Valid @RequestBody TeamAccessModels.RoleDefinitionRequest request) { return ok(roleDefinitions.update(id,request)); }
    @PatchMapping("/role-definitions/{id}/status") public ResponseEntity<ResponseDTO> roleStatus(@PathVariable long id,@RequestParam boolean active) { return ok(roleDefinitions.setActive(id,active)); }

    private ResponseEntity<ResponseDTO> ok(Object data) { return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),data)); }
}
