package org.pms.silverocean.common;

import com.google.common.collect.ImmutableMap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.pms.silverocean.service.PMSCustomException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import static org.pms.silverocean.common.ResponseCode.FAILED_TO_GENERATE_QR_CODE_IMAGE;

public class QRCodeUtil {
    private static final String BASE64_PREFIX = "data:image/png;base64,";
    private static final int QR_CODE_WHITESPACE_MARGIN = 2;
    private static final String DEFAULT_IMAGE_FORMAT = "png";
    private static final String UTF_8_CHARSET = "UTF-8";
    private static final Map<EncodeHintType, Object> HINTS = ImmutableMap.of(
            EncodeHintType.CHARACTER_SET, UTF_8_CHARSET,
            EncodeHintType.MARGIN, QR_CODE_WHITESPACE_MARGIN);


    private QRCodeUtil(){

    }

    /**
     * Generates QR code in base64 encoded image. Doesn't support coloring.
     *
     * @param qrData Data embedded into the QR code.
     * @param width The preferred width in pixels.
     * @param height The preferred height in pixels.
     * @return Base64 encoded image (png) string. Can directly be used in the markup.
     */
    public static String generateBase64ImagedQrCode(final String qrData,
                                                    final int width,
                                                    final int height)  {
        try {
            final BufferedImage bufferedImage = toQrCode(qrData, width, height);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, DEFAULT_IMAGE_FORMAT, outputStream);
            return BASE64_PREFIX + new String(Base64.getEncoder().encode(outputStream.toByteArray()));
        } catch (WriterException | IOException e) {
            throw new PMSCustomException(FAILED_TO_GENERATE_QR_CODE_IMAGE, e);
        }
    }

    private static BufferedImage toQrCode(final String input,
                                          final int width,
                                          final int height) throws WriterException {
        final QRCodeWriter barcodeWriter = new QRCodeWriter();
        final BitMatrix bitMatrix = barcodeWriter.encode(input, BarcodeFormat.QR_CODE, width, height,
                HINTS);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }
}
