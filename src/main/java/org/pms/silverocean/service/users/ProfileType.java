package org.pms.silverocean.service.users;

import lombok.Getter;

@Getter
public enum ProfileType {
    INDIVIDUAL("profile.type.individual"),
    COMPANY("profile.type.company");

    private final String name;


    ProfileType(String name) {
        this.name = name;
    }
}
