package org.pms.silverocean.service.payment.platforms;

import java.time.LocalDateTime;

public record TokenStore(String accessToken, LocalDateTime expiryTime) {
}
