package org.pms.silverocean.service.param;

public interface ParamAndPropertyProjection {
    Long getParamId();
    Long getPropertyId();
    String getParamName();
    String getParamValue();
    Long getPropertyParamId();
}
