package org.pms.silverocean.service.kyc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.Document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "kyc.ocr.provider", havingValue = "aws-textract")
public class AwsTextractKycOcrProvider implements KycOcrProvider {
    private static final Pattern KRA_PIN = Pattern.compile("\\b[A-Z][0-9]{9}[A-Z]\\b");
    private static final Pattern NATIONAL_ID = Pattern.compile("\\b[0-9]{7,9}\\b");
    private static final Pattern PASSPORT = Pattern.compile("\\b[A-Z][0-9]{7,8}\\b");
    private final TextractClient client;

    public AwsTextractKycOcrProvider(TextractClient client) { this.client = client; }

    @Override
    public OcrResult extract(byte[] document, String contentType, KycDocumentType documentType) {
        var response = client.detectDocumentText(DetectDocumentTextRequest.builder()
                .document(Document.builder().bytes(SdkBytes.fromByteArray(document)).build()).build());
        List<Block> lines = response.blocks().stream().filter(block -> block.blockType() == BlockType.LINE).toList();
        String text = lines.stream().map(Block::text).reduce("", (left, right) -> left + "\n" + right).toUpperCase(Locale.ROOT);
        Map<String, String> fields = new LinkedHashMap<>();
        find(KRA_PIN, text).ifPresent(value -> fields.put("taxPin", value));
        if (documentType == KycDocumentType.PASSPORT) {
            find(PASSPORT, text).ifPresent(value -> fields.put("documentNumber", value));
        } else {
            find(NATIONAL_ID, text).ifPresent(value -> fields.put("documentNumber", value));
        }
        double confidence = lines.stream().map(Block::confidence).filter(java.util.Objects::nonNull)
                .mapToDouble(Float::doubleValue).average().orElse(0);
        return new OcrResult("AWS_TEXTRACT", confidence, fields);
    }

    private java.util.Optional<String> find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? java.util.Optional.of(matcher.group()) : java.util.Optional.empty();
    }

    @Override public boolean enabled() { return true; }
}
