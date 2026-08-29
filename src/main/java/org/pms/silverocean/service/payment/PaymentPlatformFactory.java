package org.pms.silverocean.service.payment;

import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentChannelDTO;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PaymentPlatformFactory {
    private final Map<String, PaymentPlatform> platforms;
    private final GarageService garageService;

    public PaymentPlatformFactory(Map<String, PaymentPlatform> platforms, GarageService garageService) {
        this.platforms = platforms;
        this.garageService = garageService;
    }

    public PaymentPlatform getPlatform(PaymentChannel channel) {
        PaymentPlatform platform = platforms.get(channel.getName());
        if (platform == null || !platform.isActive()) {
            throw new IllegalArgumentException("Unsupported payment type: " + channel.getName());
        }
        return platform;
    }

    public Set<PaymentChannelDTO> getPaymentTypes() {
       return platforms.values().stream().filter(PaymentPlatform::isActive)
               .map(platforms ->
                       new PaymentChannelDTO(
                               platforms.channelType().name(), platforms.channelType().getName(),
                               platforms.channelType().getDescription(),
                               signImage(platforms.channelIcon())
                       )).collect(Collectors.toSet());
    }

    public String getChannelImage(PaymentChannel channel) {
       return signImage(getPlatform(channel).channelIcon());
    }

    private String signImage(String imageLocation) {
        return garageService.getPresignedUrl(PMSUtils.systemImagesFolder + imageLocation);
    }
}
