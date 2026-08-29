package org.pms.silverocean.service.visitor.wrappers;

import jakarta.validation.constraints.NotNull;

public record VisitorDecisionRequest(@NotNull Decision decision, String reason) {
    public enum Decision { APPROVE, DENY }
}
