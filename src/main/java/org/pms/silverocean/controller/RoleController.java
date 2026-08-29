package org.pms.silverocean.controller;

import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/role")
@Validated
public class RoleController {
    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/list")
    public ResponseEntity<ResponseDTO> getAllRoles(WebRequest webRequest, Pageable pageable) {
        return ResponseEntity.ok(roleService.listRoles(pageable));
    }

    @PostMapping("/assign")
    public ResponseEntity<ResponseDTO> assignRole(@Validated @RequestParam String token) {
        ResponseDTO response = roleService.addRoleToLoggedInUserUsingInviteToken(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/self-assign")
    public ResponseEntity<ResponseDTO> selfAssignRole(@RequestParam long roleId) {
        return ResponseEntity.ok(roleService.selfAssignRole(roleId));
    }
}
