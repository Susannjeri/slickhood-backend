package org.pms.silverocean.service.kyc;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.KycDocument;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KycDocumentViewTest {

    @Test
    void exposesUnreadableDocumentNumberAsAFieldLevelBlockingIssue() {
        KycDocument document = document(DocumentStatus.REJECTED, 70.0);

        KycDocumentView view = KycDocumentView.from(document, Map.of(
                "_validationWarnings", "Could not confidently extract: document number"
        ), null);

        assertThat(view.validationIssues()).singleElement().satisfies(issue -> {
            assertThat(issue.field()).isEqualTo("documentNumber");
            assertThat(issue.code()).isEqualTo("DOCUMENT_NUMBER_UNREADABLE");
            assertThat(issue.blocking()).isTrue();
            assertThat(issue.guidance()).contains("complete number");
        });
    }

    @Test
    void exposesNameMismatchAgainstTheExactExtractedField() {
        KycDocument document = document(DocumentStatus.REJECTED, 98.0);

        KycDocumentView view = KycDocumentView.from(document, Map.of(
                "fullName", "DIFFERENT TEST PERSON",
                "_validationWarnings", "Name on the document does not match the account name"
        ), null);

        assertThat(view.validationIssues()).singleElement().satisfies(issue -> {
            assertThat(issue.field()).isEqualTo("fullName");
            assertThat(issue.code()).isEqualTo("NAME_MISMATCH");
            assertThat(issue.message()).contains("does not match");
        });
    }

    @Test
    void exposesAcceptedUploadWarningsAsReviewerAdvisories() {
        KycDocument document = document(DocumentStatus.OCR_COMPLETE, 98.0);

        KycDocumentView view = KycDocumentView.from(document, Map.of(
                "fullName", "DIFFERENT TEST PERSON",
                "_validationWarnings", "Name on the document does not match the account name"
        ), null);

        assertThat(view.validationIssues()).singleElement().satisfies(issue -> {
            assertThat(issue.field()).isEqualTo("fullName");
            assertThat(issue.code()).isEqualTo("NAME_MISMATCH");
            assertThat(issue.blocking()).isFalse();
        });
    }

    private KycDocument document(DocumentStatus status, double confidence) {
        KycDocument document = new KycDocument();
        document.setId(1L);
        document.setDocumentType(KycDocumentType.NATIONAL_ID_FRONT.name());
        document.setOriginalFileName("test.png");
        document.setContentType("image/png");
        document.setStatus(status.name());
        document.setQualityStatus("PASSED");
        document.setOcrConfidence(confidence);
        return document;
    }
}
