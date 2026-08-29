package org.pms.silverocean.controller.wrappers;

import org.pms.silverocean.service.users.ProfileType;

public record UpdateUserDetailsDTO(String name, ProfileType profileType, String identificationNumber, String taxPin) {
}
