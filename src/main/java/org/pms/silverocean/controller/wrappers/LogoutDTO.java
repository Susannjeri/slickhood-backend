package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.Email;

public record LogoutDTO(@Email(message = "Email should be a valid email address") String email) {
}
