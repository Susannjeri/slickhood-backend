package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

public record MPesaStaticMembers(String businessShortCode, String password, String timestamp, String callbackURL) {
}
