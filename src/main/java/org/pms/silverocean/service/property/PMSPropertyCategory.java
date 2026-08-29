package org.pms.silverocean.service.property;

import lombok.Getter;
import org.pms.silverocean.service.I18NService;

import java.util.Set;
import java.util.stream.Collectors;

@Getter
public enum PMSPropertyCategory {
    RESIDENTIAL("property.category.display.name.residential", "property.type.residential"),
    COMMERCIAL("property.category.display.name.commercial", "property.type.commercial"),
    MIXED("property.category.display.name.mixed", "property.type.mixed"),
    INDUSTRIAL("property.category.display.name.industrial", "property.type.industrial"),
    HOSPITALITY("property.category.display.name.hospitality", "property.type.hospitality"),
    LAND("property.category.display.name.land", "property.type.land");


    private final String displayName;
    private final String description;
    private static final Set<PMSPropertyCategory> CACHE = Set.of(values());

    PMSPropertyCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public static Set<PMSPropertyCategory> getMatching(String normalizedQuery, I18NService i18n) {
        return CACHE.stream()
                .filter(cat -> cat.matches(normalizedQuery, i18n))
                .collect(Collectors.toSet());
    }

    private boolean matches(String normalizedQuery, I18NService i18n) {
        String enumName = this.name().toUpperCase();
        String localizedName = i18n.getLocalizedMessage(this.displayName)
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "");

        return enumName.contains(normalizedQuery) || localizedName.contains(normalizedQuery);
    }
}
