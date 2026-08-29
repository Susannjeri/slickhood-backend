package org.pms.silverocean.service.visitor.wrappers;

import java.time.ZonedDateTime;

public record AccessDecisionDTO(boolean granted, String reasonCode, String correlationId,
                                Long visitorId, String visitorName, String unitRef,
                                String visitType, ZonedDateTime decidedAt) {}
