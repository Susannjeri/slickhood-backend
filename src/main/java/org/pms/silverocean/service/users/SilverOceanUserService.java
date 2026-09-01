package org.pms.silverocean.service.users;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.controller.wrappers.UpdateUserDetailsDTO;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.entities.UserOTP;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.totp.impl.OTPEncryptionService;
import org.pms.silverocean.service.auth.totp.impl.OtpType;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationChannel;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.wrappers.EnumWrapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SilverOceanUserService {
    private final I18NService i18NService;
    private final UserDao userDao;
    private final UserRoleRepo userRoleRepo;

    private final NotificationService notificationService;
    private final OTPEncryptionService otpEncryptionService;
    @Value("${kyc.manual-identity-entry-enabled:false}")
    private boolean manualIdentityEntryEnabled;
    @Value("${security.otp.resend-cooldown-seconds:60}")
    private int otpResendCooldownSeconds;

    public SilverOceanUserService(I18NService i18NService, UserDao userDao, UserRoleRepo userRoleRepo,
                                  NotificationService notificationService, OTPEncryptionService otpEncryptionService) {
        this.i18NService = i18NService;
        this.userDao = userDao;
        this.userRoleRepo = userRoleRepo;
        this.notificationService = notificationService;
        this.otpEncryptionService = otpEncryptionService;
    }


    public ResponseDTO getUserList(Pageable pageable, Optional<String> searchParam) {
        Page<Users> usersPage = userDao.searchAllUsers(pageable, searchParam);
        List<Long> userIds = usersPage.stream().map(Users::getId).toList();
        Map<Long, List<String>> roleNamesByUser = userIds.isEmpty()
                ? Collections.emptyMap()
                : userRoleRepo.findRoleNamesByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(UserRoleNameDTO::userId,
                        Collectors.mapping(UserRoleNameDTO::roleName,
                                Collectors.collectingAndThen(Collectors.toSet(), roles -> roles.stream().sorted().toList()))));
        Page<UserDTO> filteredUsers = usersPage.map(users -> new UserDTO(users,
                new EnumWrapper(ProfileType.valueOf(users.getProfileType()).name(),
                        i18NService.getLocalizedMessage(ProfileType.valueOf(users.getProfileType()).getName()), null),
                roleNamesByUser.getOrDefault(users.getId(), List.of())));
        return new ResponseDTO(true, ResponseCode.LIST_OF_USERS.getCode(), i18NService.getLocalizedMessage(ResponseCode.LIST_OF_USERS), filteredUsers.toList(),
                filteredUsers.getTotalPages(), filteredUsers.getTotalElements(), filteredUsers.getSize());
    }

    public UserDTO getLoggedInUserDetails() {
        Users loggedInUser = userDao.getUserObject();
        if (loggedInUser == null) {
            throw new PMSCustomException(ResponseCode.COULD_NOT_FIND_USER_SESSION);
        }
        EnumWrapper profileType = new EnumWrapper(ProfileType.valueOf(loggedInUser.getProfileType()).name(), i18NService.getLocalizedMessage(ProfileType.valueOf(loggedInUser.getProfileType()).getName()), null);
        return new UserDTO(loggedInUser, profileType);
    }

    public void verifyOTPAndUpdateContact(String code) {
        Users loggedInUser = userDao.getUserObject();
        if (loggedInUser == null) {
            throw new PMSCustomException(ResponseCode.LOAD_USER_ERROR);
        }
        OtpType otpType = otpEncryptionService.getActiveOTP(loggedInUser.getId())
                .map(UserOTP::getChannel)
                .map(OtpType::valueOf)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.TOTP_VALIDATION_FAILURE));
        if (!otpEncryptionService.verifyOTPAgainstValueInDB(loggedInUser.getEmail(), code, otpType)) {
            throw new PMSCustomException(ResponseCode.TOTP_VALIDATION_FAILURE);
        }
        UserOTP lastVerifiedOTP = otpEncryptionService.getLastVerifiedOTP(loggedInUser.getId());
        String contact = lastVerifiedOTP.getContact();
        NotificationChannel channel = NotificationChannel.valueOf(lastVerifiedOTP.getChannel());
        if (NotificationChannel.EMAIL.equals(channel)) {
            loggedInUser.setEmail(contact);
        } else if (NotificationChannel.SMS.equals(channel)) {
            loggedInUser.setPhoneNumber(contact);
            loggedInUser.setPhoneVerified(true);
            loggedInUser.setPhoneVerifiedAt(ZonedDateTime.now());
            Locale locale = PMSUtils.getLocaleFromPhoneNumber(contact);
            if (locale != null) {
                loggedInUser.setCountry(locale.getDisplayCountry());
                loggedInUser.setCountryCode(locale.getCountry());
            }
        }
        loggedInUser.setLastModifiedDate(LocalDateTime.now());
        userDao.save(loggedInUser);
    }

    public void saveChangeContactRequestAndSendOTP(String contact, NotificationChannel channel) {
        Users loggedInUser = userDao.getUserObject();
        if (loggedInUser == null) {
            throw new PMSCustomException(ResponseCode.LOAD_USER_ERROR);
        }
        switch (channel) {
            case EMAIL -> {
                if (!PMSUtils.isValidEmail(contact) || userDao.findByEmail(contact).isPresent()) {
                    throw new PMSCustomException(ResponseCode.INVALID_EMAIL);
                }
                ensureResendAllowed(loggedInUser.getId(), contact, OtpType.EMAIL);
                String otp = PMSUtils.generateRandomOTP();
                otpEncryptionService.saveOTP(loggedInUser.getEmail(), otp, OtpType.EMAIL, contact);
                String formattedMessage = String.format(i18NService.getLocalizedMessage(NotificationType.EMAIL_OTP.getBody()), otp,
                        "expiry_time");
                notificationService.sendNotification(new NotificationDTO(formattedMessage, contact, NotificationType.EMAIL_OTP));
            }
            case SMS -> {
                String localisedPhoneNumber = PMSUtils.getLocalisedPhoneNumber(contact);
                if (StringUtils.isBlank(localisedPhoneNumber)) {
                    throw new PMSCustomException(ResponseCode.INVALID_PHONENUMBER);
                }
                userDao.findByPhone(localisedPhoneNumber)
                        .filter(owner -> !owner.getId().equals(loggedInUser.getId()))
                        .ifPresent(owner -> { throw new PMSCustomException(ResponseCode.PHONE_NUMBER_ALREADY_IN_USE); });
                ensureResendAllowed(loggedInUser.getId(), localisedPhoneNumber, OtpType.SMS);
                String otp = PMSUtils.generateRandomOTP();
                otpEncryptionService.saveOTP(loggedInUser.getEmail(), otp, OtpType.SMS, localisedPhoneNumber);
                String formattedMessage = String.format(i18NService.getLocalizedMessage(NotificationType.OTP_SMS.getBody()), otp);
                notificationService.sendNotification(new NotificationDTO(formattedMessage, localisedPhoneNumber, NotificationType.OTP_SMS));
            }
        }
    }

    private void ensureResendAllowed(long userId, String contact, OtpType otpType) {
        otpEncryptionService.getActiveOTP(userId)
                .filter(activeOtp -> contact.equals(activeOtp.getContact()))
                .filter(activeOtp -> otpType.name().equals(activeOtp.getChannel()))
                .filter(activeOtp -> activeOtp.getCreatedOn() != null)
                .filter(activeOtp -> ZonedDateTime.now().isBefore(activeOtp.getCreatedOn().plusSeconds(otpResendCooldownSeconds)))
                .ifPresent(activeOtp -> { throw new PMSCustomException(ResponseCode.OTP_RESEND_TOO_SOON); });
    }

    public void updateUserDetails(UpdateUserDetailsDTO updateUserDetailsDTO) {
        Users loggedInUser = userDao.getUserObject();
        if (loggedInUser == null) {
            throw new PMSCustomException(ResponseCode.LOAD_USER_ERROR);
        }
        if (!manualIdentityEntryEnabled && (StringUtils.isNotBlank(updateUserDetailsDTO.identificationNumber())
                || StringUtils.isNotBlank(updateUserDetailsDTO.taxPin()))) {
            throw new PMSCustomException(ResponseCode.KYC_MANUAL_ENTRY_DISABLED);
        }
        if (manualIdentityEntryEnabled && !userDao.isValidIDAndTaxPin(loggedInUser.getId(), loggedInUser.getCountry(), updateUserDetailsDTO.identificationNumber(), updateUserDetailsDTO.taxPin())) {
            throw new PMSCustomException(ResponseCode.DUPLICATED_PROFILE_DETAILS);
        }
        loggedInUser.setFullName(updateUserDetailsDTO.name());
        loggedInUser.setProfileType(updateUserDetailsDTO.profileType().name());
        if (manualIdentityEntryEnabled) {
            loggedInUser.setIdentificationNumber(updateUserDetailsDTO.identificationNumber());
            loggedInUser.setTaxPin(updateUserDetailsDTO.taxPin());
        }
        loggedInUser.setLastModifiedDate(LocalDateTime.now());
        userDao.save(loggedInUser);
    }
}
