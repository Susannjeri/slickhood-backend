package org.pms.silverocean.service.property;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.service.I18NService;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public enum PMSPropertyType {
    // Residential
    APARTMENT_BLOCK(PMSPropertyCategory.RESIDENTIAL, "property.type.display.name.apartment.block", "property.type.description.apartment.block"),
    STANDALONE_HOUSE(PMSPropertyCategory.RESIDENTIAL, "property.type.display.name.standalone.house", "property.type.description.standalone.house"),
    MAISONETTE(PMSPropertyCategory.RESIDENTIAL, "property.type.display.name.maisonette", "property.type.description.maisonette"),
    TOWNHOUSE(PMSPropertyCategory.RESIDENTIAL, "property.type.display.name.townhouse", "property.type.description.townhouse"),
    BUNGALOW(PMSPropertyCategory.RESIDENTIAL, "property.type.display.name.bungalow", "property.type.description.bungalow"),
    STUDENT_HOSTEL(PMSPropertyCategory.RESIDENTIAL, "property.type.display.name.student.hostel", "property.type.description.student.hostel"),
    SERVICED_APARTMENT(PMSPropertyCategory.RESIDENTIAL, "property.type.display.name.serviced.apartment", "property.type.description.serviced.apartment"),
    GATED_ESTATE(PMSPropertyCategory.RESIDENTIAL, "property.type.display.name.gated.estate", "property.type.description.gated.estate"),

    // Commercial
    OFFICE_BLOCK_CBD(PMSPropertyCategory.COMMERCIAL, "property.type.display.name.office.block.cbd", "property.type.description.office.block.cbd"),
    GRADE_A_OFFICE(PMSPropertyCategory.COMMERCIAL, "property.type.display.name.grade.a.office", "property.type.description.grade.a.office"),
    RETAIL_SHOP(PMSPropertyCategory.COMMERCIAL, "property.type.display.name.retail.shop", "property.type.description.retail.shop"),
    SHOPPING_MALL(PMSPropertyCategory.COMMERCIAL, "property.type.display.name.shopping.mall", "property.type.description.shopping.mall"),
    MIXED_RETAIL_PLAZA(PMSPropertyCategory.COMMERCIAL, "property.type.display.name.mixed.retail.plaza", "property.type.description.mixed.retail.plaza"),
    BUSINESS_PARK(PMSPropertyCategory.COMMERCIAL, "property.type.display.name.business.park", "property.type.description.business.park"),
    MEDICAL_PLAZA(PMSPropertyCategory.COMMERCIAL, "property.type.display.name.medical.plaza", "property.type.description.medical.plaza"),
    CO_WORKING_SPACE(PMSPropertyCategory.COMMERCIAL, "property.type.display.name.co.working.space", "property.type.description.co.working.space"),

    // Mixed Use
    SHOPS_AND_APARTMENTS(PMSPropertyCategory.MIXED, "property.type.display.name.shops.and.apartments", "property.type.description.shops.and.apartments"),
    OFFICE_AND_RESIDENTIAL(PMSPropertyCategory.MIXED, "property.type.display.name.office.and.residential", "property.type.description.office.and.residential"),
    COMMERCIAL_AND_HOSTELS(PMSPropertyCategory.MIXED, "property.type.display.name.commercial.and.hostels", "property.type.description.commercial.and.hostels"),
    MALL_AND_APARTMENTS(PMSPropertyCategory.MIXED, "property.type.display.name.mall.and.apartments", "property.type.description.mall.and.apartments"),

    // Industrial
    WAREHOUSE(PMSPropertyCategory.INDUSTRIAL, "property.type.display.name.warehouse", "property.type.description.warehouse"),
    GODOWN(PMSPropertyCategory.INDUSTRIAL, "property.type.display.name.godown", "property.type.description.godown"),
    FACTORY_LIGHT(PMSPropertyCategory.INDUSTRIAL, "property.type.display.name.factory.light", "property.type.description.factory.light"),
    FACTORY_HEAVY(PMSPropertyCategory.INDUSTRIAL, "property.type.display.name.factory.heavy", "property.type.description.factory.heavy"),
    EPZ_FACILITY(PMSPropertyCategory.INDUSTRIAL, "property.type.display.name.epz.facility", "property.type.description.epz.facility"),
    COLD_STORAGE(PMSPropertyCategory.INDUSTRIAL, "property.type.display.name.cold.storage", "property.type.description.cold.storage"),

    // Hospitality
    HOTEL(PMSPropertyCategory.HOSPITALITY, "property.type.display.name.hotel", "property.type.description.hotel"),
    BOUTIQUE_HOTEL(PMSPropertyCategory.HOSPITALITY, "property.type.display.name.boutique.hotel", "property.type.description.boutique.hotel"),
    RESORT(PMSPropertyCategory.HOSPITALITY, "property.type.display.name.resort", "property.type.description.resort"),
    AIRBNB_UNIT(PMSPropertyCategory.HOSPITALITY, "property.type.display.name.airbnb.unit", "property.type.description.airbnb.unit"),
    GUEST_HOUSE(PMSPropertyCategory.HOSPITALITY, "property.type.display.name.guest.house", "property.type.description.guest.house"),

    // Land [cite: 2]
    SERVICED_PLOT(PMSPropertyCategory.LAND, "property.type.display.name.serviced.plot", "property.type.description.serviced.plot"),
    RESIDENTIAL_PLOT(PMSPropertyCategory.LAND, "property.type.display.name.residential.plot", "property.type.description.residential.plot"),
    COMMERCIAL_PLOT(PMSPropertyCategory.LAND, "property.type.display.name.commercial.plot", "property.type.description.commercial.plot"),
    AGRICULTURAL_LAND(PMSPropertyCategory.LAND, "property.type.display.name.agricultural.land", "property.type.description.agricultural.land"),
    INDUSTRIAL_PLOT(PMSPropertyCategory.LAND, "property.type.display.name.industrial.plot", "property.type.description.industrial.plot");

    private final PMSPropertyCategory category;
    private final String displayNamePlaceHolder;
    private final String descriptionPlaceHolder;

    // Cache for filtering
    private static final Map<PMSPropertyCategory, Set<PMSPropertyType>> BY_CATEGORY = new EnumMap<>(PMSPropertyCategory.class);
    private static final Set<PMSPropertyType> TYPE_CACHE = Set.of(values());

    static {
        for (PMSPropertyType type : values()) {
            BY_CATEGORY.computeIfAbsent(type.category, k -> new HashSet<>()).add(type);
        }
    }

    PMSPropertyType(PMSPropertyCategory category, String displayNamePlaceHolder, String descriptionPlaceHolder) {
        this.category = category;
        this.displayNamePlaceHolder = displayNamePlaceHolder;
        this.descriptionPlaceHolder = descriptionPlaceHolder;
    }

    public static Set<PMSPropertyType> search(String query, I18NService i18n) {
        if (StringUtils.isBlank(query)) {
            return EnumSet.allOf(PMSPropertyType.class);
        }

        String normalizedQuery = query.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");

        Set<PMSPropertyType> results = new HashSet<>();
        // 1. Identify categories that match the query (e.g., "RE" matches RESIDENTIAL)
        PMSPropertyCategory.getMatching(normalizedQuery, i18n).forEach(cat -> {
            Set<PMSPropertyType> types = BY_CATEGORY.get(cat);
            if (types != null) results.addAll(types);
        });

        // Add types where the name itself matches (e.g., "RE" matches RETAIL_SHOP)
        results.addAll(getMatching(normalizedQuery, i18n));

        return results;
    }

    private static Set<PMSPropertyType> getMatching(String normalizedQuery, I18NService i18NService) {
        return TYPE_CACHE.stream()
                .filter(cat -> cat.matches(normalizedQuery, i18NService))
                .collect(Collectors.toSet());
    }

    private boolean matches(String normalizedQuery, I18NService i18n) {
        String enumName = this.name().toUpperCase();
        String localizedName = i18n.getLocalizedMessage(this.getDisplayNamePlaceHolder())
                .toUpperCase().replaceAll("[^A-Z0-9]", "");

        return enumName.contains(normalizedQuery) || localizedName.contains(normalizedQuery);
    }
}
