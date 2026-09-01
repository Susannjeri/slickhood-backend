package org.pms.silverocean.service.users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.totp.impl.OTPEncryptionService;
import org.pms.silverocean.service.notification.NotificationService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SilverOceanUserServiceUserListTest {

    @Mock private I18NService i18NService;
    @Mock private UserDao userDao;
    @Mock private UserRoleRepo userRoleRepo;
    @Mock private NotificationService notificationService;
    @Mock private OTPEncryptionService otpEncryptionService;

    private SilverOceanUserService service;

    @BeforeEach
    void setUp() {
        service = new SilverOceanUserService(i18NService, userDao, userRoleRepo,
                notificationService, otpEncryptionService);
    }

    @Test
    void listsDistinctAuthoritativeUserTypesForEveryUser() {
        Users user = new Users();
        user.setId(17L);
        user.setEmail("owner@example.com");
        user.setFullName("Owner Example");
        user.setProfileType(ProfileType.INDIVIDUAL.name());
        user.setCreatedOn(ZonedDateTime.now());

        PageRequest pageable = PageRequest.of(0, 14);
        when(userDao.searchAllUsers(pageable, Optional.empty()))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));
        when(userRoleRepo.findRoleNamesByUserIds(List.of(17L))).thenReturn(List.of(
                new UserRoleNameDTO(17L, "Tenant"),
                new UserRoleNameDTO(17L, "Landlord"),
                new UserRoleNameDTO(17L, "Landlord")
        ));
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Individual");

        ResponseDTO response = service.getUserList(pageable, Optional.empty());

        assertTrue(response.isSuccess());
        UserDTO listedUser = (UserDTO) response.getData().get(0);
        assertEquals(List.of("Landlord", "Tenant"), listedUser.userTypes());
    }
}
