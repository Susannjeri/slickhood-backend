package org.pms.silverocean.service.users;

import java.time.LocalDateTime;

public record StaffInviteDTO(String email, String role, LocalDateTime expiresAt) { }
