package org.pms.silverocean.service.auth.roles.wrappers;

import java.time.LocalDateTime;

public record Staff(long id, String email, String name, LocalDateTime joinedOn) {
}
