package org.pms.silverocean.service.payment;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentPlatformFactoryMaintenanceTest {
    @Test
    void disabledProviderAllowsAccountMaintenanceButNotPaymentInitiation() {
        PaymentPlatform platform = mock(PaymentPlatform.class);
        GarageService storage = mock(GarageService.class);
        when(platform.channelType()).thenReturn(PaymentChannel.PAYSTACK);
        when(platform.channelIcon()).thenReturn("paystack.png");
        when(storage.getPresignedUrl(anyString())).thenReturn("https://example.test/icon");
        PaymentPlatformFactory factory = new PaymentPlatformFactory(Map.of("Paystack", platform), storage);
        assertThat(factory.getChannelImage(PaymentChannel.PAYSTACK)).isEqualTo("https://example.test/icon");
        assertThatThrownBy(() -> factory.getPlatform(PaymentChannel.PAYSTACK)).isInstanceOf(IllegalArgumentException.class);
        assertThat(factory.isAvailableForNewPayments(PaymentChannel.PAYSTACK)).isFalse();
        assertThat(factory.getAvailableChannelsForNewPayments()).isEmpty();
        assertThat(factory.getPaymentTypes()).isEmpty();
    }

    @Test
    void availableChannelSetUsesRuntimePlatformStateAndExcludesRetiredFlutterwave() {
        PaymentPlatform mpesa = mock(PaymentPlatform.class);
        when(mpesa.channelType()).thenReturn(PaymentChannel.MPESA);
        when(mpesa.isActive()).thenReturn(true);
        PaymentPlatform flutterwave = mock(PaymentPlatform.class);
        when(flutterwave.channelType()).thenReturn(PaymentChannel.FLUTTER_WAVE);
        when(flutterwave.isActive()).thenReturn(true);

        PaymentPlatformFactory factory = new PaymentPlatformFactory(
                Map.of("mpesa", mpesa, "flutterwave", flutterwave), mock(GarageService.class));

        assertThat(factory.getAvailableChannelsForNewPayments())
                .containsExactly(PaymentChannel.MPESA);
    }
}
