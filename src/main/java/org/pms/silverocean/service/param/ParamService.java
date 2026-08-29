package org.pms.silverocean.service.param;


import jakarta.validation.constraints.NotNull;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.Param;
import org.pms.silverocean.database.pms.entities.PaymentAccountProperty;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.payment.wrappers.AccountPropertyDefinition;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.property.PropertyDao;
import org.pms.silverocean.service.security.DecryptDTO;
import org.pms.silverocean.service.security.EncryptionService;
import org.pms.silverocean.service.wrappers.EnumWrapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ParamService {
    private final AccountDao accountDao;
    private final ConfigService configService;

    private final AuditLogService auditLogService;
    private final UserDao userDao;

    private final PropertyDao propertyDao;
    private final I18NService i18NService;

    private final EncryptionService encryptionService;

    private final NotificationService notificationService;


    public ParamService(AccountDao accountDao, ConfigService configService, AuditLogService auditLogService, UserDao userDao, PropertyDao propertyDao, I18NService i18NService, EncryptionService encryptionService, NotificationService notificationService) {
        this.accountDao = accountDao;
        this.configService = configService;
        this.auditLogService = auditLogService;
        this.userDao = userDao;
        this.propertyDao = propertyDao;
        this.i18NService = i18NService;
        this.encryptionService = encryptionService;
        this.notificationService = notificationService;
    }


    public List<ParamGroupDTO> getParamByLoggedInUser() {
//        Map<String, List<Param>> params = paramDao.loadParamsForUser(userDao.getUserId()).stream().collect(Collectors.groupingBy(Param::getName));
//
//        return params.entrySet().stream().map(entry -> {
//            List<ParamDTO> paramList = entry.getValue().stream().map(ParamDTO::new).toList();
//            String channelName = PaymentChannel.findChannelByPropertyLabelKey(entry.getValue().get(0).getType()).getName();
//            return new ParamGroupDTO(entry.getKey(), channelName, paramList, entry.getValue().get(0).isVerified());
//        }).collect(Collectors.toList());
        return List.of();
    }

    public Page<ParamGroupDTO> getAllParams(Pageable pageable, String filter) {
        //TODO clean this up
        return Page.empty();
    }

    public Set<EnumWrapper> getSupportedParamTypes() {
        //TODO clean this up
        return Set.of();
    }

    public void verifyParam(String groupName, boolean verify) {
        //TODO clean this up
    }

    @Transactional
    public void updateParam(ParamGroupDTO paramGroupDTO) {
        //TODO clean this up
    }

    public ParamGroupDTO getDecryptedParamValue(String groupName) {
        List<Param> paramByGroupName = List.of();
        if (paramByGroupName.isEmpty()) {
            throw new PMSCustomException(ResponseCode.PARAM_NOT_FOUND_ERROR);
        }
        List<ParamDTO> paramDTOList = paramByGroupName.stream().map(paramFromDb -> {
            AccountPropertyDefinition prop = PaymentChannel.findPropertyByLabelKey(paramFromDb.getType());
            String channelName = PaymentChannel.findChannelByPropertyLabelKey(paramFromDb.getType()).getName();
            if (prop.encrypted()) {
                return new ParamDTO(paramFromDb.getId(), prop, channelName, "");
            } else {
                return new ParamDTO(paramFromDb.getId(), prop, channelName, new String(paramFromDb.getValue(), StandardCharsets.UTF_8));
            }
        }).collect(Collectors.toList());

        String channelName = PaymentChannel.findChannelByPropertyLabelKey(paramByGroupName.get(0).getType()).getName();
        return new ParamGroupDTO(paramByGroupName.get(0).getName(), channelName, paramDTOList, paramByGroupName.get(0).isVerified());
    }

    public void createParam(ParamGroupDTO paramGroupDTO) {

    }

    public void deleteParamGroup(String groupName) {
        //TODO clean this up
    }

    public String getParamByAccountIdAndType(long accountId, AccountPropertyDefinition prop, long propertyId) {
        Optional<PaymentAccountProperty> paramFromDbOptional = propertyDao.getParamByAccountIdAndTypeAndPropertyId(accountId, prop.key(), propertyId);
        if (paramFromDbOptional.isPresent()) {
            return paramFromDbOptional.map(paramFromDb -> prop.encrypted()
                    ? getParamAndUpdateIfEncryptedWithOldKey(paramFromDb)
                    : new String(paramFromDb.getValue())).orElseThrow();
        }
        throw new PMSCustomException(ResponseCode.PARAM_NOT_FOUND_ERROR);
    }

    private @NotNull String getParamAndUpdateIfEncryptedWithOldKey(PaymentAccountProperty paramFromDb) {
        DecryptDTO decryptedParam = encryptionService.decrypt(paramFromDb.getValue());
        if (decryptedParam.usedOldKey()) {
            paramFromDb.setValue(encryptionService.encrypt(decryptedParam.decryptedValue()));
            paramFromDb.setLastModifiedDate(LocalDateTime.now());
            accountDao.upsertProperty(paramFromDb);
        }
        return decryptedParam.decryptedValue();
    }

}
