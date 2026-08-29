package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;

public record ChangeEmailDTO(String email, @NotBlank String otp) {
}
