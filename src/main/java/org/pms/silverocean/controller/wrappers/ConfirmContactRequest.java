package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;

public record ConfirmContactRequest(@NotBlank String otp) { }
