package org.pms.silverocean.service.leasedocument;

import org.pms.silverocean.database.pms.entities.LeaseDocument;

import java.util.Locale;

/** Shared by both PDF endpoints so the legacy route cannot substitute current template terms. */
public final class LeaseDocumentPdf {
    private LeaseDocumentPdf() {}

    public static String html(LeaseDocument document) {
        String audit = "<section><h2>Electronic execution record</h2><p>Document #" + document.getId()
                + " — " + LeaseDocumentStatus.displayed(document) + "</p><p>Issuer #" + document.getIssuerUserId() + ": "
                + value(document.getIssuerSignedAt()) + "</p><p>Recipient #" + document.getRecipientUserId() + ": "
                + value(document.getRecipientSignedAt()) + "</p></section>";
        String html = document.getRenderedHtml();
        String lower = html.toLowerCase(Locale.ROOT);
        int bodyClose = lower.lastIndexOf("</body>");
        if (bodyClose >= 0) return html.substring(0, bodyClose) + audit + html.substring(bodyClose);
        int htmlClose = lower.lastIndexOf("</html>");
        return htmlClose >= 0
                ? html.substring(0, htmlClose) + audit + html.substring(htmlClose)
                : html + audit;
    }

    private static String value(Object value) { return value == null ? "Not signed" : value.toString(); }
}
