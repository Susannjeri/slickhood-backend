package org.pms.silverocean.service.kyc;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentQualityServiceTest {
    private final DocumentQualityService service = new DocumentQualityService(900,550,35);

    @Test void rejectsDarkCaptureWithActionableReason() throws Exception {
        ImageQualityResult result = service.inspect(image(Color.BLACK),"image/png");
        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).contains("too dark");
    }

    @Test void rejectsWashedOutCaptureWithActionableReason() throws Exception {
        ImageQualityResult result = service.inspect(image(Color.WHITE),"image/png");
        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).containsAnyOf("glare","overexposure");
    }

    private byte[] image(Color color) throws Exception {
        BufferedImage image = new BufferedImage(1000,600,BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(color); graphics.fillRect(0,0,1000,600); graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image,"png",output); return output.toByteArray();
    }
}
