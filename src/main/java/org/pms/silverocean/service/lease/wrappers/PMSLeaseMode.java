package org.pms.silverocean.service.lease.wrappers;

import org.pms.silverocean.service.property.wrappers.UnitChargeProjection;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public enum PMSLeaseMode {
    RENT {
        @Override
        public Map<String, Object> defaultTemplateData() {
            Map<String, Object> data = new HashMap<>();


            data.put("selfRenewable", true);

            data.put("lease_duration", 12);
            data.put("notice_period", 1);
            data.put("deposit_return_days", 30);
            data.put("rent_due_date", 1);
            data.put("entry_notice_days", 24);
            data.put("repair_threshold", 20000.00);

            data.put("pets_policy", "Not Allowed");

            data.put("unitCharges", defaultChargesForRent());
            return data;
        }
    },

    SALE {
        @Override
        public Map<String, Object> defaultTemplateData() {
            Map<String, Object> data = new HashMap<>();

            data.put("selfRenewable", false);

            data.put("pets_policy", "Pets are permitted only with prior written approval. Tenants are responsible for all pet-related damage, cleanliness, and compliance with local laws. The Landlord may revoke permission if pets cause nuisance, damage, or safety concerns.");
            data.put("unitCharges", defaultChargesForSale());

            return data;
        }
    },

    SERVICE_CHARGE {
        @Override
        public Map<String, Object> defaultTemplateData() {
            Map<String, Object> data = new HashMap<>();

            data.put("selfRenewable", false);

            data.put("pets_policy", "Pets are permitted only with prior written approval. Tenants are responsible for all pet-related damage, cleanliness, and compliance with local laws. The Landlord may revoke permission if pets cause nuisance, damage, or safety concerns.");
            data.put("unitCharges", defaultChargesForSale());

            return data;
        }
    };

    public abstract Map<String, Object> defaultTemplateData();

    private static List<Map<String, Object>> defaultChargesForRent() {
        return List.of(
                Map.of("id", 0L, "createdOn", ZonedDateTime.now(), "chargeId", 2L, "chargeName", "WATER", "amount", 100.0, "periodId", "MONTHLY"),
                Map.of("id", 0L, "createdOn", ZonedDateTime.now(), "chargeId", 3L, "chargeName", "SECURITY", "amount", 100000.0, "periodId", "ONE_TIME"),
                Map.of("id", 0L, "createdOn", ZonedDateTime.now(), "chargeId", 4L, "chargeName", "GARBAGE", "amount", 400.0, "periodId", "MONTHLY"),
                Map.of("id", 0L, "createdOn", ZonedDateTime.now(), "chargeId", 7L, "chargeName", "LATE_FEES", "amount", 40000.0, "periodId", "MONTHLY"),
                Map.of("id", 0L, "createdOn", ZonedDateTime.now(), "chargeId", 8L, "chargeName", "ELECTRICITY", "amount", 10.0, "periodId", "MONTHLY")
        );
    }

    private static List<Map<String, Object>> defaultChargesForSale() {
        return List.of(
                Map.of("id", 0L, "createdOn", ZonedDateTime.now(), "chargeId", 1L, "chargeName", "SERVICE", "amount", 50.0, "periodId", "MONTHLY"),
                Map.of("id", 0L, "createdOn", ZonedDateTime.now(), "chargeId", 4L, "chargeName", "GARBAGE", "amount", 5.2, "periodId", "MONTHLY"),
                Map.of("id", 0L, "createdOn", ZonedDateTime.now(), "chargeId", 5L, "chargeName", "PARKING", "amount", 7.2, "periodId", "MONTHLY"),
                Map.of("id", 0L, "createdOn", ZonedDateTime.now(), "chargeId", 6L, "chargeName", "SECURITY", "amount", 8.2, "periodId", "MONTHLY")
        );
    }

    public List<UnitChargeProjection> getDefaultCharges() {
        return ((List<Map<String, Object>>) defaultTemplateData().get("unitCharges"))
                .stream()
                .map(m -> new UnitChargeProjectionDTO(
                        (Long) m.get("id"),
                        (ZonedDateTime) m.get("createdOn"),
                        (Long) m.get("chargeId"),
                        (String) m.get("chargeName"),
                        ((Number) m.get("amount")).doubleValue(),
                        (String) m.get("periodId"),
                        null
                ))
                .collect(Collectors.toList());
    }
}
