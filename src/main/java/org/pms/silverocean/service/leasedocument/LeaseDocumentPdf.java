package org.pms.silverocean.service.leasedocument;

import org.pms.silverocean.database.pms.entities.LeaseDocument;

/** Shared by both PDF endpoints so the legacy route cannot substitute current template terms. */
public final class LeaseDocumentPdf {
    private LeaseDocumentPdf() {}

    public static String html(LeaseDocument document) {
        String audit = "<section><h2>Electronic execution record</h2><p>Document #" + document.getId()
                + " — " + LeaseDocumentStatus.displayed(document) + "</p><p>Issuer #" + document.getIssuerUserId() + ": "
                + value(document.getIssuerSignedAt()) + "</p><p>Recipient #" + document.getRecipientUserId() + ": "
                + value(document.getRecipientSignedAt()) + "</p></section>";
        String html = document.getRenderedHtml();
        return html.contains("</body>") ? html.replace("</body>", audit + "</body>") : html + audit;
    }

    private static String value(Object value) { return value == null ? "Not signed" : value.toString(); }
}
