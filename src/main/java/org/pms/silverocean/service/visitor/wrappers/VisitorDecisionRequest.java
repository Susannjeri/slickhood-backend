package org.pms.silverocean.service.visitor.wrappers;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VisitorDecisionRequest(@NotNull Decision decision, @Size(max = 250) String reason) {
    public enum Decision { APPROVE, DENY }
}
