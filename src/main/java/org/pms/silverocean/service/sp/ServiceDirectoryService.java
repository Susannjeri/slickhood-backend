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
    private final ProviderServiceDao serviceDao;
    private final RiskScoreDao riskScoreDao;
    private final ServiceRatingDao ratingDao;

    public Page<ProviderServiceDTO> listDirectory(Pageable pageable, Long categoryId) {
        return serviceDao.findListedByCategoryEnriched(pageable, categoryId);
    }

    public Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query,
                                                     BigDecimal minAmount, BigDecimal maxAmount,
                                                     Double latitude, Double longitude, Double radiusKm) {
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("Minimum price cannot exceed maximum price");
        }
        if ((latitude == null) != (longitude == null)) throw new IllegalArgumentException("Latitude and longitude must be supplied together");
        Double minLat=null,maxLat=null,minLng=null,maxLng=null;
        if(latitude!=null){double radius=Math.max(1,Math.min(radiusKm==null?25:radiusKm,100));double latDelta=radius/111.0;double lngDelta=radius/(111.0*Math.max(.2,Math.cos(Math.toRadians(latitude))));minLat=latitude-latDelta;maxLat=latitude+latDelta;minLng=longitude-lngDelta;maxLng=longitude+lngDelta;}
        return serviceDao.searchDirectory(pageable, categoryId, StringUtils.trimToNull(query), minAmount, maxAmount,
                latitude,longitude,minLat,maxLat,minLng,maxLng);
    }

    public Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query,
                                                     BigDecimal minAmount, BigDecimal maxAmount) {
        return searchDirectory(pageable,categoryId,query,minAmount,maxAmount,null,null,null);
    }

    public ServiceDetailsDTO getServiceDetails(long serviceId) {
        ProviderServiceDTO service = serviceDao.findByIdEnriched(serviceId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        var riskScore = riskScoreDao.findLatestByServiceId(serviceId)
                .map(RiskScoreDTO::new)
                .orElse(null);
        return new ServiceDetailsDTO(service, riskScore, ratingDao.avgStars(serviceId), ratingDao.countByServiceId(serviceId));
    }
}
