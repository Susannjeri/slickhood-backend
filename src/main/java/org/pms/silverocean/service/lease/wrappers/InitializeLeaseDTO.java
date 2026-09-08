package org.pms.silverocean.service.lease.wrappers;

import jakarta.validation.constraints.NotBlank;

/** The tenant accepts the landlord-defined invitation; lease dates are never client supplied. */
public record InitializeLeaseDTO(@NotBlank String token) {
}
