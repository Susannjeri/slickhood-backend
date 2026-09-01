package org.pms.silverocean.controller;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.dashboard.DashBoardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/dash")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DashBoardController {
    private final DashBoardService dashBoardService;
    private final I18NService i18NService;

    @GetMapping("/totals")
    public CompletableFuture<ResponseDTO> getDashTotalsByRole(@RequestParam PMSRole role) {
        return dashBoardService.getReportDtoPerActiveRole(role)
                .thenApply(reportDto -> new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), reportDto));
    }
}
