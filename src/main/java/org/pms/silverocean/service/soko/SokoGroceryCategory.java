package org.pms.silverocean.service.soko;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;

import java.util.Arrays;
import java.util.List;

public enum SokoGroceryCategory {
    FRESH_PRODUCE("Fresh produce"), MEAT_POULTRY_SEAFOOD("Meat, poultry & seafood"),
    DAIRY_EGGS("Dairy & eggs"), BAKERY("Bakery"), PANTRY_STAPLES("Pantry staples"),
    NON_ALCOHOLIC_BEVERAGES("Non-alcoholic beverages"), SNACKS_CONFECTIONERY("Snacks & confectionery"),
    FROZEN_FOODS("Frozen foods"), BREAKFAST_CEREALS("Breakfast & cereals"),
    COOKING_OILS_SPICES("Cooking oils & spices");

    private final String label;
    SokoGroceryCategory(String label){this.label=label;}
    public String label(){return label;}
    public record View(String code,String label){}
    public static List<View> views(){return Arrays.stream(values()).map(v->new View(v.name(),v.label)).toList();}
    public static String normalize(String raw){
        String value=raw==null?"":raw.trim();
        return Arrays.stream(values()).filter(v->v.name().equalsIgnoreCase(value)||v.label.equalsIgnoreCase(value))
                .findFirst().map(Enum::name).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                        "Choose a supported grocery category. Alcohol and restricted products are not permitted in Soko."));
    }
}
