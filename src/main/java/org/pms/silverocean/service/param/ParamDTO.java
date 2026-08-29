package org.pms.silverocean.service.param;

import org.pms.silverocean.database.pms.entities.Param;
import org.pms.silverocean.service.payment.wrappers.AccountPropertyDefinition;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;

import java.nio.charset.StandardCharsets;

public record ParamDTO(Long id, AccountPropertyDefinition param, String channelType, String value) {
    public ParamDTO(Param paramFromDb) {
        this(paramFromDb.getId(),
                PaymentChannel.findPropertyByLabelKey(paramFromDb.getType()),
                PaymentChannel.findChannelByPropertyLabelKey(paramFromDb.getType()).getName(),
                paramFromDb.isEncrypted() ? "*****"
                        : new String(paramFromDb.getValue(), StandardCharsets.UTF_8));
    }

    public ParamDTO(Long id, AccountPropertyDefinition param, String channelType, String value) {
        this.id = id;
        this.param = param;
        this.channelType = channelType;
        this.value = value;
    }
}
