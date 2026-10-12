package org.pms.silverocean.service.sp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.sp.dao.RiskScoreDao;
import org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO;
import org.pms.silverocean.service.sp.wrappers.RiskScoreDTO;
import org.pms.silverocean.service.sp.wrappers.ServiceDetailsDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.service.sp.dao.ServiceRatingDao;

@Service
@Slf4j
@RequiredArgsConstructor
public class ServiceDirectoryService {
    private static final java.util.Set<org.pms.silverocean.service.sp.enums.PricingUnit> SUPPORTED_PRICING_UNITS =
            java.util.Set.of(org.pms.silverocean.service.sp.enums.PricingUnit.PER_HOUR,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_DAY,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_WEEK,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_MONTH,
                    org.pms.silverocean.service.sp.enums.PricingUnit.PER_JOB,
                    org.pms.silverocean.service.sp.enums.PricingUnit.FIXED);
    private final ProviderServiceDao serviceDao;
    private final RiskScoreDao riskScoreDao;
    private final ServiceRatingDao ratingDao;

    public Page<ProviderServiceDTO> listDirectory(Pageable pageable, Long categoryId) {
        return enrichRatings(serviceDao.findListedByCategoryEnriched(bounded(pageable), categoryId));
    }

    public Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query,
                                                     BigDecimal minAmount, BigDecimal maxAmount,
                                                     org.pms.silverocean.service.sp.enums.PricingUnit pricingUnit,
                                                     Double latitude, Double longitude, Double radiusKm) {
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Minimum price cannot exceed maximum price.");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Latitude and longitude must be supplied together.");
        }
        if (pricingUnit != null && !SUPPORTED_PRICING_UNITS.contains(pricingUnit)) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Choose per hour, per day, per week, per month, per job or fixed pricing.");
        }
        Double minLat=null,maxLat=null,minLng=null,maxLng=null;
        if(latitude!=null){double radius=Math.max(1,Math.min(radiusKm==null?25:radiusKm,100));double latDelta=radius/111.0;double lngDelta=radius/(111.0*Math.max(.2,Math.cos(Math.toRadians(latitude))));minLat=latitude-latDelta;maxLat=latitude+latDelta;minLng=longitude-lngDelta;maxLng=longitude+lngDelta;}
        var amountOrder=pageable.getSort().getOrderFor("amount");
        boolean lowestPriceFirst=amountOrder!=null && amountOrder.isAscending();
        Double orderingLatitude=lowestPriceFirst?null:latitude;
        Double orderingLongitude=lowestPriceFirst?null:longitude;
        return enrichRatings(serviceDao.searchDirectory(bounded(pageable), categoryId, StringUtils.trimToNull(query), minAmount, maxAmount, pricingUnit,
                orderingLatitude,orderingLongitude,minLat,maxLat,minLng,maxLng));
    }

    public Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query,
                                                     BigDecimal minAmount, BigDecimal maxAmount) {
        return searchDirectory(pageable,categoryId,query,minAmount,maxAmount,null,null,null,null);
    }

    public Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query,
                                                     BigDecimal minAmount, BigDecimal maxAmount,
                                                     Double latitude, Double longitude, Double radiusKm) {
        return searchDirectory(pageable, categoryId, query, minAmount, maxAmount, null,
                latitude, longitude, radiusKm);
    }

    public ServiceDetailsDTO getServiceDetails(long serviceId) {
        ProviderServiceDTO service = serviceDao.findByIdEnriched(serviceId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        var providerRating = ratingDao.aggregateByProfileIds(java.util.List.of(service.profileId())).stream().findFirst().orElse(null);
        service = service.withRatings(providerRating == null ? 0 : providerRating.getAverageRating(),
                providerRating == null ? 0 : providerRating.getRatingCount()).withPublicLocation();
        var riskScore = riskScoreDao.findLatestByServiceId(serviceId)
                .map(RiskScoreDTO::new)
                .orElse(null);
        return new ServiceDetailsDTO(service, riskScore, ratingDao.avgStars(serviceId), ratingDao.countByServiceId(serviceId));
    }

    private Page<ProviderServiceDTO> enrichRatings(Page<ProviderServiceDTO> page) {
        if (page.isEmpty()) return page;
        java.util.Map<Long,org.pms.silverocean.database.pms.ServiceRatingRepo.ProviderRatingAggregate> byProfile =
                ratingDao.aggregateByProfileIds(page.getContent().stream().map(ProviderServiceDTO::profileId).distinct().toList())
                        .stream().collect(java.util.stream.Collectors.toMap(
                                org.pms.silverocean.database.pms.ServiceRatingRepo.ProviderRatingAggregate::getProfileId,
                                java.util.function.Function.identity()));
        return page.map(service -> {
            var aggregate=byProfile.get(service.profileId());
            return service.withRatings(aggregate == null ? 0 : aggregate.getAverageRating(),
                    aggregate == null ? 0 : aggregate.getRatingCount()).withPublicLocation();
        });
    }

    private Pageable bounded(Pageable pageable) {
        int page=Math.max(0,pageable.getPageNumber());
        int size=Math.min(48,Math.max(1,pageable.getPageSize()));
        return org.springframework.data.domain.PageRequest.of(page,size);
    }
}
