package org.pms.silverocean.service.leasedocument;

import com.samskivert.mustache.Mustache;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.LeaseDocument;
import org.pms.silverocean.service.mustache.RenderService;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class LeaseDocumentPdfTest {
    @Test void actualPdfContainsFrozenTermsAndBothExecutionDates() throws Exception {
        LeaseDocument snapshot = new LeaseDocument(); snapshot.setId(91L);
        snapshot.setIssuerUserId(1L); snapshot.setRecipientUserId(2L);
        snapshot.setStatus(LeaseDocumentStatus.SIGNED);
        snapshot.setIssuerSignedAt(LocalDateTime.of(2026,9,7,11,0));
        snapshot.setRecipientSignedAt(LocalDateTime.of(2026,9,7,10,0));
        String original = "<html><head><title>Lease</title></head><body><h1>Rental Agreement</h1><p>Frozen rent KES 25000</p></body></html>";
        snapshot.setRenderedHtml(original);
        try (var bytes = new ByteArrayOutputStream()) {
            new RenderService(Mustache.compiler(),null).toPdf(LeaseDocumentPdf.html(snapshot),bytes);
            try (PDDocument pdf = PDDocument.load(bytes.toByteArray())) {
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(text.contains("Frozen rent KES 25000"));
                assertTrue(text.contains("Electronic execution record"));
                assertTrue(text.contains("2026-09-07T11:00"));
                assertTrue(text.contains("2026-09-07T10:00"));
                assertTrue(text.contains("SIGNED"));
            }
        }
        assertEquals(original,snapshot.getRenderedHtml(),"Downloading never mutates the legal snapshot");
    }
}
