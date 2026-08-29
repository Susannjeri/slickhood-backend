package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;

public record ChangePhoneDTO(String phoneNumber, @NotBlank String otp) {
}
