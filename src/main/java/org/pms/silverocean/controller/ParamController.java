package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.param.ParamGroupDTO;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.wrappers.EnumWrapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/param")
public class ParamController {
    private final ParamService paramService;

    private final I18NService i18NService;

    public ParamController(ParamService paramService, I18NService i18NService) {
        this.paramService = paramService;
        this.i18NService = i18NService;
    }

    @GetMapping("/type")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_PARAM)")
    public ResponseEntity<ResponseDTO> getSupportedUserParams() {
        Set<EnumWrapper> param = paramService.getSupportedParamTypes();
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PARAM_DETAILS.getCode(), i18NService.getLocalizedMessage(ResponseCode.PARAM_DETAILS), param));
    }


    @GetMapping("/user/params")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_PARAM)")
    public ResponseEntity<ResponseDTO> getUserParams() {
        List<ParamGroupDTO> param = paramService.getParamByLoggedInUser();
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PARAM_DETAILS.getCode(), i18NService.getLocalizedMessage(ResponseCode.PARAM_DETAILS), param));
    }

    @GetMapping("/all/params")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_ALL_PARAMS)")
    public ResponseEntity<ResponseDTO> getAllParams(Pageable pageable, Optional<String> filter) {
        Page<ParamGroupDTO> params = paramService.getAllParams(pageable, filter.orElse(null));
        ResponseDTO body = new ResponseDTO(true, ResponseCode.PARAM_DETAILS.getCode(), i18NService.getLocalizedMessage(ResponseCode.PARAM_DETAILS), params.getContent());
        body.setSize(params.getSize());
        body.setTotalPages(params.getTotalPages());
        body.setTotalElements(params.getTotalElements());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/user/params/decrypt")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_PARAM)")
    public ResponseEntity<ResponseDTO> getUserParamDecryptedValue(@RequestParam String groupName) {
        ParamGroupDTO param = paramService.getDecryptedParamValue(groupName);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.DECRYPTED_PARAM_VALUE.getCode(), i18NService.getLocalizedMessage(ResponseCode.DECRYPTED_PARAM_VALUE), param));
    }

    @PutMapping("/update")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_PARAM)")
    public ResponseEntity<ResponseDTO> updateParam(@Valid @RequestBody ParamGroupDTO paramGroupDTO) {
            paramService.updateParam(paramGroupDTO);
            return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PARAM_UPDATED_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.PARAM_UPDATED_SUCCESS)));

    }

    @DeleteMapping("/delete/{groupName}")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).DELETE_PARAM)")
    public ResponseEntity<ResponseDTO> deleteParam(@PathVariable String groupName) {
            paramService.deleteParamGroup(groupName);
            return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PARAM_DELETED_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.PARAM_DELETED_SUCCESS)));

    }

    @PatchMapping("/verify")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VERIFY_PARAM)")
    public ResponseEntity<ResponseDTO> verifyUserParams(@RequestParam String groupName, @RequestParam boolean verify) {
        paramService.verifyParam(groupName, verify);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PARAM_VERIFIED.getCode(), i18NService.getLocalizedMessage(ResponseCode.PARAM_VERIFIED)));
    }



    @PostMapping("/create")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_PARAM)")
    public ResponseEntity<ResponseDTO> createParam(@Valid @RequestBody ParamGroupDTO paramGroupDTO) {
            paramService.createParam(paramGroupDTO);
            return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PARAM_CREATED_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.PARAM_CREATED_SUCCESS)));
    }
}
