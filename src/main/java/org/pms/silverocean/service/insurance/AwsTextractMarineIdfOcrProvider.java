package org.pms.silverocean.service.insurance;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.pms.silverocean.service.insurance.InsuranceModels.MarineIdfOcrView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentRequest;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.EntityType;
import software.amazon.awssdk.services.textract.model.FeatureType;
import software.amazon.awssdk.services.textract.model.RelationshipType;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "kyc.ocr.provider", havingValue = "aws-textract")
public class AwsTextractMarineIdfOcrProvider implements MarineIdfOcrProvider {
    private static final int MAX_PDF_PAGES = 5;
    private static final float PDF_DPI = 180f;
    private static final Pattern NUMBER = Pattern.compile("[0-9][0-9,]*(?:\\.[0-9]+)?");
    private static final Pattern KRA_PIN = Pattern.compile("\\b[A-Z][0-9]{9}[A-Z]\\b", Pattern.CASE_INSENSITIVE);
    private final TextractClient client;

    public AwsTextractMarineIdfOcrProvider(TextractClient client) {
        this.client = client;
    }

    @Override
    public MarineIdfOcrView extract(byte[] document, String contentType) {
        List<Block> blocks = new ArrayList<>();
        for (byte[] page : pages(document, contentType)) {
            blocks.addAll(client.analyzeDocument(AnalyzeDocumentRequest.builder()
                    .featureTypes(FeatureType.FORMS, FeatureType.TABLES)
                    .document(Document.builder().bytes(SdkBytes.fromByteArray(page)).build())
                    .build()).blocks());
        }
        return fromBlocks(blocks);
    }

    MarineIdfOcrView fromBlocks(List<Block> blocks) {
        Evidence evidence = new Evidence(blocks);
        Found idf = evidence.find("idf number", "idf no", "form number", "form no", "import declaration form number");
        Found importer = evidence.find("importer name", "name of importer", "consignee name");
        Found pin = evidence.find("importer pin", "importer kra pin", "pin number", "kra pin");
        Found origin = evidence.find("country of origin", "origin country", "origin");
        Found port = evidence.find("port of discharge", "discharge port");
        Found hs = evidence.find("hs code", "h.s. code", "tariff code", "commodity code");
        Found description = evidence.find("full description and application", "goods description and application", "full description", "description of goods");
        Found fob = evidence.find("fob value", "total fob value", "f.o.b. value", "fob amount");
        Found transport = evidence.find("mode of transport", "transport mode", "means of transport");
        Found mass = evidence.find("net mass", "net weight", "net mass kg");
        Found quantity = evidence.find("quantity", "qty");
        Found unit = evidence.find("unit of measure", "unit of measurement", "uom", "unit");

        String importerPin = pin.value();
        Matcher pinMatcher = KRA_PIN.matcher(importerPin);
        if (pinMatcher.find()) importerPin = pinMatcher.group().toUpperCase(Locale.ROOT);
        String mode = transport.value().toUpperCase(Locale.ROOT);
        if (mode.contains("SEA")) mode = "SEA";
        else if (mode.contains("AIR")) mode = "AIR";

        List<Found> found = List.of(idf, importer, pin, origin, port, hs, description, fob, transport, mass, quantity, unit);
        List<String> names = List.of("IDF number", "Importer name", "Importer PIN", "Origin", "Port of discharge", "HS code",
                "Full description and application", "FOB value", "Mode of transport", "Net mass", "Quantity", "Unit of measure");
        List<String> review = new ArrayList<>();
        for (int index = 0; index < found.size(); index++) {
            if (found.get(index).value().isBlank() || found.get(index).confidence() < 75) review.add(names.get(index));
        }
        double confidence = found.stream().filter(item -> !item.value().isBlank()).mapToDouble(Found::confidence).average().orElse(0);
        return new MarineIdfOcrView(idf.value(), importer.value(), importerPin, origin.value(), port.value(), hs.value(),
                description.value(), numeric(fob.value()), mode, numeric(mass.value()), numeric(quantity.value()), unit.value(),
                Math.round(confidence * 10.0) / 10.0, List.copyOf(review), null);
    }

    private String numeric(String value) {
        Matcher matcher = NUMBER.matcher(value);
        return matcher.find() ? matcher.group().replace(",", "") : "";
    }

    private List<byte[]> pages(byte[] bytes, String contentType) {
        if (!"application/pdf".equalsIgnoreCase(contentType)) return List.of(bytes);
        try (PDDocument document = PDDocument.load(bytes)) {
            if (document.isEncrypted() || document.getNumberOfPages() == 0) throw new IllegalArgumentException("The PDF is encrypted or empty");
            PDFRenderer renderer = new PDFRenderer(document);
            List<byte[]> result = new ArrayList<>();
            for (int page = 0; page < Math.min(document.getNumberOfPages(), MAX_PDF_PAGES); page++) {
                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(renderer.renderImageWithDPI(page, PDF_DPI, ImageType.RGB), "png", output)) throw new IOException("No PNG encoder");
                    result.add(output.toByteArray());
                }
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("The IDF/IM0 PDF could not be read", exception);
        }
    }

    private record Found(String value, double confidence) {
        private static Found empty() { return new Found("", 0); }
    }

    private static final class Evidence {
        private final Map<String, Found> keyValues = new LinkedHashMap<>();
        private final List<Cell> cells = new ArrayList<>();
        private final List<Found> lines = new ArrayList<>();

        private Evidence(List<Block> blocks) {
            Map<String, Block> byId = new LinkedHashMap<>();
            blocks.stream().filter(block -> block.id() != null).forEach(block -> byId.put(block.id(), block));
            for (Block block : blocks) {
                if (block.blockType() == BlockType.LINE && block.text() != null) lines.add(new Found(block.text().trim(), confidence(block)));
                if (block.blockType() == BlockType.CELL) cells.add(new Cell(block.rowIndex(), block.columnIndex(), text(block, byId), confidence(block)));
                if (block.blockType() == BlockType.KEY_VALUE_SET && block.entityTypes().contains(EntityType.KEY)) {
                    String key = text(block, byId);
                    String value = block.relationships().stream().filter(r -> r.type() == RelationshipType.VALUE).flatMap(r -> r.ids().stream())
                            .map(byId::get).filter(Objects::nonNull).map(valueBlock -> text(valueBlock, byId)).filter(s -> !s.isBlank()).findFirst().orElse("");
                    if (!key.isBlank() && !value.isBlank()) keyValues.put(normalize(key), new Found(value, confidence(block)));
                }
            }
        }

        private Found find(String... aliases) {
            List<String> normalizedAliases = Arrays.stream(aliases).map(Evidence::normalize).toList();
            Optional<Found> keyed = keyValues.entrySet().stream().filter(entry -> normalizedAliases.stream()
                    .anyMatch(alias -> entry.getKey().contains(alias) || alias.contains(entry.getKey()))).map(Map.Entry::getValue).findFirst();
            if (keyed.isPresent()) return keyed.get();
            for (Cell header : cells) {
                if (normalizedAliases.stream().noneMatch(alias -> normalize(header.text()).contains(alias))) continue;
                Optional<Cell> below = cells.stream().filter(cell -> cell.column() == header.column() && cell.row() == header.row() + 1 && !cell.text().isBlank()).findFirst();
                if (below.isPresent()) return new Found(below.get().text(), Math.min(header.confidence(), below.get().confidence()));
                Optional<Cell> beside = cells.stream().filter(cell -> cell.row() == header.row() && cell.column() == header.column() + 1 && !cell.text().isBlank()).findFirst();
                if (beside.isPresent()) return new Found(beside.get().text(), Math.min(header.confidence(), beside.get().confidence()));
            }
            for (int index = 0; index < lines.size(); index++) {
                Found line = lines.get(index);
                String normalized = normalize(line.value());
                for (String alias : normalizedAliases) {
                    int position = normalized.indexOf(alias);
                    if (position < 0) continue;
                    String sameLine = line.value().replaceFirst("(?i)^.*?" + Pattern.quote(alias) + "\\s*[:#.-]?\\s*", "").trim();
                    if (!sameLine.isBlank() && !normalize(sameLine).equals(normalized)) return new Found(sameLine, line.confidence());
                    if (index + 1 < lines.size()) return lines.get(index + 1);
                }
            }
            return Found.empty();
        }

        private static String text(Block block, Map<String, Block> byId) {
            if (block == null) return "";
            if (block.text() != null && !block.text().isBlank()) return block.text().trim();
            return block.relationships().stream().filter(r -> r.type() == RelationshipType.CHILD).flatMap(r -> r.ids().stream())
                    .map(byId::get).filter(Objects::nonNull).filter(child -> child.blockType() == BlockType.WORD || child.blockType() == BlockType.SELECTION_ELEMENT)
                    .map(child -> child.blockType() == BlockType.SELECTION_ELEMENT ? child.selectionStatusAsString() : child.text())
                    .filter(Objects::nonNull).reduce((left, right) -> left + " " + right).orElse("").trim();
        }

        private static double confidence(Block block) { return block.confidence() == null ? 0 : block.confidence(); }
        private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }
        private record Cell(int row, int column, String text, double confidence) { }
    }
}
