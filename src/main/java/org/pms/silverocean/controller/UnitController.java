package org.pms.silverocean.controller;

import jakarta.validation.Valid;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.controller.wrappers.UnitChargesDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.visitor.projections.PropertyIdUnitRefPropertyNameProjection;
import org.pms.silverocean.service.wrappers.IdNameDescDTO;
import org.pms.silverocean.service.property.MeasurementUnitsDTO;
import org.pms.silverocean.service.property.PMSMeasurementUnits;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.pms.silverocean.service.property.PMSUnitTypes;
import org.pms.silverocean.service.property.PropertyService;
import org.pms.silverocean.service.property.wrappers.PropertyManagerDetailsDTO;
import org.pms.silverocean.service.property.wrappers.UnitDTO;
import org.pms.silverocean.service.property.wrappers.UnitTenantProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

@RestController
@RequestMapping("/property/unit")
@Validated
public class UnitController extends BasePropertyController {
    private final PropertyService propertyService;


    public UnitController(PropertyService propertyService,  I18NService i18NService) {
        super(i18NService);
        this.propertyService = propertyService;
    }

    @GetMapping("/type")
    public ResponseEntity<ResponseDTO> getUnitType(@RequestParam PMSPropertyType propertyType) {
        return ResponseEntity.ok(propertyService.getUnitTypes(propertyType));
    }


    @PostMapping("/create")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_UNIT)")
    public ResponseEntity<ResponseDTO> createUnit(@RequestParam("propertyId") long propertyId,
                                                  @RequestParam("uniqueRef") String ref,
                                                  @RequestParam("unitType") PMSUnitTypes unitType,
                                                  @RequestParam("size") double size,
                                                  @RequestParam("currency") Optional<String> currency,
                                                  @RequestParam("measurementUnits") PMSMeasurementUnits measurementUnits,
                                                  @RequestParam("utilities") Set<Long> utilities,
                                                  @RequestParam("leaseMode") PMSLeaseMode leaseMode,
                                                  @RequestParam("price") double price,
                                                  @RequestParam("templateId") Long templateId,
                                                  @RequestParam("image") MultipartFile image) {

        UnitDTO unitDTO = new UnitDTO(propertyId, ref, unitType, size, new MeasurementUnitsDTO(measurementUnits.getId(), measurementUnits.getName()), utilities, leaseMode, price, currency.orElse(null), templateId);
        return handleUnit(unitDTO, propertyService::createUnit, image, HttpStatus.CREATED);
    }


    @PutMapping("/update")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_UNIT)")
    public ResponseEntity<ResponseDTO> editUnit(@RequestParam("unitId") long unitId,
                                                @RequestParam("propertyId") long propertyId,
                                                @RequestParam("uniqueRef") String ref,
                                                @RequestParam("unitType") PMSUnitTypes unitType,
                                                @RequestParam("size") double size,
                                                @RequestParam("currency") Optional<String> currency,
                                                @RequestParam("measurementUnits") PMSMeasurementUnits measurementUnits,
                                                @RequestParam("utilities") Set<Long> utilities,
                                                @RequestParam("leaseMode") PMSLeaseMode leaseMode,
                                                @RequestParam("price") double price,
                                                @RequestParam("templateId") Long templateId,
                                                @RequestParam("image") Optional<MultipartFile> image) {
        return handleUnit(new UnitDTO(propertyId, ref, unitType, size, new MeasurementUnitsDTO(measurementUnits.getId(), measurementUnits.getName()), utilities, leaseMode, price, currency.orElse(null), templateId),
                (dto, img) -> propertyService.editUnit(unitId, dto, img), image.orElse(null), HttpStatus.OK);
    }

    @PostMapping("/charges")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_UNIT_CHARGES)")
    public ResponseEntity<ResponseDTO> updateUnitCharges(@Valid @RequestBody UnitChargesDTO unitChargesDTO) {
        ResponseDTO responseDTO = propertyService.updateUnitCharges(unitChargesDTO);
        return responseDTO.isSuccess() ? ResponseEntity.ok(responseDTO) : ResponseEntity.status(HttpStatus.CONFLICT).body(responseDTO);
    }

    @GetMapping("/charges")
    @PreAuthorize("#token.isPresent() || hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_UNIT_CHARGES)")
    public ResponseEntity<ResponseDTO> getUnitCharges(@RequestParam Optional<String> token, @RequestParam long unitId) {
        ResponseDTO responseDTO = propertyService.getUnitCharges(token.orElse(null), unitId);
        return responseDTO.isSuccess() ? ResponseEntity.ok(responseDTO) : ResponseEntity.status(HttpStatus.CONFLICT).body(responseDTO);
    }

    @PatchMapping("/create/similar")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).CREATE_UNIT)")
    public ResponseEntity<ResponseDTO> duplicateUnit(@RequestParam("unitId") long unitId, @RequestParam int count) {
        ResponseDTO responseDTO = propertyService.createDuplicateJob(unitId, count);
        return responseDTO.isSuccess() ? ResponseEntity.ok(responseDTO) : ResponseEntity.status(HttpStatus.CONFLICT).body(responseDTO);
    }

    @GetMapping("/create/similar/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_UNIT)")
    public ResponseEntity<ResponseDTO> getUnitCreationJobs(Pageable pageable) {
        ResponseDTO responseDTO = propertyService.getUnitCreationJobList(pageable);
        return responseDTO.isSuccess() ? ResponseEntity.ok(responseDTO) : ResponseEntity.status(HttpStatus.CONFLICT).body(responseDTO);
    }

    @GetMapping("/create/similar/count")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_UNIT)")
    public ResponseEntity<ResponseDTO> getPendingUnitCreationJobs() {
        ResponseDTO responseDTO = propertyService.getPendingUnitCreationJobs();
        return responseDTO.isSuccess() ? ResponseEntity.ok(responseDTO) : ResponseEntity.status(HttpStatus.CONFLICT).body(responseDTO);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).DELETE_UNIT)")
    public ResponseEntity<ResponseDTO> deleteUnit(@RequestParam long unitId) {
        ResponseDTO responseDTO = propertyService.deleteUnit(unitId);
        return responseDTO.isSuccess() ? ResponseEntity.ok(responseDTO) : ResponseEntity.status(HttpStatus.CONFLICT).body(responseDTO);
    }

    @PatchMapping("/{unitId}/advertise-toggle")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).ADVERTISE_UNIT)")
    public ResponseEntity<ResponseDTO> advertiseUnit(@PathVariable("unitId") long unitId) {
        ResponseDTO responseDTO = propertyService.advertiseUnit(unitId);
        return responseDTO.isSuccess() ? ResponseEntity.ok(responseDTO) : ResponseEntity.status(HttpStatus.NOT_FOUND).body(responseDTO);
    }

    @PutMapping("/images")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).EDIT_UNIT)")
    public ResponseEntity<ResponseDTO> uploadUnitImages(@RequestParam("unitId") long unitId, @RequestParam("images") List<MultipartFile> images) {
        return ResponseEntity.ok(propertyService.uploadUnitSliderImages(unitId, images));
    }

    @GetMapping("/tenants")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_TENANTS)")
    public ResponseEntity<ResponseDTO> listUnitTenants(Pageable pageable, @RequestParam("unitId") long unitId) {
        Page<UnitTenantProjection> unitTenantProjections = propertyService.listUnitTenants(pageable, unitId);
        ResponseDTO responseDTO = new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), unitTenantProjections.toList());
        responseDTO.setTotalElements(unitTenantProjections.getTotalElements());
        responseDTO.setTotalPages(unitTenantProjections.getTotalPages());
        responseDTO.setSize(unitTenantProjections.getSize());
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping("/managers")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_LANDLORD_AND_MANAGERS)")
    public ResponseEntity<ResponseDTO> listUnitLandlordAndManagers(@RequestParam("unitId") long unitId) {
        List<PropertyManagerDetailsDTO> propertyManagerDetailsDTOList = propertyService.listUnitLandlordAndManagers(unitId);
        ResponseDTO responseDTO = new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), propertyManagerDetailsDTOList);
        return ResponseEntity.ok(responseDTO);
    }

    private ResponseEntity<ResponseDTO> handleUnit(
            UnitDTO unitDTO,
            BiFunction<UnitDTO, MultipartFile, ResponseDTO> serviceCall,
            MultipartFile image,
            HttpStatus successStatus) {

        Optional<ResponseDTO> violations = validate(unitDTO);
        if (violations.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(violations.get());
        }

        ResponseDTO response = serviceCall.apply(unitDTO, image);
        return response.isSuccess()
                ? ResponseEntity.status(successStatus).body(response)
                : ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_UNIT)")
    public ResponseEntity<ResponseDTO> getUnitList(@RequestParam Optional<String> search, @RequestParam Optional<Long> propertyId, @RequestParam Optional<Long> unitId, Pageable pageable,
                                                   @RequestParam(required = false) Optional<PMSLeaseMode> leaseMode) {
        return ResponseEntity.ok(propertyService.listUnits(pageable, search, propertyId, unitId, leaseMode));
    }

    @GetMapping("/list/by/tenant")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_UNIT)")
    public ResponseEntity<ResponseDTO> getUnitListByTenant() {
        List<PropertyIdUnitRefPropertyNameProjection> unitList = propertyService.listUnitsByTenant();
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.UNIT_LIST.getCode(), i18NService.getLocalizedMessage(ResponseCode.UNIT_LIST), unitList));
    }

    @GetMapping("/list/by/resident")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_UNIT)")
    public ResponseEntity<ResponseDTO> getUnitListByResident() {
        List<PropertyIdUnitRefPropertyNameProjection> unitList = propertyService.listUnitsByResident();
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.UNIT_LIST.getCode(), i18NService.getLocalizedMessage(ResponseCode.UNIT_LIST), unitList));
    }

    @GetMapping("/list/key/value")
    @PreAuthorize("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_UNIT_LIST)")
    public ResponseEntity<ResponseDTO> getUnitListKeyValue(@RequestParam Optional<String> search, @RequestParam Long propertyId, Pageable pageable) {
        Page<IdNameDescDTO> unitKeyValueDTOS = propertyService.listUnitsKeyValue(pageable, search, propertyId);
        return ResponseEntity.ok(new ResponseDTO(true, ResponseCode.GENERAL_SUCCESS.getCode(), i18NService.getLocalizedMessage(ResponseCode.GENERAL_SUCCESS), unitKeyValueDTOS.toList(),
                unitKeyValueDTOS.getTotalPages(), unitKeyValueDTOS.getTotalElements(), unitKeyValueDTOS.getSize()));
    }
}

