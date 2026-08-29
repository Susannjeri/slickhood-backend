package org.pms.silverocean.service.subscription;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Partial catalog update: upsert each feature/quota by key. Omit a list or pass empty to leave that side unchanged.
 */
public record PlanCatalogUpsertDTO(
        List<@Valid PlanFeatureDTO> features,
        List<@Valid PlanQuotaDTO> quotas
) {
}
