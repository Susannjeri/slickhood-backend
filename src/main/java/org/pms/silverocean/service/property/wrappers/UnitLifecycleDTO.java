package org.pms.silverocean.service.property.wrappers;

/**
 * Current customer-facing position of a unit in its rental, homeowner or sale journey.
 * This is derived from authoritative records rather than stored as a second mutable status.
 */
public record UnitLifecycleDTO(String code, String label, String description,
                               boolean invitationBlocked, Long activeInviteId, Long journeyId) {
}
