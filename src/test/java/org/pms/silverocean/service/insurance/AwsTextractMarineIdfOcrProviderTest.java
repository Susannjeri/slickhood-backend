package org.pms.silverocean.service.insurance;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AwsTextractMarineIdfOcrProviderTest {
    @Test
    void readsEveryRequiredDeclarationFieldFromATextractTable() {
        AwsTextractMarineIdfOcrProvider provider = new AwsTextractMarineIdfOcrProvider(mock(TextractClient.class));
        String[] headers = {"IDF Number", "Importer Name", "Importer PIN", "Country of Origin", "Port of Discharge",
                "HS Code", "Full Description and Application", "FOB Value", "Mode of Transport", "Net Mass", "Quantity", "Unit of Measure"};
        String[] values = {"2026IM000123", "Example Importer Limited", "P051234567A", "China", "Mombasa",
                "8703.23.90", "Industrial pumps", "KES 4,500,000.00", "Sea freight", "1,250 KG", "20", "PCS"};
        List<Block> blocks = new ArrayList<>();
        for (int column = 0; column < headers.length; column++) {
            blocks.add(cell(1, column + 1, headers[column]));
            blocks.add(cell(2, column + 1, values[column]));
        }

        InsuranceModels.MarineIdfOcrView result = provider.fromBlocks(blocks);

        assertThat(result.idfNumber()).isEqualTo("2026IM000123");
        assertThat(result.importerName()).isEqualTo("Example Importer Limited");
        assertThat(result.importerPin()).isEqualTo("P051234567A");
        assertThat(result.origin()).isEqualTo("China");
        assertThat(result.portOfDischarge()).isEqualTo("Mombasa");
        assertThat(result.hsCode()).isEqualTo("8703.23.90");
        assertThat(result.descriptionAndApplication()).isEqualTo("Industrial pumps");
        assertThat(result.fobValue()).isEqualTo("4500000.00");
        assertThat(result.transportMode()).isEqualTo("SEA");
        assertThat(result.netMass()).isEqualTo("1250");
        assertThat(result.quantity()).isEqualTo("20");
        assertThat(result.unitOfMeasure()).isEqualTo("PCS");
        assertThat(result.reviewFields()).isEmpty();
    }

    @Test
    void flagsMissingFieldsForReviewInsteadOfInventingValues() {
        AwsTextractMarineIdfOcrProvider provider = new AwsTextractMarineIdfOcrProvider(mock(TextractClient.class));
        InsuranceModels.MarineIdfOcrView result = provider.fromBlocks(List.of(
                cell(1, 1, "IDF Number"), cell(2, 1, "2026IM000123")));
        assertThat(result.idfNumber()).isEqualTo("2026IM000123");
        assertThat(result.reviewFields()).contains("Importer name", "Importer PIN", "FOB value", "Quantity");
    }

    private Block cell(int row, int column, String text) {
        return Block.builder().blockType(BlockType.CELL).rowIndex(row).columnIndex(column).text(text).confidence(96f).build();
    }
}
