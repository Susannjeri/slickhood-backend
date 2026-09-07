package org.pms.silverocean.service.leasedocument;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.LeaseDocument;
import org.pms.silverocean.service.mustache.RenderService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class SaleOfferDocumentTest {
    private LeaseDocument offer() {
        var d = new LeaseDocument(); d.setId(71L); d.setSaleId(9L); d.setIssuerUserId(1L); d.setRecipientUserId(2L);
        d.setDocumentType(LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER);
        d.setResponseDueDate(LocalDate.now(org.pms.silverocean.common.PMSUtils.getZoneId()).minusDays(1));
        d.setRenderedHtml("<html><head><title>Offer</title></head><body><h1>Letter of offer</h1><p>Unit A-7</p><p>Agreed offer KES 14500000</p></body></html>");
        return d;
    }

    @Test void overdueUnsignedOfferIsExpiredInBothApiAndPdfWithoutMutatingTheSnapshot() {
        for (var status : java.util.List.of(LeaseDocumentStatus.DRAFT,LeaseDocumentStatus.ISSUED,
                LeaseDocumentStatus.ACKNOWLEDGED,LeaseDocumentStatus.PARTIALLY_SIGNED)) {
            var d=offer(); d.setStatus(status); String snapshot=d.getRenderedHtml();
            assertEquals(LeaseDocumentStatus.EXPIRED,new LeaseDocumentDTO(d,2L).status());
            assertTrue(LeaseDocumentPdf.html(d).contains("EXPIRED"));
            assertEquals(status,d.getStatus()); assertEquals(snapshot,d.getRenderedHtml());
        }
    }

    @Test void signedOfferRemainsSignedAndActualPdfPreservesTermsAndExecutionDates() throws Exception {
        var d=offer(); d.setStatus(LeaseDocumentStatus.SIGNED);
        d.setIssuerSignedAt(LocalDateTime.of(2026,9,1,10,0)); d.setRecipientSignedAt(LocalDateTime.of(2026,9,1,11,0));
        assertEquals(LeaseDocumentStatus.SIGNED,new LeaseDocumentDTO(d,2L).status());
        try(var bytes=new ByteArrayOutputStream()) {
            new RenderService(com.samskivert.mustache.Mustache.compiler(),null).toPdf(LeaseDocumentPdf.html(d),bytes);
            try(var pdf=org.apache.pdfbox.pdmodel.PDDocument.load(bytes.toByteArray())) {
                String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
                assertTrue(text.contains("Letter of offer")); assertTrue(text.contains("14500000"));
                assertTrue(text.contains("Unit A-7")); assertTrue(text.contains("SIGNED"));
                assertTrue(text.contains("2026-09-01T10:00")); assertTrue(text.contains("2026-09-01T11:00"));
            }
        }
    }
}
