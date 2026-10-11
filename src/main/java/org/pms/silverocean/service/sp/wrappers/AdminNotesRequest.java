package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.Size;

public record AdminNotesRequest(@Size(max = 1000) String adminNotes) {
}
