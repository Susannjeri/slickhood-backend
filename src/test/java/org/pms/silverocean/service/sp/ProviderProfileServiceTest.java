package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.enums.ProviderProfileStatus;
import org.pms.silverocean.service.sp.wrappers.ProviderProfileDTO;
import org.pms.silverocean.service.sp.wrappers.SetupProfileRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderProfileServiceTest {

    @Mock
    private ProviderProfileDao profileDao;

    @Mock
    private UserDao userDao;
    @Mock
    private AccountDao accountDao;

    private ProviderProfileService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProviderProfileService(profileDao, userDao, accountDao);
    }

    @Test
    void setupProfile_throwsSP_PROFILE_ALREADY_EXISTS_whenProfileExists() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.existsByUserId(1L)).thenReturn(true);

        SetupProfileRequest request = new SetupProfileRequest("Acme Ltd", true,  1.0, 2.0);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.setupProfile(request, "127.0.0.1"));
        assertEquals(ResponseCode.SP_PROFILE_ALREADY_EXISTS, ex.getResponseCode());
    }

    @Test
    void setupProfile_throwsSP_PROFILE_CONSENT_REQUIRED_whenConsentIsNull() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.existsByUserId(1L)).thenReturn(false);

        SetupProfileRequest request = new SetupProfileRequest("Acme Ltd", null,  1.0, 2.0);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.setupProfile(request, "127.0.0.1"));
        assertEquals(ResponseCode.SP_PROFILE_CONSENT_REQUIRED, ex.getResponseCode());
    }

    @Test
    void setupProfile_throwsSP_PROFILE_CONSENT_REQUIRED_whenConsentIsFalse() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.existsByUserId(1L)).thenReturn(false);

        SetupProfileRequest request = new SetupProfileRequest("Acme Ltd", false,  1.0, 2.0);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.setupProfile(request, "127.0.0.1"));
        assertEquals(ResponseCode.SP_PROFILE_CONSENT_REQUIRED, ex.getResponseCode());
    }

    @Test
    void setupProfile_savesProfileWithCorrectFieldsAndReturnDTO_onSuccess() {
        when(userDao.getUserId()).thenReturn(42L);
        when(profileDao.existsByUserId(42L)).thenReturn(false);
        doAnswer(inv -> { ((ProviderProfile) inv.getArgument(0)).setId(1L); return null; })
                .when(profileDao).save(any(ProviderProfile.class), anyString());

        SetupProfileRequest request = new SetupProfileRequest("Best Cleaners", true, -1.28, 36.82);

        ProviderProfileDTO dto = service.setupProfile(request, "127.0.0.1");

        ArgumentCaptor<ProviderProfile> captor = ArgumentCaptor.forClass(ProviderProfile.class);
        verify(profileDao).save(captor.capture(), anyString());
        ProviderProfile saved = captor.getValue();

        assertEquals("Best Cleaners", saved.getBusinessName());
        assertEquals(ProviderProfileStatus.ACTIVE.name(), saved.getStatus());
        assertTrue(saved.isActive());
        assertEquals(42L, saved.getUserId());
        assertEquals("Best Cleaners", dto.businessName());
        assertEquals(ProviderProfileStatus.ACTIVE.name(), dto.status());
    }

    @Test
    void getMyProfile_throwsSP_PROFILE_NOT_FOUND_whenNotFound() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.getMyProfile());
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void getMyProfile_returnsDTOOnSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderProfile profile = new ProviderProfile();
        profile.setId(10L);
        profile.setBusinessName("My Biz");
        profile.setStatus(ProviderProfileStatus.ACTIVE.name());
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(profile));

        ProviderProfileDTO dto = service.getMyProfile();
        assertEquals("My Biz", dto.businessName());
        assertEquals(ProviderProfileStatus.ACTIVE.name(), dto.status());
    }

    @Test
    void getProfileById_throwsSP_PROFILE_NOT_FOUND_whenNotFound() {
        when(profileDao.findById(99L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.getProfileById(99L));
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void blacklistProfile_throwsSP_PROFILE_NOT_FOUND_whenNotFound() {
        when(profileDao.findById(55L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.blacklistProfile(55L));
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void blacklistProfile_setsStatusBLACKLISTED_andSaves() {
        ProviderProfile profile = new ProviderProfile();
        profile.setId(5L);
        profile.setStatus(ProviderProfileStatus.ACTIVE.name());
        when(profileDao.findById(5L)).thenReturn(Optional.of(profile));
        doNothing().when(profileDao).save(any(ProviderProfile.class), anyString());

        service.blacklistProfile(5L);

        ArgumentCaptor<ProviderProfile> captor = ArgumentCaptor.forClass(ProviderProfile.class);
        verify(profileDao).save(captor.capture(), anyString());
        assertEquals(ProviderProfileStatus.BLACKLISTED.name(), captor.getValue().getStatus());
    }
}
