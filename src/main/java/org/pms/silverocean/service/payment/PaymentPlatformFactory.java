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
        // Spring bean names are legacy display labels and are not a stable
        // identifier (for example the M-Pesa bean is named "M-Pesa" while
        // the channel display label is "M-Pesa Direct Paybill"). Resolve by
        // the platform's canonical enum instead of the human-facing label.
        PaymentPlatform platform = platforms.values().stream()
                .filter(candidate -> candidate.channelType() == channel)
                .findFirst()
                .orElse(null);
        if (platform == null || !platform.isActive()) {
            throw new IllegalArgumentException("Unsupported payment type: " + channel.getName());
        }
        return platform;
    }

    public Set<PaymentChannelDTO> getPaymentTypes() {
       return platforms.values().stream()
               .filter(PaymentPlatform::isActive)
               // Flutterwave remains readable for historic transactions and callbacks,
               // but is retired from all new Kenyan checkout and account journeys.
               .filter(platform -> platform.channelType() != PaymentChannel.FLUTTER_WAVE)
               .map(platforms ->
                       new PaymentChannelDTO(
                               platforms.channelType().name(), platforms.channelType().getName(),
                               platforms.channelType().getDescription(),
                               signImage(platforms.channelIcon())
                       )).collect(Collectors.toSet());
    }

    /**
     * Returns whether a channel may be attached to a new commercial transaction.
     * Historic accounts and callbacks remain readable when a rail is disabled, but
     * new marketplace work must not be pinned to a provider that cannot collect it.
     */
    public boolean isAvailableForNewPayments(PaymentChannel channel) {
        return channel != null
                && channel != PaymentChannel.FLUTTER_WAVE
                && platforms.values().stream()
                .anyMatch(platform -> platform.channelType() == channel && platform.isActive());
    }

    /**
     * Canonical set of rails that may currently be used for a new payment.
     *
     * Directory queries use this set inside the database predicate rather than
     * filtering a page after it has been fetched. This keeps page totals and
     * page boundaries correct when an operator disables a payment integration.
     */
    public Set<PaymentChannel> getAvailableChannelsForNewPayments() {
        return platforms.values().stream()
                .filter(PaymentPlatform::isActive)
                .map(PaymentPlatform::channelType)
                .filter(channel -> channel != PaymentChannel.FLUTTER_WAVE)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String getChannelImage(PaymentChannel channel) {
       // Turning off checkout must not make existing account settings unreadable.
       return platforms.values().stream()
               .filter(platform -> platform.channelType() == channel)
               .findFirst().map(platform -> signImage(platform.channelIcon())).orElse(null);
    }

    private String signImage(String imageLocation) {
        return garageService.getPresignedUrl(PMSUtils.systemImagesFolder + imageLocation);
    }
}
