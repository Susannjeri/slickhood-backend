package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.estateops.EstateOperationsModels;
import org.pms.silverocean.service.estateops.EstateOperationsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/estate/operations")
@RequiredArgsConstructor
public class EstateOperationsController {
    private static final String MANAGE = "hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_ESTATE)";
    private static final String VIEW = "hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_ESTATE)";
    private final EstateOperationsService service;
    private final I18NService i18n;

    @PostMapping("/budgets") @PreAuthorize(MANAGE)
    ResponseEntity<ResponseDTO> budget(@RequestBody @Valid EstateOperationsModels.BudgetCreate request) { return ok(service.createBudget(request)); }
    @PostMapping("/budgets/{id}/lines") @PreAuthorize(MANAGE)
    ResponseEntity<ResponseDTO> line(@PathVariable long id,@RequestBody @Valid EstateOperationsModels.BudgetLineCreate request) { return ok(service.addLine(id,request)); }
    @PutMapping("/budgets/{id}/approve") @PreAuthorize(MANAGE)
    ResponseEntity<ResponseDTO> approve(@PathVariable long id) { return ok(service.approve(id)); }
    @PutMapping("/budgets/{id}/close") @PreAuthorize(MANAGE)
    ResponseEntity<ResponseDTO> close(@PathVariable long id) { return ok(service.close(id)); }
    @PutMapping("/budget-lines/{id}/actual") @PreAuthorize(MANAGE)
    ResponseEntity<ResponseDTO> actual(@PathVariable long id,@RequestBody @Valid EstateOperationsModels.Actual request) { return ok(service.actual(id,request)); }
    @GetMapping("/properties/{id}/budgets") @PreAuthorize(VIEW)
    ResponseEntity<ResponseDTO> budgets(@PathVariable long id,@PageableDefault(size=20,sort="budgetYear") Pageable pageable) { return page(service.budgetList(id,pageable)); }

    @PostMapping("/meetings") @PreAuthorize(MANAGE)
    ResponseEntity<ResponseDTO> meeting(@RequestBody @Valid EstateOperationsModels.MeetingCreate request) { return ok(service.createMeeting(request)); }
    @PutMapping("/meetings/{id}") @PreAuthorize(MANAGE)
    ResponseEntity<ResponseDTO> meetingUpdate(@PathVariable long id,@RequestBody @Valid EstateOperationsModels.MeetingUpdate request) { return ok(service.updateMeeting(id,request)); }
    @GetMapping("/properties/{id}/meetings") @PreAuthorize(VIEW)
    ResponseEntity<ResponseDTO> meetings(@PathVariable long id,@PageableDefault(size=20,sort="scheduledAt") Pageable pageable) { return page(service.meetingList(id,pageable)); }
    @PostMapping("/meetings/{id}/resolutions") @PreAuthorize(MANAGE)
    ResponseEntity<ResponseDTO> resolution(@PathVariable long id,@RequestBody @Valid EstateOperationsModels.ResolutionCreate request) { return ok(service.createResolution(id,request)); }
    @PutMapping("/resolutions/{id}") @PreAuthorize(MANAGE)
    ResponseEntity<ResponseDTO> resolutionUpdate(@PathVariable long id,@RequestBody @Valid EstateOperationsModels.ResolutionUpdate request) { return ok(service.updateResolution(id,request)); }
    @GetMapping("/meetings/{id}/resolutions") @PreAuthorize(VIEW)
    ResponseEntity<ResponseDTO> resolutions(@PathVariable long id,@PageableDefault(size=50,sort="createdOn") Pageable pageable) { return page(service.resolutionList(id,pageable)); }

    @PostMapping("/work-orders") @PreAuthorize(MANAGE)
    ResponseEntity<ResponseDTO> work(@RequestBody @Valid EstateOperationsModels.WorkCreate request) { return ok(service.createWork(request)); }
    @PutMapping("/work-orders/{id}") @PreAuthorize(MANAGE)
    ResponseEntity<ResponseDTO> workUpdate(@PathVariable long id,@RequestBody @Valid EstateOperationsModels.WorkUpdate request) { return ok(service.updateWork(id,request)); }
    @GetMapping("/properties/{id}/work-orders") @PreAuthorize(VIEW)
    ResponseEntity<ResponseDTO> works(@PathVariable long id,@PageableDefault(size=20,sort="createdOn") Pageable pageable) { return page(service.workList(id,pageable)); }

    private ResponseEntity<ResponseDTO> ok(Object value) {
        return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),value));
    }
    private ResponseEntity<ResponseDTO> page(Page<?> values) {
        ResponseDTO response = new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),values.getContent());
        response.setSize(values.getSize());response.setTotalPages(values.getTotalPages());response.setTotalElements(values.getTotalElements());
        return ResponseEntity.ok(response);
    }
}
