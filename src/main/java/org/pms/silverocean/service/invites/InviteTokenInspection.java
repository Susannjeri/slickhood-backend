package org.pms.silverocean.service.invites;

import java.time.LocalDateTime;

/** Minimal, non-sensitive metadata returned by the read-only invitation check. */
public record InviteTokenInspection(String type, LocalDateTime expiresAt, long validForSeconds) {
}
