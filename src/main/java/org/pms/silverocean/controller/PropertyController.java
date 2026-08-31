package org.pms.silverocean.controller;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.account.dto.AttachAccountToPropertyDTO;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.PropertyManagerService;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.param.ParamAndPropertyDTO;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.pms.silverocean.service.property.PropertyService;
import org.pms.silverocean.service.property.wrappers.PropertyDTO;
import org.pms.silverocean.service.wrappers.IdNameDescDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/property")
@Validated
public class PropertyController extends BasePropertyController {
    private final PropertyService propertyService;
    private final PropertyManagerService propertyManagerService;
    @Autowired
    public PropertyController(PropertyService propertyService, I18NService i18NService, PropertyManagerService propertyManagerService, UserDao userDao) {
        super(i18NService);
        this.propertyService = propertyService;
        this.propertyManagerService = propertyManagerService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_PROPERTY)")
    public ResponseEntity<ResponseDTO> createProperty(@RequestParam("name") String name,
                                                      @RequestParam("type") PMSPropertyType type,
                                                      @RequestParam(value = "managementMode", defaultValue = "RENTAL") PMSPropertyManagementMode managementMode,
                                                      @RequestParam("address") String address,
                                                      @RequestParam("mapLocation") String mapLocation,
                                                      @RequestParam("currency") String currency,
                                                      @RequestParam("image") MultipartFile image) {
        PropertyDTO propertyDTO = new PropertyDTO(name, type, type.getCategory(), managementMode, address, mapLocation, currency, null, null, null);
        Optional<ResponseDTO> violations = validate(propertyDTO);
        if (violations.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(violations.get());
        }

        ResponseDTO responseDTO = propertyService.createProperty(propertyDTO, image);
        return responseDTO.isSuccess() ? ResponseEntity.status(HttpStatus.CREATED).body(responseDTO) : ResponseEntity.status(propertyFailureStatus(responseDTO)).body(responseDTO);
    }

    @GetMapping("/type")
    public ResponseEntity<ResponseDTO> getPropertyType(@RequestParam Optional<String> filter) {
        return ResponseEntity.ok(propertyService.getSupportedPropertyTypes(filter.orElse(null)));
    }


    @GetMapping("/utilities")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_UNIT)")
    public ResponseEntity<ResponseDTO> getSupportedUtilities() {
        return ResponseEntity.ok(propertyService.getSupportedUtilities());
    }

    @GetMapping("/charge/types")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_UNIT)")
    public ResponseEntity<ResponseDTO> getSupportedChargeTypes() {
        return ResponseEntity.ok(propertyService.getSupportedChargeTypes());
    }

    @GetMapping("/period/types")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_UNIT)")
    public ResponseEntity<ResponseDTO> getSupportedPeriodTypes() {
        return ResponseEntity.ok(propertyService.getSupportedPeriods());
    }


    @GetMapping("/measurement/units")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_UNIT)")
    public ResponseEntity<ResponseDTO> getMeasurementUnits() {
        return ResponseEntity.ok(propertyService.getSupportedMeasurement());
    }


    @PutMapping("/update")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_PROPERTY)")
    public ResponseEntity<ResponseDTO> updateProperty(@RequestParam long propertyId,
                                                      @RequestParam("name") String name,
                                                      @RequestParam("type") PMSPropertyType type,
                                                      @RequestParam(value = "managementMode", required = false) Optional<PMSPropertyManagementMode> managementMode,
                                                      @RequestParam("address") String address,
                                                      @RequestParam("mapLocation") String mapLocation,
                                                      @RequestParam("currency") String currency,
                                                      @RequestParam("image") Optional<MultipartFile> image) {
        PropertyDTO propertyDTO = new PropertyDTO(name, type, type.getCategory(), managementMode.orElse(null), address, mapLocation, currency, propertyId, null, null);
        Optional<ResponseDTO> violations = validate(propertyDTO);
        if (violations.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(violations.get());
        }

        ResponseDTO responseDTO = propertyService.updateProperty(propertyId, propertyDTO, image.orElse(null));
        return responseDTO.isSuccess() ? ResponseEntity.ok(responseDTO) : ResponseEntity.status(propertyFailureStatus(responseDTO)).body(responseDTO);
    }


    @DeleteMapping("/delete")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).DELETE_PROPERTY)")
    public ResponseEntity<ResponseDTO> deleteProperty(@RequestParam long propertyId) {
        ResponseDTO responseDTO = propertyService.deleteProperty(propertyId);
        return responseDTO.isSuccess() ? ResponseEntity.ok(responseDTO) : ResponseEntity.status(HttpStatus.CONFLICT).body(responseDTO);
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_PROPERTY)")
    public ResponseEntity<ResponseDTO> getPropertyList(@RequestParam Optional<String> search, @RequestParam Optional<Long> propertyId, Pageable pageable) {
        return ResponseEntity.ok(propertyService.listProperty(pageable, search, propertyId, this::determineUserRoleInProperty));
    }

    @GetMapping("/list/key/value")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_PROPERTY_LIST)")
    public ResponseEntity<ResponseDTO> getPropertyListForKeyValue(@RequestParam Optional<String> search, Pageable pageable) {
        Page<IdNameDescDTO> propertyKeyValueDTOS = propertyService.listPropertyListForKeyValue(pageable, search);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), propertyKeyValueDTOS.toList(),
                propertyKeyValueDTOS.getTotalPages(), propertyKeyValueDTOS.getTotalElements(), propertyKeyValueDTOS.getSize()));
    }

    // todo remove these endpoint after integrating the new account approach
    @PostMapping("/param/set")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_PROPERTY_PARAM)")
    public ResponseEntity<ResponseDTO> setPropertyParam(@RequestBody ParamAndPropertyDTO paramAndPropertyDTO) {
        ResponseCode responseCode = propertyService.savePropertyParam(paramAndPropertyDTO.groupName(), paramAndPropertyDTO.propertyId());
        return ResponseEntity.ok(new ResponseDTO(true, responseCode.getCode(), i18NService.getLocalizedMessage(responseCode)));

    }

    @PostMapping("/account/add")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_PROPERTY_PARAM)")
    public ResponseEntity<ResponseDTO> addAccountToProperty(@RequestBody AttachAccountToPropertyDTO attachAccountToPropertyDTO) {
        propertyService.attachAccountToProperty(attachAccountToPropertyDTO.accountId(), attachAccountToPropertyDTO.propertyId());
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)));

    }

    @DeleteMapping("/account/delete")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).DELETE_PROPERTY_PARAM)")
    public ResponseEntity<ResponseDTO> removeAccountFromProperty(@RequestBody AttachAccountToPropertyDTO attachAccountToPropertyDTO) {
       propertyService.removeAccountFromProperty(attachAccountToPropertyDTO.accountId(), attachAccountToPropertyDTO.propertyId());
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS)));

    }

    @GetMapping("/param/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_PROPERTY_PARAM)")
    public ResponseEntity<ResponseDTO> getPropertyParam(@RequestParam Long propertyId) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PROPERTY_PARAM_LIST.getCode(), i18NService.getLocalizedMessage(ResponseCode.PROPERTY_PARAM_LIST), propertyService.listPropertyAccounts(propertyId)));
    }

    @DeleteMapping("/param/delete")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).DELETE_PROPERTY_PARAM)")
    public ResponseEntity<ResponseDTO> deletePropertyParam(@RequestParam String groupName, @RequestParam Long propertyId) {
        propertyService.deletePropertyParam(groupName, propertyId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.DELETE_PROPERTY_PARAM_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.DELETE_PROPERTY_PARAM_SUCCESS)));

    }

    @GetMapping("/staff")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_PROPERTY_STAFF)")
    public ResponseEntity<ResponseDTO> getPropertyStaff(@RequestParam Long propertyId) {
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.PROPERTY_STAFF_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.PROPERTY_STAFF_LIST), propertyManagerService.findAllStaffByProperty(propertyId)));
    }

    @GetMapping("/landlord/key/value")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_PROPERTY_LANDLORDS)")
    public ResponseEntity<ResponseDTO> getPropertyLandlords(Pageable pageable, @RequestParam Optional<String> search) {
        Page<IdNameDescDTO> allLandlords = propertyService.findAllLandlords(pageable, search.orElse(null));
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), allLandlords.toList(),
                allLandlords.getTotalPages(), allLandlords.getTotalElements(), allLandlords.getSize()));
    }


    @GetMapping("/tenant/key/value")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_PROPERTY_TENANTS)")
    public ResponseEntity<ResponseDTO> getPropertyTenants(Pageable pageable, @RequestParam Optional<String> search) {
        Page<IdNameDescDTO> allTenants = propertyService.findAllTenants(pageable, search.orElse(null));
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), allTenants.toList(),
                allTenants.getTotalPages(), allTenants.getTotalElements(), allTenants.getSize()));
    }

    @DeleteMapping("/staff")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).DELETE_PROPERTY_STAFF)")
    public ResponseEntity<ResponseDTO> deletePropertyStaff(@RequestParam Long staffId) {
        propertyManagerService.removeStaffFromProperty(staffId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.STAFF_DELETED_FROM_PROPERTY.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.STAFF_DELETED_FROM_PROPERTY)));
    }

    private String determineUserRoleInProperty(Property property, Long userId) {
        if (Objects.equals(userId, property.getCreatedBy())) {
            return PMSRole.LANDLORD.getName();
        }

        return propertyManagerService.getStaffRoleInProperty(userId, property.getId())
                .orElse(PMSRole.TENANT.getName());
    }

    private HttpStatus propertyFailureStatus(ResponseDTO response) {
        if (ResponseCode.INVALID_IMAGE.getCode().equals(response.getCode())
                || ResponseCode.IMAGE_TOO_SMALL.getCode().equals(response.getCode())
                || ResponseCode.MAX_UPLOAD_SIZE_EXCEEDED.getCode().equals(response.getCode())) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.CONFLICT;
    }
}
