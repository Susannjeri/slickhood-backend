package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceDetailsDTO(ProviderServiceDTO service, RiskScoreDTO riskScore,
                                double averageRating, long ratingCount) {}
