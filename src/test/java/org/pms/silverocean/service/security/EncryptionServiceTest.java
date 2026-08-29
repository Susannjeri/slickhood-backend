package org.pms.silverocean.service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.totp.impl.OTPDao;
import org.pms.silverocean.service.auth.totp.impl.OTPEncryptionService;
import org.pms.silverocean.service.config.ConfigService;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EncryptionServiceTest {


    private UserDao userDao;
    private KeyDao keyDao;
    private ConfigService configService;
    private EncryptionService encryptionService;
    private OTPEncryptionService otpEncryptionService;
    private OTPDao otpDao;

    // 32 byte key (Base64 encoded string = 44 chars)
    private static final byte[] SECRET_KEY_BASE64 =
            Base64.getEncoder().encode(new byte[32]);

    @BeforeEach
    void setUp() throws Exception {
        userDao = mock(UserDao.class);
        configService = mock(ConfigService.class);
        keyDao = mock(KeyDao.class);
        when(keyDao.getActiveSecretKey()).thenReturn(SECRET_KEY_BASE64);

        encryptionService = new EncryptionService(keyDao);
        otpEncryptionService = new OTPEncryptionService(encryptionService, configService, userDao, otpDao);
        encryptionService.init();
    }

    @Test
    void encryptAndDecrypt_shouldWork() {
        String text = "HelloWorld";

        byte[] encrypted = encryptionService.encrypt(text);
        assertThat(encrypted).isNotNull();

        DecryptDTO decrypted = encryptionService.decrypt(encrypted);
        assertThat(decrypted.decryptedValue()).isEqualTo(text);
    }

    @Test
    void decrypt_shouldReturnNullOnInvalidData() {
        byte[] badCipher = new byte[5]; // too short
        assertNull(encryptionService.decrypt(badCipher));
//        assertThatThrownBy(() -> e)
//                .isInstanceOf(PMSCustomException.class)
//                .hasMessageContaining("CANNOT_DECRYPT_EMPTY_VALUE");
    }
//
//    @Test
//    void saveUserSecretKey_shouldEncryptAndSave() throws Exception {
//        Users user = new Users();
//        ConfigDTO config = new ConfigDTO(0l, "", "", 30, false);
//
//        when(userDao.findByEmail("test@example.com")).thenReturn(Optional.of(user));
//        when(configService.getConfigByName(any())).thenReturn(config);
//        otpEncryptionService.saveOTP("test@example.com", "mysecret", OtpType.EMAIL, "test@example.com");
//
//        ArgumentCaptor<Users> captor = ArgumentCaptor.forClass(Users.class);
//        verify(userDao).save(captor.capture());

//        Users saved = captor.getValue();
//        assertThat(saved.getTempOtp()).isNotNull();
//        assertThat(encryptionService.decrypt(saved.getTempOtp()).decryptedValue()).isEqualTo("mysecret");
//
//        assertThat(otpEncryptionService.getUserSecretKey("test@example.com").otp()).isEqualTo("mysecret");
//    }

//    @Test
//    void setTotpEnabled_shouldUpdateUser() throws Exception {
//        Users user = new Users();
//        when(userDao.findByEmail("test@example.com")).thenReturn(Optional.of(user));
////        otpEncryptionService.updateVerifiedTotp("test@example.com", "randomOTP");
//
//        verify(userDao).save(user);
//        assertTrue(user.isMfaSetup());
//    }

}