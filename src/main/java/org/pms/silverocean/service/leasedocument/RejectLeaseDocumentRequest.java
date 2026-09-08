package org.pms.silverocean.service.leasedocument;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectLeaseDocumentRequest(@NotBlank @Size(max = 1000) String reason) {
}
