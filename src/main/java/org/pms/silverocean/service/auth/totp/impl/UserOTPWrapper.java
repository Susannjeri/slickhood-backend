package org.pms.silverocean.service.auth.totp.impl;

import java.time.ZonedDateTime;

public record UserOTPWrapper(String otp, int attempts, ZonedDateTime otpExpiryTime) {
}
