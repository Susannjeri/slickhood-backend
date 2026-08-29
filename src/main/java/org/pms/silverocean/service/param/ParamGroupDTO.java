package org.pms.silverocean.service.param;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParamGroupDTO(
        String name,
        String type,
        List<ParamDTO> params,
        boolean verified,
        String createdBy) {
    public ParamGroupDTO(String name,
                         String type,
                         List<ParamDTO> params,
                         boolean verified) {
        this(name, type, params, verified, null);
    }
//
//    public ParamGroupDTO(PMSParam param, String name, String WAValue) {
//        this(null, name, param, WAValue, param.isEncrypted());
//    }
//
//    public ParamGroupDTO(Long paramId) {
//        this(paramId, null, null, null, false);
//    }
}
