package org.pms.silverocean.service.users;

import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.wrappers.EnumWrapper;

public record UserDTO(String name, String email, String phoneNumber, String registrationDate, String lastLogin,
                      String registrationIp, String country, String countryCode, String city, String source,
                      EnumWrapper profileType, String organizationName, String identificationNumber, String taxPin,
                      boolean active, boolean completedProfile, boolean verified) {
    public UserDTO(Users users, EnumWrapper profileType) {
        this(users.getFullName(), users.getEmail(), users.getPhoneNumber(), PMSUtils.toKenyanTime(users.getCreatedOn()), PMSUtils.timeAgo(users.getLastLogin()),
                users.getRegistrationIP(), users.getCountry(), users.getCountryCode(), users.getCity(), users.getSource(),
                profileType, users.getOrganizationName(), users.getIdentificationNumber(), users.getTaxPin(),
                users.isActive(), users.isCompletedProfile(), users.isVerified());
    }
}
