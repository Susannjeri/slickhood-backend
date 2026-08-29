package org.pms.silverocean.service.visitor.enums;

import lombok.Getter;

public enum VisitorCategory {
    GUEST("visitor.category.name.guest"),
    DELIVERY("visitor.category.name.delivery"),
    CONTRACTOR("visitor.category.name.contractor");

    @Getter
    private final String name;


    VisitorCategory(String name) {
        this.name = name;
    }
}
