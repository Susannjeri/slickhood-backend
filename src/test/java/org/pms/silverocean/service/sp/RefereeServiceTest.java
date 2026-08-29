package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.database.pms.entities.Referee;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.RefereeDao;
import org.pms.silverocean.service.sp.enums.RefereeStatus;
import org.pms.silverocean.service.sp.wrappers.AddRefereeRequest;
import org.pms.silverocean.service.sp.wrappers.RefereeDTO;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefereeServiceTest {

    @Mock
    private RefereeDao refereeDao;
    @Mock
    private ProviderProfileDao profileDao;
    @Mock
    private UserDao userDao;

    private RefereeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RefereeService(refereeDao, profileDao, userDao);
    }

    private ProviderProfile makeProfile(long id, long userId) {
        ProviderProfile p = new ProviderProfile();
        p.setId(id);
        p.setUserId(userId);
        return p;
    }

    private Referee makeReferee(long id, long profileId, String status) {
        Referee r = new Referee();
        r.setId(id);
        r.setProfileId(profileId);
        r.setName("John Ref");
        r.setContact("+254700000001");
        r.setVerificationStatus(status);
        r.setActive(true);
        return r;
    }

    // --- addReferee ---

    @Test
    void addReferee_throwsSP_PROFILE_NOT_FOUND_whenNoProfile() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.empty());

        AddRefereeRequest request = new AddRefereeRequest("Jane Doe", "+254711111111");

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.addReferee(request));
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void addReferee_savesRefereeWithPendingStatusAndActive_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        doAnswer(inv -> { ((Referee) inv.getArgument(0)).setId(1L); return null; })
                .when(refereeDao).save(any(Referee.class), anyString());

        AddRefereeRequest request = new AddRefereeRequest("Jane Doe", "+254711111111");

        RefereeDTO dto = service.addReferee(request);

        ArgumentCaptor<Referee> captor = ArgumentCaptor.forClass(Referee.class);
        verify(refereeDao).save(captor.capture(), anyString());
        Referee saved = captor.getValue();

        assertEquals("Jane Doe", saved.getName());
        assertEquals("+254711111111", saved.getContact());
        assertEquals(RefereeStatus.PENDING.name(), saved.getVerificationStatus());
        assertTrue(saved.isActive());
        assertEquals(10L, saved.getProfileId());
        assertEquals("Jane Doe", dto.name());
    }

    // --- editReferee ---

    @Test
    void editReferee_throwsSP_PROFILE_NOT_FOUND_whenNoProfile() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.empty());

        AddRefereeRequest request = new AddRefereeRequest("Updated Name", "+254700000002");

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.editReferee(1L, request));
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void editReferee_throwsSP_REFEREE_NOT_FOUND_whenRefereeNotFound() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        when(refereeDao.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.empty());

        AddRefereeRequest request = new AddRefereeRequest("Updated Name", "+254700000002");

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.editReferee(5L, request));
        assertEquals(ResponseCode.SP_REFEREE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void editReferee_throwsSP_REFEREE_CANNOT_EDIT_whenStatusIsNotPending() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        Referee r = makeReferee(5L, 10L, RefereeStatus.CONFIRMED.name());
        when(refereeDao.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(r));

        AddRefereeRequest request = new AddRefereeRequest("Updated Name", "+254700000002");

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.editReferee(5L, request));
        assertEquals(ResponseCode.SP_REFEREE_CANNOT_EDIT, ex.getResponseCode());
    }

    @Test
    void editReferee_updatesNameAndContact_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        Referee r = makeReferee(5L, 10L, RefereeStatus.PENDING.name());
        when(refereeDao.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(r));
        doNothing().when(refereeDao).save(any(Referee.class), anyString());

        AddRefereeRequest request = new AddRefereeRequest("New Name", "+254700000099");

        RefereeDTO dto = service.editReferee(5L, request);

        ArgumentCaptor<Referee> captor = ArgumentCaptor.forClass(Referee.class);
        verify(refereeDao).save(captor.capture(), anyString());
        Referee saved = captor.getValue();

        assertEquals("New Name", saved.getName());
        assertEquals("+254700000099", saved.getContact());
        assertEquals("New Name", dto.name());
    }

    // --- removeReferee ---

    @Test
    void removeReferee_throwsSP_REFEREE_CANNOT_EDIT_whenStatusIsNotPending() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        Referee r = makeReferee(3L, 10L, RefereeStatus.REJECTED.name());
        when(refereeDao.findByIdAndProfileId(3L, 10L)).thenReturn(Optional.of(r));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.removeReferee(3L));
        assertEquals(ResponseCode.SP_REFEREE_CANNOT_EDIT, ex.getResponseCode());
    }

    @Test
    void removeReferee_setsActiveFalse_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        Referee r = makeReferee(3L, 10L, RefereeStatus.PENDING.name());
        when(refereeDao.findByIdAndProfileId(3L, 10L)).thenReturn(Optional.of(r));
        doNothing().when(refereeDao).save(any(Referee.class), anyString());

        service.removeReferee(3L);

        ArgumentCaptor<Referee> captor = ArgumentCaptor.forClass(Referee.class);
        verify(refereeDao).save(captor.capture(), anyString());
        assertFalse(captor.getValue().isActive());
    }

    // --- verifyReferee ---

    @Test
    void verifyReferee_throwsSP_REFEREE_NOT_FOUND_whenNotFound() {
        when(refereeDao.findById(20L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.verifyReferee(20L, RefereeStatus.CONFIRMED));
        assertEquals(ResponseCode.SP_REFEREE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void verifyReferee_setsVerificationStatus_onSuccess() {
        Referee r = makeReferee(20L, 10L, RefereeStatus.PENDING.name());
        when(refereeDao.findById(20L)).thenReturn(Optional.of(r));
        doNothing().when(refereeDao).save(any(Referee.class), anyString());

        service.verifyReferee(20L, RefereeStatus.CONFIRMED);

        ArgumentCaptor<Referee> captor = ArgumentCaptor.forClass(Referee.class);
        verify(refereeDao).save(captor.capture(), anyString());
        assertEquals(RefereeStatus.CONFIRMED.name(), captor.getValue().getVerificationStatus());
    }
}
