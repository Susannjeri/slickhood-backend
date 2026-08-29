package org.pms.silverocean.service.visitor.projections;

public interface GuardHostOptionProjection {
    Long getUnitId();
    String getUnitRef();
    Long getPropertyId();
    String getPropertyName();
    Long getHostUserId();
    String getHostName();
}
