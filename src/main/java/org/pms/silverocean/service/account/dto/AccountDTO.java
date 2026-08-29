package org.pms.silverocean.service.account.dto;

import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;

import java.time.ZonedDateTime;
import java.util.List;

public record AccountDTO(
        Long id,
        String name,
        AccountCategory category,
        PaymentChannel channel,
        String icon,
        String channelDisplayName,
        boolean active,
        boolean verified,
        ZonedDateTime createdOn,
        List<AccountPropertyDTO> properties
) {
    public AccountDTO(PaymentAccount account, List<AccountPropertyDTO> properties, String icon) {
        this(account.getId(),
                account.getName(),
                account.getCategory(),
                account.getChannel(),
                icon,
                account.getChannel().getName(),
                account.isActive(),
                account.isVerified(),
                account.getCreatedOn(),
                properties);
    }
}
