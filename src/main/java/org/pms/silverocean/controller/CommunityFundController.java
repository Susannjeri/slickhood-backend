package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.communityfund.CommunityFundModels;
import org.pms.silverocean.service.communityfund.CommunityFundService;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/community-funds")
@RequiredArgsConstructor
public class CommunityFundController {
    private final CommunityFundService service;
    private final I18NService i18n;

    @PostMapping
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_COMMUNITY_FUNDS)")
    public ResponseEntity<ResponseDTO> create(@RequestBody @Valid CommunityFundModels.CreateFundRequest request){return ok(service.create(request));}

    @GetMapping
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_COMMUNITY_FUNDS)")
    public ResponseEntity<ResponseDTO> list(){return ok(service.list());}

    @GetMapping("/{fundId}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_COMMUNITY_FUNDS)")
    public ResponseEntity<ResponseDTO> dashboard(@PathVariable long fundId){return ok(service.dashboard(fundId));}

    @PostMapping("/{fundId}/open")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).MANAGE_COMMUNITY_FUNDS)")
    public ResponseEntity<ResponseDTO> open(@PathVariable long fundId){return ok(service.open(fundId));}

    @PostMapping("/contributions/{contributionId}/pledge")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_COMMUNITY_FUNDS)")
    public ResponseEntity<ResponseDTO> pledge(@PathVariable long contributionId,@RequestBody @Valid CommunityFundModels.PledgeRequest request){return ok(service.pledge(contributionId,request));}

    @PostMapping("/{fundId}/expenditures")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REQUEST_FUND_EXPENDITURE)")
    public ResponseEntity<ResponseDTO> requestExpenditure(@PathVariable long fundId,@RequestBody @Valid CommunityFundModels.ExpenditureRequest request){return ok(service.requestExpenditure(fundId,request));}

    @PostMapping("/expenditures/{expenditureId}/approve")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).APPROVE_FUND_EXPENDITURE)")
    public ResponseEntity<ResponseDTO> approve(@PathVariable long expenditureId){return ok(service.approve(expenditureId));}

    @PostMapping("/expenditures/{expenditureId}/reject")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).APPROVE_FUND_EXPENDITURE)")
    public ResponseEntity<ResponseDTO> reject(@PathVariable long expenditureId,@RequestBody @Valid CommunityFundModels.RejectRequest request){return ok(service.reject(expenditureId,request));}

    @PostMapping("/expenditures/{expenditureId}/disburse")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).RECORD_FUND_DISBURSEMENT)")
    public ResponseEntity<ResponseDTO> disburse(@PathVariable long expenditureId,@RequestBody @Valid CommunityFundModels.DisbursementRequest request){return ok(service.disburse(expenditureId,request));}

    private ResponseEntity<ResponseDTO> ok(Object data){return ResponseEntity.ok(new ResponseDTO(true,ResponseCode.GENERAL_SUCCESS.getCode(),i18n.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS),data));}
}
