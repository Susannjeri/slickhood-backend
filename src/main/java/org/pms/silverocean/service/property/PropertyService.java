package org.pms.silverocean.service.property;


import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.controller.wrappers.UnitChargesDTO;
import org.pms.silverocean.database.pms.entities.BulkUnitJob;
import org.pms.silverocean.database.pms.entities.Param;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.PropertyAccount;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.UnitCharge;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.lease.wrappers.LeaseIdTenantSignDateDTO;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.param.ParamDao;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.property.charges.FormattedUnitChargesDTO;
import org.pms.silverocean.service.property.charges.PMSChargeTypes;
import org.pms.silverocean.service.property.charges.PMSPeriod;
import org.pms.silverocean.service.property.wrappers.DbUnitDTO;
import org.pms.silverocean.service.property.wrappers.DuplicateUnitJobDTO;
import org.pms.silverocean.service.property.wrappers.PropertyAccountDTO;
import org.pms.silverocean.service.property.wrappers.PropertyDTO;
import org.pms.silverocean.service.property.wrappers.PropertyManagerDetailsDTO;
import org.pms.silverocean.service.property.wrappers.PropertyViewDTO;
import org.pms.silverocean.service.property.wrappers.UnitDTO;
import org.pms.silverocean.service.property.wrappers.UnitTenantProjection;
import org.pms.silverocean.service.property.wrappers.UtilitiesDTO;
import org.pms.silverocean.service.threadpooling.PMSThreadPoolExecutorService;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.pms.silverocean.service.visitor.projections.PropertyIdUnitRefPropertyNameProjection;
import org.pms.silverocean.service.wrappers.EnumWrapper;
import org.pms.silverocean.service.wrappers.IdNameDescDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PropertyService {
    public static final String SLIDERIMAGES = "sliderimages";
    private static final String IMAGE_UPLOAD_EXECUTOR = "upload-images";
    private final PropertyDao propertyDao;

    private final UnitDao unitDao;

    private final UnitTypeDao unitTypeDao;

    private final UserDao userDao;

    private final I18NService i18NService;

    private final ParamDao paramDao;
    private final AuditLogService auditLogService;

    private final ConfigService configService;

    private final PMSMeasurementUnitsConverter measurementUnitConverter;

    private final PropertyRoutines propertyRoutines;

    private final GarageService garageService;

    private final ThreadPoolBeans threadPoolBeans;

    private final PaymentPlatformFactory paymentPlatformFactory;

    private final AccountDao accountDao;

    @Value("${min.upload.image.width:300}")
    private int imageWidth;
    @Value("${min.upload.image.height:200}")
    private int imageHeight;

    @Value("${silverocean.dir}")
    private String appDir;

    private final static String PROPERTY_ID_PREFIX = PMSUtils.ID_PREFIX + "112";
    private final static String IMAGE_DIR = "/images";
    private Path globalImagePath;

    private PMSThreadPoolExecutorService threadPoolExecutorService;


    @PostConstruct
    public void init() {
        globalImagePath = Paths.get(appDir + IMAGE_DIR);
        PMSUtils.createDirectoryIfNotExists(globalImagePath.toAbsolutePath().toString());
        loadIncompleteDuplicateUnitJobs();
        threadPoolExecutorService = threadPoolBeans.ioExecutorService(IMAGE_UPLOAD_EXECUTOR);
    }

    public ResponseDTO getSupportedPropertyTypes(String filter) {
        Set<EnumWrapper> propertyTypes = PMSPropertyType.search(filter, i18NService).stream()
                .map(type -> new EnumWrapper(type.name(), i18NService.getLocalizedMessage(type.getDisplayNamePlaceHolder()), i18NService.getLocalizedMessage(type.getDescriptionPlaceHolder())))
                .collect(Collectors.toSet());
        return new ResponseDTO(true, ResponseCode.PROPERTY_TYPES.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.PROPERTY_TYPES), propertyTypes);
    }

    public ResponseDTO getUnitTypes(PMSPropertyType propertyType) {
        Set<EnumWrapper> unitTypes = unitTypeDao.getByPropertyType(propertyType).stream()
                .map(pmsUnitType -> {
                    return new EnumWrapper(pmsUnitType.name(),
                            i18NService.getLocalizedMessage(pmsUnitType.getName()),
                            i18NService.getLocalizedMessage(pmsUnitType.getDescription()));
                })
                .collect(Collectors.toSet());
        return new ResponseDTO(true, ResponseCode.UNIT_TYPES.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.UNIT_TYPES), unitTypes);
    }

    public ResponseDTO getSupportedUtilities() {
        List<IdNameDescDTO> unitTypes = unitDao.getSupportedUtilities().stream()
                .map(utility -> new IdNameDescDTO(utility.getId(), i18NService.getLocalizedMessage(utility.getName())))
                .collect(Collectors.toList());
        return new ResponseDTO(true, ResponseCode.UTILITIES.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.UTILITIES), unitTypes);
    }

    public ResponseDTO getSupportedChargeTypes() {
        List<IdNameDescDTO> unitTypes = unitDao.getSupportedChargeTypes().stream()
                .map(chargeType -> new IdNameDescDTO(chargeType.getId(), i18NService.getLocalizedMessage(PMSChargeTypes.valueOf(chargeType.getName()).getName())))
                .collect(Collectors.toList());
        return new ResponseDTO(true, ResponseCode.CHARGE_TYPES.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.CHARGE_TYPES), unitTypes);
    }

    public ResponseDTO getSupportedPeriods() {
        List<EnumWrapper> unitTypes = EnumSet.allOf(PMSPeriod.class).stream()
                .map(pmsPeriod -> new EnumWrapper(pmsPeriod.name(), i18NService.getLocalizedMessage(pmsPeriod.getName()), null))
                .collect(Collectors.toList());
        return new ResponseDTO(true, ResponseCode.PERIOD.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.PERIOD), unitTypes);
    }

    public ResponseDTO getSupportedMeasurement() {
        Set<MeasurementUnitsDTO> measurementUnits = EnumSet.allOf(PMSMeasurementUnits.class).stream()
                .map(units -> new MeasurementUnitsDTO(units.getId(), i18NService.getLocalizedMessage(units.getName())))
                .collect(Collectors.toSet());
        return new ResponseDTO(true, ResponseCode.MEASUREMENT_UNITS.getCode(), i18NService.getLocalizedMessage(ResponseCode.MEASUREMENT_UNITS), measurementUnits);
    }

    public ResponseDTO createProperty(PropertyDTO propertyDTO, MultipartFile image) {
        Users user = userDao.getUserObject();
        if (!user.isCompletedProfile()) {
            throw new PMSCustomException(ResponseCode.INCOMPLETE_USER_PROFILE, user.getProfileCompletenessState());
        }
        try {
            Optional<ResponseDTO> imageValidationError = validateImage(image);
            if (imageValidationError.isPresent()) {
                return imageValidationError.get();
            }

            Property property = new Property(propertyDTO);
            property.setActive(true);
            property.setCreatedBy(user.getId());

            propertyDao.save(property);
            property.setRef(PROPERTY_ID_PREFIX + Long.toHexString(property.getId()));
            savePropertyImage(property, image, false);

            return new ResponseDTO(true, ResponseCode.PROPERTY_CREATION_SUCCESS.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.PROPERTY_CREATION_SUCCESS), Set.of(property.getId()));
        } catch (PMSCustomException e) {
            log.error(e.getMessage(), e);
        } catch (IOException e) {
            log.error("Error reading uploaded image", e);
            return new ResponseDTO(false,
                    ResponseCode.INVALID_IMAGE.getCode(), i18NService.getLocalizedMessage(ResponseCode.INVALID_IMAGE));
        }
        return new ResponseDTO(false, ResponseCode.PROPERTY_CREATION_FAILED_DUPLICATE.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.PROPERTY_CREATION_FAILED_DUPLICATE));
    }

    public ResponseDTO createUnit(UnitDTO unitDTO, MultipartFile image) {
        Pair<ResponseDTO, Property> validationResult = validateUnitAndImage(unitDTO, image);
        if (validationResult.getLeft() != null) {
            return validationResult.getLeft();
        }
        Property property = validationResult.getRight();
        Unit unit = new Unit(unitDTO);
        unit.setCreatedBy(userDao.getUserId());
        if (StringUtils.isBlank(unitDTO.currency())) {
            unit.setCurrency(property.getCurrency());
        }
        try {
            unitDao.save(unit);
            saveUnitImage(unit, image, false);
            if (!property.isHasUnits()) {
                property.setHasUnits(true);
                propertyDao.update(property);
            }
            return new ResponseDTO(true, ResponseCode.UNIT_CREATION_SUCCESS.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.UNIT_CREATION_SUCCESS), Set.of(unit.getId()));
        } catch (PMSCustomException e) {
            log.error(e.getMessage(), e);
        } catch (IOException e) {
            log.error("Error reading uploaded image", e);
            return new ResponseDTO(false,
                    ResponseCode.INVALID_IMAGE.getCode(), i18NService.getLocalizedMessage(ResponseCode.INVALID_IMAGE));
        }
        return new ResponseDTO(false, ResponseCode.UNIT_CREATION_FAILED_DUPLICATE.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.UNIT_CREATION_FAILED_DUPLICATE));
    }

    public ResponseDTO updateUnitCharges(@RequestBody UnitChargesDTO unitChargesDTO) {
        Optional<Unit> unitFromDb = unitDao.findByIdAndCreatedBy(unitChargesDTO.unitId(), userDao.getUserId());
        if (unitFromDb.isEmpty()) {
            return new ResponseDTO(false, ResponseCode.UNIT_NOT_FOUND.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.UNIT_NOT_FOUND));
        }
        Set<UnitCharge> newUnitCharges = unitChargesDTO.charges().stream().map(dto -> {
            UnitCharge unitCharge = new UnitCharge(dto);
            unitCharge.setUnitId(unitChargesDTO.unitId());
            return unitCharge;
        }).collect(Collectors.toSet());
        unitDao.updateUnitCharges(unitChargesDTO.unitId(), newUnitCharges);
        return new ResponseDTO(true, ResponseCode.UPDATED_UNIT_CHARGES.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.UPDATED_UNIT_CHARGES));
    }

    public ResponseDTO getUnitCharges(String token, long unitId) {
        if (StringUtils.isNotBlank(token)) {
            Unit unit = unitDao.findByToken(token).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_OR_EXPIRED_TOKEN));
            if (unit.getId() != unitId) {
                throw new PMSCustomException(ResponseCode.UNIT_NOT_FOUND);
            }
        } else if (unitDao.findByIdAndStaffOrOwnerOrTenant(unitId, userDao.getUserId()).isEmpty()) {
            throw new PMSCustomException(ResponseCode.UNIT_NOT_FOUND);
        }
        List<FormattedUnitChargesDTO> unitCharges = unitDao.getUnitCharges(unitId)
                .stream().map(unitCharge -> new FormattedUnitChargesDTO(unitCharge.getId(), unitCharge.getCreatedOn(),
                        unitCharge.getChargeId(), i18NService.getLocalizedMessage(PMSChargeTypes.valueOf(unitCharge.getChargeName()).getName()), unitCharge.getAmount(), unitCharge.getPeriodId(),
                        i18NService.getLocalizedMessage(PMSPeriod.valueOf(unitCharge.getPeriodId()).getName()))).collect(Collectors.toList());
        return new ResponseDTO(true, ResponseCode.UNIT_CHARGES.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.UNIT_CHARGES), unitCharges);
    }

    public ResponseDTO createDuplicateJob(long unitId, int count) {
        if (count > configService.getConfigByName(PMSConfigs.MAX_UNIT_DUPLICATE_COUNT).get().intValue()) {
            return new ResponseDTO(false, ResponseCode.NUMBER_EXCEEDS_ALLOWED_LIMIT.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.NUMBER_EXCEEDS_ALLOWED_LIMIT));
        }
        Optional<Unit> unitFromDb = unitDao.findByIdAndCreatedBy(unitId, userDao.getUserId());
        return unitFromDb.map(unit -> {
            BulkUnitJob bulkUnitJob = new BulkUnitJob();
            bulkUnitJob.setUnitId(unit.getId());
            bulkUnitJob.setCount(count);
            bulkUnitJob.setEmail(userDao.getEmail());
            bulkUnitJob.setDescription(i18NService.getLocalizedMessage(ResponseCode.CREATE_SIMILAR_UNIT_JOB.getDescription()));
            bulkUnitJob.setCompleted(false);
            bulkUnitJob.setCreatedBy(userDao.getUserId());

            unitDao.createBulkUnitJob(bulkUnitJob);

            propertyRoutines.scheduleDuplicateUnitJob(bulkUnitJob.getId(), () -> runDuplicateJob(bulkUnitJob.getId()));
            return new ResponseDTO(true, ResponseCode.CREATE_SIMILAR_UNIT_JOB.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.CREATE_SIMILAR_UNIT_JOB));
        }).orElseGet(() -> new ResponseDTO(false, ResponseCode.UNIT_NOT_FOUND.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.UNIT_NOT_FOUND)));

    }

    public ResponseDTO getUnitCreationJobList(Pageable pageable) {
        Page<BulkUnitJob> bulkUnitJobs = unitDao.listBulkUnitJob(pageable, userDao.getUserId());
        ResponseDTO responseDTO = new ResponseDTO(true, ResponseCode.CREATE_SIMILAR_UNITS_JOB_LIST.getCode(), i18NService.getLocalizedMessage(ResponseCode.CREATE_SIMILAR_UNITS_JOB_LIST), bulkUnitJobs.toList());
        responseDTO.setTotalElements(bulkUnitJobs.getTotalElements());
        responseDTO.setTotalPages(bulkUnitJobs.getTotalPages());
        responseDTO.setSize(bulkUnitJobs.getSize());
        return responseDTO;
    }

    public ResponseDTO getPendingUnitCreationJobs() {
        int count = unitDao.countPendingBulkUnitJob(userDao.getUserId());
        return new ResponseDTO(true, ResponseCode.CREATE_SIMILAR_UNITS_JOB_LIST.getCode(), i18NService.getLocalizedMessage(ResponseCode.CREATE_SIMILAR_UNITS_JOB_LIST), count);
    }

    public ResponseDTO deleteUnit(long unitId) {
        Optional<Unit> unitFromDb = unitDao.findByIdAndCreatedBy(unitId, userDao.getUserId());
        return unitFromDb.map(unit -> {
            if (unit.isOccupied()) {
                unitDao.logDeleteUnitFailure(unit, i18NService.getLocalizedMessage(ResponseCode.UNIT_IS_OCCUPIED));
                return new ResponseDTO(false, ResponseCode.UNIT_IS_OCCUPIED.getCode(),
                        i18NService.getLocalizedMessage(ResponseCode.UNIT_IS_OCCUPIED));
            }
            unitDao.delete(unit);
            garageService.deletePath(unit.getImagePath(), true);
            int countRemainingUnits = unitDao.countActiveByPropertyId(unit.getPropertyId());
            if (countRemainingUnits < 1) {
                Property property = propertyDao.findByIdAndCreatedBy(unit.getPropertyId(), unit.getCreatedBy()).orElse(null);
                if (property != null) {
                    property.setHasUnits(false);
                    propertyDao.update(property);
                }
            }
            return new ResponseDTO(true, ResponseCode.UNIT_DELETED_SUCCESS.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.UNIT_DELETED_SUCCESS));
        }).orElseGet(() -> new ResponseDTO(false, ResponseCode.UNIT_NOT_FOUND.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.UNIT_NOT_FOUND)));
    }

    public ResponseDTO editUnit(long unitId, UnitDTO unitDTO, MultipartFile image) {
        Optional<Property> targetProperty = propertyDao.findByIdAndCreatedBy(unitDTO.propertyId(), userDao.getUserId());
        if (targetProperty.isEmpty()) {
            return new ResponseDTO(false, ResponseCode.UNIT_CREATION_FAILED_MISSING_PROPERTY.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.UNIT_CREATION_FAILED_MISSING_PROPERTY));
        }

        if (image != null && image.getSize() > 0) {
            Optional<ResponseDTO> imageValidationError = validateImage(image);
            if (imageValidationError.isPresent()) {
                return imageValidationError.get();
            }
        }
        Property property = targetProperty.get();

        //get unit from db
        Optional<Unit> unitFromDb = unitDao.findByIdAndCreatedBy(unitId, userDao.getUserId());
        if (unitFromDb.isEmpty()) {
            return new ResponseDTO(false, ResponseCode.UNIT_NOT_FOUND.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.UNIT_NOT_FOUND));
        }
        Unit unit = unitFromDb.get();
        unit.updateFromDto(unitDTO);
        try {
            saveUnitImage(unit, image, true);
            unitDao.update(unit);
            if (property != null && !property.isHasUnits()) {
                property.setHasUnits(true);
                propertyDao.update(property);
            }
            return new ResponseDTO(true, ResponseCode.UNIT_EDIT_SUCCESS.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.UNIT_EDIT_SUCCESS), Set.of(unitId));
        } catch (PMSCustomException e) {
            log.error(e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            log.error("Error reading uploaded image", e);
            return new ResponseDTO(false,
                    ResponseCode.INVALID_IMAGE.getCode(), i18NService.getLocalizedMessage(ResponseCode.INVALID_IMAGE));
        }
    }

//    public ResponseDTO markOccupiedUnit(long unitId) {
//        Optional<Unit> unitFromDb = unitDao.findByIdAndCreatedBy(unitId, userDao.getUserId());
//        if (unitFromDb.isEmpty()) {
//            return new ResponseDTO(false, ResponseCode.UNIT_NOT_FOUND.getCode(),
//                    i18NService.getLocalizedMessage(ResponseCode.UNIT_NOT_FOUND));
//        }
//        Unit unit = unitFromDb.get();
//        unit.setOccupied(!unit.isOccupied());
//        unitDao.update(unit);
//        return new ResponseDTO(true, ResponseCode.UNIT_OCCUPATION_STATUS_UPDATED.getCode(),
//                i18NService.getLocalizedMessage(ResponseCode.UNIT_OCCUPATION_STATUS_UPDATED), Set.of(unitId));
//    }

    public ResponseDTO advertiseUnit(long unitId) {
        Optional<Unit> unitFromDb = unitDao.findByIdAndCreatedBy(unitId, userDao.getUserId());
        if (unitFromDb.isEmpty()) {
            return new ResponseDTO(false, ResponseCode.UNIT_NOT_FOUND.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.UNIT_NOT_FOUND));
        }
        Unit unit = unitFromDb.get();
        unit.setAdvertise(!unit.isAdvertise());
        unitDao.update(unit);
        return new ResponseDTO(true, ResponseCode.UNIT_ADVERTISE.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.UNIT_ADVERTISE), Set.of(unitId));
    }

    public Page<UnitTenantProjection> listUnitTenants(Pageable pageable, long unitId) {
        return unitDao.findUnitTenantsByUnitIdAndOwnerOrPropertyManager(pageable, unitId, userDao.getUserId());
    }

    public List<PropertyManagerDetailsDTO> listUnitLandlordAndManagers(long unitId) {
        if (unitDao.findByIdAndStaffOrOwnerOrTenant(unitId, userDao.getUserId()).isEmpty()) {
            throw new PMSCustomException(ResponseCode.UNIT_NOT_FOUND);
        }
        List<PropertyManagerDetailsDTO> propertyManagers = unitDao.findPropertyManagersByUnit(unitId).stream()
                .map(user -> new PropertyManagerDetailsDTO(user.getFullName(), user.getPhoneNumber(), user.getEmail(), PMSRole.PROPERTY_MANAGER.getName())).toList();
        Users landlordDetails = unitDao.getLandlordDetails(unitId);
        List<PropertyManagerDetailsDTO> propertyManagersCopy = new ArrayList<>(propertyManagers);
        propertyManagersCopy.add(new PropertyManagerDetailsDTO(landlordDetails.getFullName(), landlordDetails.getPhoneNumber(), landlordDetails.getEmail(), PMSRole.LANDLORD.getName()));
        return propertyManagersCopy;
    }

    public ResponseDTO uploadUnitSliderImages(long unitId, List<MultipartFile> images) {
        Optional<Unit> unitFromDb = unitDao.findByIdAndCreatedBy(unitId, userDao.getUserId());
        if (unitFromDb.isEmpty()) {
            return new ResponseDTO(false, ResponseCode.UNIT_NOT_FOUND.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.UNIT_NOT_FOUND));
        }
        Unit unit = unitFromDb.get();
        threadPoolExecutorService.submit(() -> processImageUploadsAsync(unit.getCreatedBy(), unit.getPropertyId(), unit.getId(), images));
        return new ResponseDTO(true, ResponseCode.IMAGES_UPLOADED.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.IMAGES_UPLOADED), Set.of(unitId));
    }

    public void processImageUploadsAsync(Long createdBy, Long propertyId, Long unitId, List<MultipartFile> images) {
        Path filePath = Paths.get(String.format("%d/%d/%d/%s/", createdBy, propertyId, unitId, SLIDERIMAGES));

        try {
            garageService.deletePath(filePath.toString(), true);

            for (MultipartFile image : images) {
                Optional<ResponseDTO> validationResponse = validateImage(image);
                if (validationResponse.isEmpty()) {
                    garageService.uploadFile(filePath.toString(), image);
                } else {
                    log.warn("Skipping invalid image: {}", image.getOriginalFilename());
                }
            }
            log.info("Successfully completed async upload for Unit: {}", unitId);
        } catch (Exception ex) {
            log.error("Async upload failed for Unit: {}. Error: {}", unitId, ex.getMessage());
        }
    }

    public ResponseDTO updateProperty(long propertyId, PropertyDTO propertyDTO, MultipartFile image) {
        Optional<Property> propertyFromDb = propertyDao.findByIdAndCreatedBy(propertyId, userDao.getUserId());
        if (propertyFromDb.isEmpty()) {
            return new ResponseDTO(false, ResponseCode.PROPERTY_NOT_FOUND.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.PROPERTY_NOT_FOUND));
        }

        try {
            Property property = propertyFromDb.get();
            property.updateFromDto(propertyDTO);
            if (image != null && image.getSize() > 0) {
                Optional<ResponseDTO> imageValidationError = validateImage(image);
                if (imageValidationError.isPresent()) {
                    return imageValidationError.get();
                }
                savePropertyImage(property, image, true);
            }
            propertyDao.update(property);
            return new ResponseDTO(true, ResponseCode.PROPERTY_UPDATED_SUCCESS.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.PROPERTY_UPDATED_SUCCESS), Set.of(propertyId));
        } catch (IOException e) {
            log.error("Error reading uploaded image", e);
            return new ResponseDTO(false,
                    ResponseCode.INVALID_IMAGE.getCode(), i18NService.getLocalizedMessage(ResponseCode.INVALID_IMAGE));
        }
    }

    private Pair<ResponseDTO, Property> validateUnitAndImage(UnitDTO unitDTO, MultipartFile image) {
        Optional<Property> propertyOptional = propertyDao.findByIdAndCreatedBy(unitDTO.propertyId(), userDao.getUserId());
        if (propertyOptional.isEmpty()) {
            return Pair.of(new ResponseDTO(false, ResponseCode.UNIT_CREATION_FAILED_MISSING_PROPERTY.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.UNIT_CREATION_FAILED_MISSING_PROPERTY)), null);
        }
        Optional<ResponseDTO> imageValidationError = validateImage(image);
        if (imageValidationError.isPresent()) {
            return Pair.of(imageValidationError.get(), null);
        }
        return Pair.of(null, propertyOptional.get());
    }

    private Optional<ResponseDTO> validateImage(MultipartFile image) {
        try {
            BufferedImage bufferedImage = ImageIO.read(image.getInputStream());
            if (bufferedImage == null) {
                return Optional.of(new ResponseDTO(false,
                        ResponseCode.INVALID_IMAGE.getCode(),
                        i18NService.getLocalizedMessage(ResponseCode.INVALID_IMAGE)));
            }

            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            if (width < imageWidth || height < imageHeight) {
                return Optional.of(new ResponseDTO(false,
                        ResponseCode.IMAGE_TOO_SMALL.getCode(),
                        String.format(i18NService.getLocalizedMessage(ResponseCode.IMAGE_TOO_SMALL), imageWidth, imageHeight)));
            }

            return Optional.empty(); // means "valid"
        } catch (IOException e) {
            log.error("Error reading uploaded image", e);
            return Optional.of(new ResponseDTO(false,
                    ResponseCode.INVALID_IMAGE.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.INVALID_IMAGE)));
        }
    }


    public ResponseDTO listProperty(Pageable pageable, Optional<String> searchParam, Optional<Long> propertyId, BiFunction<Property, Long, String> getUserRoleInProperty) {
        Users user = userDao.getUserObject();
        if (!user.isCompletedProfile()) {
            throw new PMSCustomException(ResponseCode.INCOMPLETE_USER_PROFILE, user.getProfileCompletenessState());
        }

        if (propertyId != null && propertyId.isPresent()) {
            return getPropertyByIdAndOwnerOrStaff(propertyId.get());
        }
        Page<PropertyDTO> filteredProperty = propertyDao.findAll(searchParam, true, user.getId(), userDao.getActiveRole(), pageable, getUserRoleInProperty, garageService::getPresignedUrl);
        return new ResponseDTO(true, ResponseCode.PROPERTY_LIST.getCode(), i18NService.getLocalizedMessage(ResponseCode.PROPERTY_LIST), filteredProperty.toList(),
                filteredProperty.getTotalPages(), filteredProperty.getTotalElements(), filteredProperty.getSize());
    }

    public Page<IdNameDescDTO> listPropertyListForKeyValue(Pageable pageable, Optional<String> searchParam) {
        Users user = userDao.getUserObject();
        if (!user.isCompletedProfile()) {
            throw new PMSCustomException(ResponseCode.INCOMPLETE_USER_PROFILE, user.getProfileCompletenessState());
        }
        if (userDao.hasRole(PMSRole.SUPER_ADMIN)) {
            return propertyDao.findAllForKeyValue(searchParam, true, null, PMSRole.SUPER_ADMIN, pageable);
        }
        return propertyDao.findAllForKeyValue(searchParam, true, user.getId(), userDao.getActiveRole(), pageable);
    }

    private ResponseDTO getPropertyByIdAndOwnerOrStaff(long propertyId) {
        PMSRole activeRole = userDao.getActiveRole();
        Optional<Property> accessible = switch (activeRole) {
            case LANDLORD -> propertyDao.findByIdAndCreatedBy(propertyId, userDao.getUserId());
            case TENANT -> propertyDao.findByIdAndTenant(propertyId, userDao.getUserId());
            case PROPERTY_MANAGER, WORKSPACE_ADMIN, PROPERTY_ACCOUNTANT, LEASING_OFFICER,
                 ESTATE_OPERATIONS_MANAGER, SECURITY_SUPERVISOR, SALES_COORDINATOR,
                 LISTING_AGENT, WORKSPACE_VIEWER, ESTATE_MANAGER, SALES_AGENT, GUARD -> propertyDao.findByIdAndManagerRole(propertyId, userDao.getUserId(), activeRole.name());
            case HOMEOWNER -> propertyDao.findByIdAndHomeowner(propertyId, userDao.getUserId());
            case BUYER -> propertyDao.findByIdAndBuyer(propertyId, userDao.getUserId());
            case SUPER_ADMIN -> propertyDao.findById(propertyId).filter(Property::isActive);
            default -> Optional.empty();
        };
        return accessible
                .map(property -> new PropertyViewDTO(property, garageService.getPresignedUrl(property.getImagePath() + "/" + property.getThumbnail())))
                .map(property -> new ResponseDTO(true, ResponseCode.PROPERTY_DETAILS.getCode(),
                        i18NService.getLocalizedMessage(ResponseCode.PROPERTY_DETAILS), property)).orElseGet(() -> new ResponseDTO(false, ResponseCode.PROPERTY_NOT_FOUND.getCode(),
                        i18NService.getLocalizedMessage(ResponseCode.PROPERTY_NOT_FOUND)));
    }

    public ResponseDTO getPropertyByIDAndLoggedInUser(Long propertyId) {
        Optional<Property> propertyFromDb = propertyDao.findByIdAndCreatedBy(propertyId, userDao.getUserId());

        return propertyFromDb.map(property -> new ResponseDTO(true, ResponseCode.PROPERTY_DETAILS.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.PROPERTY_DETAILS), propertyFromDb)).orElseGet(() -> new ResponseDTO(false, ResponseCode.PROPERTY_NOT_FOUND.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.PROPERTY_NOT_FOUND)));
    }

    public ResponseDTO deleteProperty(long propertyId) {
        Optional<Property> propertyFromDb = propertyDao.findByIdAndCreatedBy(propertyId, userDao.getUserId());

        return propertyFromDb.map(property -> {
            if (property.isHasUnits()) {
                Integer occupiedUnits = propertyDao.countOccupiedUnitsWithinProperty(propertyId);
                if (occupiedUnits != null && occupiedUnits > 0) {
                    propertyDao.logFailedDeleteAction(property, i18NService.getLocalizedMessage(ResponseCode.PROPERTY_HAS_OCCUPIED_UNITS));
                    return new ResponseDTO(false, ResponseCode.PROPERTY_HAS_OCCUPIED_UNITS.getCode(),
                            i18NService.getLocalizedMessage(ResponseCode.PROPERTY_HAS_OCCUPIED_UNITS));
                }
                unitDao.deactivateAllUnitsWithinProperty(propertyId);
                propertyDao.delete(property);
                garageService.deletePath(property.getImagePath(), true);
                return new ResponseDTO(true, ResponseCode.PROPERTY_AND_UNITS_DELETED.getCode(),
                        i18NService.getLocalizedMessage(ResponseCode.PROPERTY_AND_UNITS_DELETED));
            }
            propertyDao.delete(property);
            return new ResponseDTO(true, ResponseCode.PROPERTY_DELETED_SUCCESS.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.PROPERTY_DELETED_SUCCESS));
        }).orElseGet(() -> new ResponseDTO(false, ResponseCode.PROPERTY_NOT_FOUND.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.PROPERTY_NOT_FOUND)));
    }

    public ResponseCode savePropertyParam(String groupName, long propertyId) {
        //both param and property have to be created by the same user assigning right now
        long userId = userDao.getUserId();
        List<Param> paramList = paramDao.getParamByGroupNameAndCreatedBy(groupName, userId);
        if (paramList.isEmpty()) {
            throw new PMSCustomException(ResponseCode.PARAM_NOT_FOUND_ERROR);
        }
        propertyDao.findByIdAndCreatedBy(propertyId, userId).orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        List<PropertyAccount> propertyParamsToSave = paramList.stream()
                .peek(param -> {
                    if (!param.isVerified()) {
                        throw new PMSCustomException(ResponseCode.ERROR_ATTACHING_PARAM_TO_PROPERTY_UNVERIFIED);
                    }
                    boolean duplicate = propertyDao.findByPropertyIdAndParamIdAndActiveTrue(propertyId, param.getId()).isPresent()
                            || propertyDao.findByPropertyIdAndParamTypeAndActiveTrue(propertyId, param.getType()).isPresent();
                    if (duplicate) {
                        throw new PMSCustomException(ResponseCode.ERROR_ATTACHING_PARAM_TO_PROPERTY_DUPLICATE);
                    }
                })
                .map(param -> {
                    PropertyAccount propertyAccount = new PropertyAccount();
                    propertyAccount.setPropertyId(propertyId);
                    propertyAccount.setActive(true);
                    propertyAccount.setCreatedBy(userId);
                    return propertyAccount;
                })
                .toList();
//        propertyDao.saveAllPropertyParams(propertyParamsToSave);

//        propertyParamsToSave.forEach(pp ->
//                auditLogService.createAuditLog(pp, Permission.EDIT_PROPERTY_PARAM)
//        );

        return ResponseCode.PARAM_ATTACHED_TO_PROPERTY;
    }

    public void attachAccountToProperty(long accountId, long propertyId) {
        Long userId = userDao.getUserId();
        PaymentAccount paymentAccount = propertyDao.findIfAccountIsAttachable(accountId, userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND));
        if (!paymentAccount.isVerified()) {
            throw new PMSCustomException(ResponseCode.ERROR_ATTACHING_PARAM_TO_PROPERTY_UNVERIFIED);
        }
        propertyDao.findByIdAndCreatedBy(propertyId, userId).orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));

        propertyDao.findPropertyAccountByIdAndProperty(accountId, propertyId).ifPresent(__ -> {
            throw new PMSCustomException(ResponseCode.PARAM_ATTACHED_TO_PROPERTY);
        });

        PropertyAccount propertyAccount = new PropertyAccount();
        propertyAccount.setAccountId(accountId);
        propertyAccount.setPropertyId(propertyId);
        propertyAccount.setActive(true);
        propertyAccount.setCreatedBy(userId);
        propertyDao.saveAccount(propertyAccount);
    }

    public void removeAccountFromProperty(long accountId, long propertyId) {
        PropertyAccount propertyAccount = propertyDao.findPropertyAccountByIdAndProperty(accountId, propertyId)
                .orElseThrow(() ->  new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND));
        if (!Objects.equals(propertyAccount.getCreatedBy(), userDao.getUserId())) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
        }
        propertyDao.removeAccountFromProperty(propertyAccount);
    }

    public void deletePropertyParam(String groupName, long propertyId) {
        requirePropertyStaffOrOwner(propertyId);
        List<Param> paramList = paramDao.getParamByGroupNameAndCreatedBy(groupName, userDao.getUserId());
        if (paramList.isEmpty()) {
            throw new PMSCustomException(ResponseCode.PARAM_NOT_FOUND_ERROR);
        }
        List<PropertyAccount> propertyAccountList = paramList.stream().map(param -> {
            PropertyAccount propertyAccount = propertyDao.findByPropertyIdAndParamIdAndActiveTrue(propertyId, param.getId())
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.PARAM_NOT_FOUND_ERROR));
            propertyAccount.setActive(false);
            return propertyAccount;
        }).toList();
        propertyDao.saveAllPropertyParams(propertyAccountList);
        propertyAccountList.forEach(pp ->
                auditLogService.createAuditLog(pp, Permission.DELETE_PROPERTY_PARAM)
        );
    }

    public Set<PropertyAccountDTO> listPropertyAccounts(long propertyId) {
        requirePropertyStaffOrOwner(propertyId);
        return propertyDao.findPropertyParamByPropertyId(propertyId)
                .stream()
                .filter(account -> PaymentChannel.findPropertyByLabelKey(account.getType()).displayField())
                .map(account -> {
                    PaymentChannel channel = PaymentChannel.findChannelByPropertyLabelKey(account.getType());
                    boolean encrypted = PaymentChannel.findPropertyByLabelKey(account.getType()).encrypted();
                    return new PropertyAccountDTO(account.getName(), channel,
                            encrypted ? "*****" : new String(account.getValue()),
                            paymentPlatformFactory.getChannelImage(channel));
                })
                .collect(Collectors.toSet());
    }

    private void requirePropertyStaffOrOwner(long propertyId) {
        if (propertyDao.findByIdAndStaffOrOwner(propertyId, userDao.getUserId()).isEmpty()) {
            throw new PMSCustomException(ResponseCode.PROPERTY_FORBIDDEN_ACCESS);
        }
    }

    public Page<IdNameDescDTO> findAllLandlords(Pageable pageable, String landlordName) {
        return propertyDao.findPropertyLandlords(pageable, landlordName);
    }

    public Page<IdNameDescDTO> findAllTenants(Pageable pageable, String tenantName) {
        return propertyDao.findPropertyTenants(pageable, tenantName);
    }

    public List<PropertyIdUnitRefPropertyNameProjection> listUnitsByTenant() {
        return unitDao.findByUserIDIsTenant(userDao.getUserId());
    }

    public ResponseDTO listUnits(Pageable pageable, Optional<String> unitRef, Optional<Long> propertyId, Optional<Long> unitId, Optional<PMSLeaseMode> leaseMode) {
        if (unitId != null && unitId.isPresent()) {
            return getPropertyUnitByIdAndOwnerOrStaffOrTenant(unitId.get());
        }
        Page<UnitDTO> filteredUnits = unitDao.findAll(unitRef, propertyId, leaseMode, userDao.getUserId(), userDao.getActiveRole(), pageable).map(unit -> toUnitDTO(new DbUnitDTO(unit), null));
        return new ResponseDTO(true, ResponseCode.UNIT_LIST.getCode(), i18NService.getLocalizedMessage(ResponseCode.UNIT_LIST), filteredUnits.toList(),
                filteredUnits.getTotalPages(), filteredUnits.getTotalElements(), filteredUnits.getSize());
    }

    public Page<IdNameDescDTO> listUnitsKeyValue(Pageable pageable, Optional<String> unitRef, long propertyId) {
        Users user = userDao.getUserObject();
        if (!user.isCompletedProfile()) {
            throw new PMSCustomException(ResponseCode.INCOMPLETE_USER_PROFILE, user.getProfileCompletenessState());
        }
        Long userId = user.getId();
        if (userDao.hasRole(PMSRole.SUPER_ADMIN)) {
            userId = null;
        }

        return unitDao.findAll(unitRef, Optional.of(propertyId), Optional.empty(), userId, userDao.getActiveRole(), pageable).map(unit -> new IdNameDescDTO(unit.getId(), unit.getRef()));
    }

    private ResponseDTO getPropertyUnitByIdAndOwnerOrStaffOrTenant(long unitId) {
        PMSRole activeRole = userDao.getActiveRole();
        Optional<DbUnitDTO> unitFromDb = switch (activeRole) {
            case LANDLORD -> unitDao.findDTOByIdAndCreatedBy(unitId, userDao.getUserId());
            case TENANT -> unitDao.findByIdAndTenant(unitId, userDao.getUserId());
            case HOMEOWNER -> unitDao.findByIdAndHomeowner(unitId, userDao.getUserId());
            case BUYER -> unitDao.findByIdAndBuyer(unitId, userDao.getUserId());
            case PROPERTY_MANAGER, WORKSPACE_ADMIN, PROPERTY_ACCOUNTANT, LEASING_OFFICER,
                 ESTATE_OPERATIONS_MANAGER, SECURITY_SUPERVISOR, SALES_COORDINATOR,
                 LISTING_AGENT, WORKSPACE_VIEWER, ESTATE_MANAGER, SALES_AGENT, GUARD -> unitDao.findByIdAndManagerRole(unitId, userDao.getUserId(), activeRole.name());
            case SUPER_ADMIN -> unitDao.findById(unitId).filter(Unit::isActive).map(DbUnitDTO::new);
            default -> Optional.empty();
        };
        return loadUnitPropertiesAndMapDTO(unitFromDb);
    }

    public ResponseDTO getUnitByIDAndLoggedInUser(Long unitId) {
        Optional<DbUnitDTO> unitFromDb = unitDao.findDTOByIdAndCreatedBy(unitId, userDao.getUserId());
        return loadUnitPropertiesAndMapDTO(unitFromDb);
    }

    public ResponseDTO viewUnitLease(String token) {
        Optional<DbUnitDTO> unitFromDb = unitDao.findDTOByToken(token);

        return loadUnitPropertiesAndMapDTO(unitFromDb);
    }

    private ResponseDTO loadUnitPropertiesAndMapDTO(Optional<DbUnitDTO> unitFromDb) {
        return unitFromDb.map(unit -> {
            List<String> images = StringUtils.isNotBlank(unit.imagePath()) ? getSliderImageUrls(unit.imagePath() + "/" + SLIDERIMAGES) : List.of();
            return new ResponseDTO(true, ResponseCode.UNIT_DETAILS.getCode(),
                    i18NService.getLocalizedMessage(ResponseCode.UNIT_DETAILS), toUnitDTO(unit, images));
        }).orElseGet(() -> new ResponseDTO(false, ResponseCode.UNIT_NOT_FOUND.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.UNIT_NOT_FOUND)));
    }

    public List<String> getSliderImageUrls(String sliderPrefix) {
        List<String> strings = garageService.listFiles(sliderPrefix);
        return strings
                .stream()
                .map(garageService::getPresignedUrl)
                .filter(url -> !url.isEmpty())
                .toList();
    }

    private void savePropertyImage(Property property, MultipartFile image, boolean clearExisting) throws IOException { //unit/erwrwe234242342/sfssf.jpg
        Path filePath = Paths.get(String.format("%d/%d/", property.getCreatedBy(), property.getId()));
        if (clearExisting) {
            garageService.deletePath(filePath.toString(), false);
        }
        garageService.uploadFile(filePath.toString(), image);
        property.setThumbnail(image.getOriginalFilename());
        property.setImagePath(filePath.toString());
        propertyDao.update(property);
    }

    private void saveUnitImage(Unit unit, MultipartFile image, boolean clearExisting) throws IOException {
        if (image == null || image.getSize() == 0) {
            return;
        }
        Path filePath = Paths.get(String.format("%d/%d/%d/", unit.getCreatedBy(), unit.getPropertyId(), unit.getId()));
        if (clearExisting) {
            garageService.deletePath(filePath.toString(), false);
        }

        try {
            garageService.uploadFile(filePath.toString(), image);
        } catch (IOException ex) {
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE, ex);
        }
        unit.setThumbnail(image.getOriginalFilename());
        unit.setImagePath(filePath.toString());
        unitDao.update(unit);
    }

    private DuplicateUnitJobDTO runDuplicateJob(long unitJob) {
        Optional<BulkUnitJob> activeJobById = unitDao.findActiveJobById(unitJob);
        if (activeJobById.isPresent()) {
            BulkUnitJob bulkUnitJob = activeJobById.get();

            log.info("Running Duplicate Job Id: {}", bulkUnitJob.getId());
            Optional<Unit> byIdAndCreatedBy = unitDao.findByIdAndCreatedBy(bulkUnitJob.getUnitId(), bulkUnitJob.getCreatedBy());
            int count = bulkUnitJob.getCount();
            if (byIdAndCreatedBy.isPresent()) {
                Unit unitFromDb = byIdAndCreatedBy.get();
                Set<Unit> units = new HashSet<>();
                ResponseCode jobStatusDescription = ResponseCode.DUPLICATE_UNIT_JOB_FAILED;
                try {
                    while (count-- > 0) {
                        Unit newUnit = duplicateUnitInstance(unitFromDb);
                        newUnit.setRef(unitFromDb.getRef() + bulkUnitJob.getId() + count);
                        units.add(newUnit);
                    }

                    log.info("Done setting up units in memory, total instances {}", units.size());
                    unitDao.batchSave(units, unitFromDb, bulkUnitJob);
                    jobStatusDescription = ResponseCode.DUPLICATE_UNIT_JOB_SUCCESS;
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                } finally {
                    unitDao.batchUpdate(units);
                    bulkUnitJob.setDescription(i18NService.getLocalizedMessage(jobStatusDescription.getDescription()));
                    bulkUnitJob.setCompleted(true);
                    log.info("Bulk job completed, updating status in db for job id {}", bulkUnitJob.getId());
                    unitDao.updateBulkUnitJob(bulkUnitJob);
                }
                return new DuplicateUnitJobDTO(ResponseCode.DUPLICATE_UNIT_JOB_SUCCESS.equals(jobStatusDescription), bulkUnitJob.getEmail());
            }

        }
        return new DuplicateUnitJobDTO(true, null);
    }

    private Unit duplicateUnitInstance(Unit baseUnit) {
        Unit newUnit = new Unit();
        newUnit.setPropertyId(baseUnit.getPropertyId());
        newUnit.setUnitType(baseUnit.getUnitType());
        newUnit.setSize(baseUnit.getSize());
        newUnit.setUtilities(baseUnit.getUtilities());
        newUnit.setMeasurementUnits(baseUnit.getMeasurementUnits());
        newUnit.setLeaseMode(baseUnit.getLeaseMode());
        newUnit.setPrice(baseUnit.getPrice());
        newUnit.setCurrency(baseUnit.getCurrency());
        newUnit.setOccupied(false);
        newUnit.setAdvertise(baseUnit.isAdvertise());
        newUnit.setActive(true);
        newUnit.setCreatedBy(baseUnit.getCreatedBy());
        newUnit.setTemplateId(baseUnit.getTemplateId());
        newUnit.setThumbnail(baseUnit.getThumbnail());
        newUnit.setImagePath(baseUnit.getImagePath());
        return newUnit;
    }

    private UnitDTO toUnitDTO(DbUnitDTO unit, List<String> images) {
        Set<UtilitiesDTO> utilities = Arrays.stream(unit.utilities().split(","))
                .map(id -> unitDao.getUtilities(Long.parseLong(id.strip()))
                        .map(utility ->
                                new UtilitiesDTO(utility.getId(), i18NService.getLocalizedMessage(utility.getName()))
                        )
                        .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_UTILITY_VALUE)))
                .collect(Collectors.toSet());
        PMSMeasurementUnits measurementUnits = Objects.requireNonNull(measurementUnitConverter.convert(String.valueOf(unit.measurementUnits())));
        String thumbNailPath = Objects.toString(unit.imagePath(), "") + "/" + Objects.toString(unit.thumbnail(), "");
        LeaseIdTenantSignDateDTO leaseIdTenantSignDateDTO = loadUnitLeaseIdDependingOnStatus(unit);
        Long leaseId = leaseIdTenantSignDateDTO != null ? leaseIdTenantSignDateDTO.id() : null;
        Boolean tenantSigned = leaseIdTenantSignDateDTO != null && leaseIdTenantSignDateDTO.tenantSignedDate() != null;
        Boolean ownerSigned = leaseIdTenantSignDateDTO != null && leaseIdTenantSignDateDTO.ownerSignedDate() != null;
        return new UnitDTO(unit, garageService.getPresignedUrl(thumbNailPath), utilities, images,
                new MeasurementUnitsDTO(measurementUnits.getId(), i18NService.getLocalizedMessage(measurementUnits.getName())),
                leaseId, tenantSigned, ownerSigned);
    }

    private LeaseIdTenantSignDateDTO loadUnitLeaseIdDependingOnStatus(DbUnitDTO unit) {
        if (unit.occupied()) {
            //get leaseId from unitTenant Object
            return unitDao.getSignedLeaseIdByUnitId(unit.unitId())
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.GENERAL_FAILURE));
        }
        if (userDao.getUserId() == null) {
            return null;
        }
        return unitDao.getLeaseIdByTenantsUserIdAndUnitId(userDao.getUserId(), unit.unitId()).orElse(null);
    }

    public void loadIncompleteDuplicateUnitJobs() {
        Set<BulkUnitJob> incompleteJobs = unitDao.findIncompleteJobs();
        log.info("Incomplete duplicate unit jobs found: {}", incompleteJobs.size());
        incompleteJobs.forEach(job -> propertyRoutines.scheduleDuplicateUnitJob(job.getId(), () -> runDuplicateJob(job.getId())));
    }
}
