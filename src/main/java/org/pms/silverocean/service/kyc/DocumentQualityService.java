package org.pms.silverocean.service.kyc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Service
public class DocumentQualityService {
    private final int minWidth;
    private final int minHeight;
    private final double minSharpness;

    public DocumentQualityService(@Value("${kyc.image.min-width:900}") int minWidth,
                                  @Value("${kyc.image.min-height:550}") int minHeight,
                                  @Value("${kyc.image.min-sharpness:35.0}") double minSharpness) {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.minSharpness = minSharpness;
    }

    public ImageQualityResult inspect(byte[] bytes, String contentType) {
        if (contentType == null || !contentType.startsWith("image/")) {
            return new ImageQualityResult(true, 0, 0, 100, null);
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) return new ImageQualityResult(false, 0, 0, 0, "Unreadable image");
            if (image.getWidth() < minWidth || image.getHeight() < minHeight) {
                return new ImageQualityResult(false, image.getWidth(), image.getHeight(), 0,
                        "Image resolution is too low");
            }
            double sharpness = laplacianVariance(image);
            if (sharpness < minSharpness) {
                return new ImageQualityResult(false, image.getWidth(), image.getHeight(), sharpness,
                        "Image appears blurred; retake it in good light and hold the camera steady");
            }
            return new ImageQualityResult(true, image.getWidth(), image.getHeight(), sharpness, null);
        } catch (Exception ex) {
            return new ImageQualityResult(false, 0, 0, 0, "Image could not be inspected");
        }
    }

    private double laplacianVariance(BufferedImage image) {
        int step = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / 700);
        double sum = 0, sumSq = 0;
        long count = 0;
        for (int y = step; y < image.getHeight() - step; y += step) {
            for (int x = step; x < image.getWidth() - step; x += step) {
                int c = gray(image.getRGB(x, y));
                int lap = gray(image.getRGB(x - step, y)) + gray(image.getRGB(x + step, y))
                        + gray(image.getRGB(x, y - step)) + gray(image.getRGB(x, y + step)) - 4 * c;
                sum += lap;
                sumSq += (double) lap * lap;
                count++;
            }
        }
        if (count == 0) return 0;
        double mean = sum / count;
        return sumSq / count - mean * mean;
    }

    private int gray(int rgb) {
        return (int) (0.299 * ((rgb >> 16) & 255) + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255));
    }
}
