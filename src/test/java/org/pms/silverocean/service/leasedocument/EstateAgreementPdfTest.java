package org.pms.silverocean.service.leasedocument;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.LeaseDocument;
import org.pms.silverocean.service.mustache.RenderService;
import static org.junit.jupiter.api.Assertions.*;

class EstateAgreementPdfTest {
    @Test void signedEstatePdfContainsTheHomeTermsAndBothSignatureTimes() throws Exception {
        var document = new LeaseDocument();
        document.setId(81L); document.setPropertyId(11L); document.setUnitId(77L);
        document.setDocumentType(LeaseDocumentType.ESTATE_RESIDENTIAL_AGREEMENT);
        document.setStatus(LeaseDocumentStatus.SIGNED);
        document.setIssuerUserId(1L); document.setRecipientUserId(2L);
        document.setIssuerSignedAt(LocalDateTime.of(2026,9,7,9,0));
        document.setRecipientSignedAt(LocalDateTime.of(2026,9,7,10,0));
        String snapshot = "<html><body><h1>Estate Residential Agreement</h1><p>Acacia Estate / Home A-101</p><p>Service charge KES 7500</p><p>This does not create a tenancy.</p></body></html>";
        document.setRenderedHtml(snapshot);
        try (var output = new ByteArrayOutputStream()) {
            new RenderService(com.samskivert.mustache.Mustache.compiler(),null).toPdf(LeaseDocumentPdf.html(document),output);
            try (var pdf = org.apache.pdfbox.pdmodel.PDDocument.load(output.toByteArray())) {
                String text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
                for (String expected : new String[]{"Estate Residential Agreement","Home A-101","KES 7500","SIGNED","2026-09-07T09:00","2026-09-07T10:00"})
                    assertTrue(text.contains(expected), expected);
            }
        }
        assertEquals(snapshot,document.getRenderedHtml());
    }
}
