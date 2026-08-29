package org.pms.silverocean.service.property;

import lombok.Getter;

@Getter
public enum PMSUnitTypes {
    // Residential - Apartments/Flats
    BEDSITTER("property.unit.type.bedsitter.label", "property.unit.type.bedsitter.description"),
    STUDIO("property.unit.type.studio.label", "property.unit.type.studio.description"),
    ONE_BEDROOM("property.unit.type.one_bedroom.label", "property.unit.type.one_bedroom.description"),
    TWO_BEDROOM("property.unit.type.two_bedroom.label", "property.unit.type.two_bedroom.description"),
    THREE_BEDROOM("property.unit.type.three_bedroom.label", "property.unit.type.three_bedroom.description"),
    FOUR_BEDROOM("property.unit.type.four_bedroom.label", "property.unit.type.four_bedroom.description"),
    FIVE_BEDROOM_PLUS("property.unit.type.five_bedroom_plus.label", "property.unit.type.five_bedroom_plus.description"),
    PENTHOUSE("property.unit.type.penthouse.label", "property.unit.type.penthouse.description"),

    // Residential - Rooms/Shared
    SINGLE_ROOM("property.unit.type.single_room.label", "property.unit.type.single_room.description"),
    SHARED_ROOM("property.unit.type.shared_room.label", "property.unit.type.shared_room.description"),
    ENSUITE_ROOM("property.unit.type.ensuite_room.label", "property.unit.type.ensuite_room.description"),
    STUDENT_ROOM("property.unit.type.student_room.label", "property.unit.type.student_room.description"),

    // Residential - Complexes
    VILLAS("property.unit.type.villas.label", "property.unit.type.villas.description"),
    TOWNHOUSES("property.unit.type.townhouses.label", "property.unit.type.townhouses.description"),
    APARTMENT_UNIT("property.unit.type.apartment_unit.label", "property.unit.type.apartment_unit.description"),

    // Commercial - Offices
    OPEN_PLAN("property.unit.type.open_plan.label", "property.unit.type.open_plan.description"),
    PARTITIONED_OFFICE("property.unit.type.partitioned_office.label", "property.unit.type.partitioned_office.description"),
    EXECUTIVE_SUITE("property.unit.type.executive_suite.label", "property.unit.type.executive_suite.description"),
    WHOLE_FLOOR("property.unit.type.whole_floor.label", "property.unit.type.whole_floor.description"),
    HALF_FLOOR("property.unit.type.half_floor.label", "property.unit.type.half_floor.description"),
    OFFICE_UNIT("property.unit.type.office_unit.label", "property.unit.type.office_unit.description"),
    PRIVATE_OFFICE("property.unit.type.private_office.label", "property.unit.type.private_office.description"),
    DESK("property.unit.type.desk.label", "property.unit.type.desk.description"),

    // Commercial - Retail
    SHOP("property.unit.type.shop.label", "property.unit.type.shop.description"),
    KIOSK("property.unit.type.kiosk.label", "property.unit.type.kiosk.description"),
    ANCHOR_TENANT("property.unit.type.anchor_tenant.label", "property.unit.type.anchor_tenant.description"),
    INLINE_SHOP("property.unit.type.inline_shop.label", "property.unit.type.inline_shop.description"),
    RETAIL_UNIT("property.unit.type.retail_unit.label", "property.unit.type.retail_unit.description"),
    MINI_SUPERMARKET("property.unit.type.mini_supermarket.label", "property.unit.type.mini_supermarket.description"),

    // Specialized
    CLINIC_ROOM("property.unit.type.clinic_room.label", "property.unit.type.clinic_room.description"),
    LAB_SPACE("property.unit.type.lab_space.label", "property.unit.type.lab_space.description"),

    // Industrial
    OPEN_WAREHOUSE("property.unit.type.open_warehouse.label", "property.unit.type.open_warehouse.description"),
    RACKED_WAREHOUSE("property.unit.type.racked_warehouse.label", "property.unit.type.racked_warehouse.description"),
    STORAGE_UNIT("property.unit.type.storage_unit.label", "property.unit.type.storage_unit.description"),
    MANUFACTURING_UNIT("property.unit.type.manufacturing_unit.label", "property.unit.type.manufacturing_unit.description"),
    PRODUCTION_UNIT("property.unit.type.production_unit.label", "property.unit.type.production_unit.description"),
    TEMPERATURE_CONTROLLED_UNIT("property.unit.type.temp_controlled.label", "property.unit.type.temp_controlled.description"),

    // Hospitality
    STANDARD_ROOM("property.unit.type.standard_room.label", "property.unit.type.standard_room.description"),
    DELUXE_ROOM("property.unit.type.deluxe_room.label", "property.unit.type.deluxe_room.description"),
    SUITE("property.unit.type.suite.label", "property.unit.type.suite.description"),
    ROOM("property.unit.type.room.label", "property.unit.type.room.description"),
    COTTAGE("property.unit.type.cottage.label", "property.unit.type.cottage.description"),
    ENTIRE_UNIT("property.unit.type.entire_unit.label", "property.unit.type.entire_unit.description");



    private final String name;
    private final String description;

    PMSUnitTypes(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
