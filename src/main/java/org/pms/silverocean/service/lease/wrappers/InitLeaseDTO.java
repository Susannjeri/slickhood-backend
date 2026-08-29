package org.pms.silverocean.service.lease.wrappers;

import java.time.LocalDate;

public record InitLeaseDTO(String token, LocalDate moveInDate, LocalDate moveOutDate) {
}
