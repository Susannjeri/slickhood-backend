package org.pms.silverocean.service.kyc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kenya-aware extraction over Textract. Uncertain evidence is always routed to human review. */
@Component
@ConditionalOnProperty(name = "kyc.ocr.provider", havingValue = "aws-textract")
public class AwsTextractKycOcrProvider implements KycOcrProvider {
    private static final Pattern KRA_PIN = Pattern.compile("\\b[A-Z][0-9]{9}[A-Z]\\b");
    private static final Pattern NATIONAL_ID = Pattern.compile("\\b[0-9]{7,9}\\b");
    private static final Pattern PASSPORT = Pattern.compile("\\b[A-Z]{1,2}[0-9]{6,8}\\b");
    private static final Pattern DATE = Pattern.compile("\\b(?:[0-3]?[0-9][./-][01]?[0-9][./-](?:19|20)[0-9]{2}|(?:19|20)[0-9]{2}[./-][01]?[0-9][./-][0-3]?[0-9])\\b");
    private static final Set<String> ID_LABELS = Set.of("ID NO", "ID NUMBER", "IDENTITY NUMBER", "NATIONAL ID");
    private static final Set<String> NAME_LABELS = Set.of("FULL NAME", "NAME OF HOLDER", "SURNAME", "OTHER NAMES", "NAME");
    private static final Set<String> DOB_LABELS = Set.of("DATE OF BIRTH", "BIRTH DATE", "DOB");
    private static final Set<String> EXPIRY_LABELS = Set.of("DATE OF EXPIRY", "EXPIRY DATE", "EXPIRES");
    private final TextractClient client;

    public AwsTextractKycOcrProvider(TextractClient client) { this.client = client; }

    @Override
    public OcrResult extract(byte[] document, String contentType, KycDocumentType documentType) {
        var response = client.detectDocumentText(DetectDocumentTextRequest.builder()
                .document(Document.builder().bytes(SdkBytes.fromByteArray(document)).build()).build());
        List<DetectedLine> lines = response.blocks().stream()
                .filter(block -> block.blockType() == BlockType.LINE && block.text() != null)
                .map(this::line).toList();
        String text = lines.stream().map(DetectedLine::normalized).reduce("", (left, right) -> left + "\n" + right);
        Map<String, String> fields = new LinkedHashMap<>();
        List<Double> evidenceConfidence = new ArrayList<>();

        match(KRA_PIN, lines, text).ifPresent(value -> put(fields, evidenceConfidence, "taxPin", value));
        if (documentType == KycDocumentType.PASSPORT) {
            match(PASSPORT, lines, text).ifPresent(value -> put(fields, evidenceConfidence, "documentNumber", value));
            labelled(DOB_LABELS, DATE, lines).ifPresent(value -> put(fields, evidenceConfidence, "dateOfBirth", value));
            labelled(EXPIRY_LABELS, DATE, lines).ifPresent(value -> put(fields, evidenceConfidence, "expiryDate", value));
        } else if (documentType == KycDocumentType.NATIONAL_ID_FRONT
                || documentType == KycDocumentType.NATIONAL_ID_BACK
                || documentType == KycDocumentType.ALIEN_ID_FRONT
                || documentType == KycDocumentType.ALIEN_ID_BACK) {
            labelled(ID_LABELS, NATIONAL_ID, lines).or(() -> match(NATIONAL_ID, lines, text))
                    .ifPresent(value -> put(fields, evidenceConfidence, "documentNumber", value));
            labelled(DOB_LABELS, DATE, lines).ifPresent(value -> put(fields, evidenceConfidence, "dateOfBirth", value));
        }
        labelledText(NAME_LABELS, lines).ifPresent(value -> put(fields, evidenceConfidence, "fullName", value));

        List<String> missing = requiredMissing(documentType, fields);
        fields.put("_validationStatus", missing.isEmpty() ? "PASSED" : "REVIEW_REQUIRED");
        if (!missing.isEmpty()) fields.put("_validationWarnings", "Could not confidently extract: " + String.join(", ", missing));

        double confidence = evidenceConfidence.stream().mapToDouble(Double::doubleValue).average()
                .orElseGet(() -> lines.stream().mapToDouble(DetectedLine::confidence).average().orElse(0));
        if (!missing.isEmpty()) confidence = Math.min(confidence, 74.0);
        return new OcrResult("AWS_TEXTRACT_KENYA_V2", confidence, fields);
    }

    private List<String> requiredMissing(KycDocumentType type, Map<String, String> fields) {
        List<String> missing = new ArrayList<>();
        if (type == KycDocumentType.KRA_PIN_CERTIFICATE && !fields.containsKey("taxPin")) missing.add("valid KRA PIN");
        if ((type == KycDocumentType.PASSPORT || type.name().startsWith("NATIONAL_ID") || type.name().startsWith("ALIEN_ID"))
                && !fields.containsKey("documentNumber")) missing.add("document number");
        return missing;
    }

    private void put(Map<String, String> fields, List<Double> confidence, String key, Match value) {
        fields.put(key, value.value());
        fields.put("_confidence." + key, String.format(Locale.ROOT, "%.1f", value.confidence()));
        confidence.add(value.confidence());
    }

    private Optional<Match> labelled(Set<String> labels, Pattern pattern, List<DetectedLine> lines) {
        for (int index = 0; index < lines.size(); index++) {
            DetectedLine line = lines.get(index);
            if (labels.stream().noneMatch(line.normalized()::contains)) continue;
            Matcher sameLine = pattern.matcher(line.normalized());
            if (sameLine.find()) return Optional.of(new Match(sameLine.group(), line.confidence()));
            if (index + 1 < lines.size()) {
                DetectedLine next = lines.get(index + 1);
                Matcher nextLine = pattern.matcher(next.normalized());
                if (nextLine.find()) return Optional.of(new Match(nextLine.group(), Math.min(line.confidence(), next.confidence())));
            }
        }
        return Optional.empty();
    }

    private Optional<Match> labelledText(Set<String> labels, List<DetectedLine> lines) {
        for (int index = 0; index < lines.size(); index++) {
            DetectedLine line = lines.get(index);
            for (String label : labels) {
                int position = line.normalized().indexOf(label);
                if (position < 0) continue;
                String value = line.normalized().substring(position + label.length()).replaceFirst("^[ :.-]+", "").trim();
                if (value.matches("[A-Z][A-Z .'-]{3,}")) return Optional.of(new Match(value, line.confidence()));
                if (index + 1 < lines.size() && lines.get(index + 1).normalized().matches("[A-Z][A-Z .'-]{3,}")) {
                    DetectedLine next = lines.get(index + 1);
                    return Optional.of(new Match(next.normalized(), Math.min(line.confidence(), next.confidence())));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Match> match(Pattern pattern, List<DetectedLine> lines, String text) {
        for (DetectedLine line : lines) {
            Matcher matcher = pattern.matcher(line.normalized());
            if (matcher.find()) return Optional.of(new Match(matcher.group(), line.confidence()));
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(new Match(matcher.group(), 60)) : Optional.empty();
    }

    private DetectedLine line(Block block) {
        return new DetectedLine(block.text().trim().toUpperCase(Locale.ROOT),
                block.confidence() == null ? 0 : block.confidence().doubleValue());
    }

    private record DetectedLine(String normalized, double confidence) { }
    private record Match(String value, double confidence) { }

    @Override public boolean enabled() { return true; }
}
