package org.pms.silverocean.service.auth.totp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.controller.wrappers.VerificationOptionsDTO;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.totp.impl.EmailOtpServiceImpl;
import org.pms.silverocean.service.auth.totp.impl.GoogleTotpServiceImpl;
import org.pms.silverocean.service.auth.totp.impl.OtpType;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service @Slf4j
public class TotpServiceFactory {
    private final Map<String, TotpService> services;
    private final UserDao userDao;

    public static final String TOTP_SERVICE_SUFFIX = "TotpService";

    public TotpServiceFactory(Map<String, TotpService> services, UserDao userDao) {
        this.services = services;
        this.userDao = userDao;
    }

    public Optional<TotpService> getService(OtpType provider) {
        return Optional.ofNullable(services.get(provider.name() + TOTP_SERVICE_SUFFIX));
    }

    public Optional<VerificationOptionsDTO> getUserVerificationOptions(String email) {
        Optional<Users> byEmail = userDao.findByEmail(email);
        if (byEmail.isPresent()) {
            Users user = byEmail.get();
            boolean totpEnabled = !PMSUtils.isByteArrayEmpty(user.getTotpSecret());
            return Optional.of(new VerificationOptionsDTO(true, StringUtils.isNotBlank(user.getPhoneNumber()), totpEnabled, totpEnabled ? GoogleTotpServiceImpl.NAME : EmailOtpServiceImpl.NAME));
        }
        log.debug("Recovery options requested for an account that is not eligible.");
        return Optional.empty();
    }
}
