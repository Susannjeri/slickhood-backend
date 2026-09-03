package org.pms.silverocean.service.kyc;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AwsTextractKycOcrProviderTest {

    @Test
    void rendersPdfToPngBeforeCallingSynchronousTextractApi() throws Exception {
        TextractClient client = mock(TextractClient.class);
        when(client.detectDocumentText(any(DetectDocumentTextRequest.class))).thenReturn(
                DetectDocumentTextResponse.builder().blocks(
                        Block.builder().blockType(BlockType.LINE).text("PIN A123456789B").confidence(99f).build())
                        .build());
        AwsTextractKycOcrProvider provider = new AwsTextractKycOcrProvider(client);

        OcrResult result = provider.extract(onePagePdf(), "application/pdf",
                KycDocumentType.KRA_PIN_CERTIFICATE);

        ArgumentCaptor<DetectDocumentTextRequest> request = ArgumentCaptor.forClass(DetectDocumentTextRequest.class);
        verify(client).detectDocumentText(request.capture());
        byte[] submitted = request.getValue().document().bytes().asByteArray();
        assertThat(submitted).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(result.fields()).containsEntry("taxPin", "A123456789B");
    }

    private byte[] onePagePdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
