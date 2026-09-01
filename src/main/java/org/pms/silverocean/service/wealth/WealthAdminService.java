package org.pms.silverocean.service.wealth;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.WealthAssetType;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class WealthAdminService {
 private final WealthAssetTypeRepo typeRepo;private final WealthAssetRepo assetRepo;private final WealthVaultDocumentRepo vaultRepo;private final UserDao userDao;
 public record AssetTypeRequest(String code,String label,String description,Integer displayOrder,Boolean marketPricingAllowed,Boolean active){}
 public record AdminSummary(long activeAssets,long owners,long vaultDocuments,long marketPricedAssets,long activeAssetTypes){}
 public List<WealthAssetType> publicTypes(){return typeRepo.findAllByActiveTrueOrderByDisplayOrderAscLabelAsc();}
 public List<WealthAssetType> adminTypes(){return typeRepo.findAllByOrderByDisplayOrderAscLabelAsc();}
 public AdminSummary summary(){return new AdminSummary(assetRepo.countByActiveTrue(),assetRepo.countDistinctOwners(),vaultRepo.countByActiveTrue(),assetRepo.countByPricingModeAndActiveTrue("MARKET"),typeRepo.findAllByActiveTrueOrderByDisplayOrderAscLabelAsc().size());}
 @Transactional public WealthAssetType create(AssetTypeRequest r){String code=code(r.code());if(typeRepo.existsByCodeIgnoreCase(code))throw invalid();WealthAssetType t=new WealthAssetType();t.setCode(code);apply(t,r);t.setCreatedBy(userDao.getUserId());t.setActive(r.active()==null||r.active());return typeRepo.save(t);}
 @Transactional public WealthAssetType update(long id,AssetTypeRequest r){WealthAssetType t=typeRepo.findById(id).orElseThrow(this::notFound);if(r.code()!=null&&!t.getCode().equalsIgnoreCase(code(r.code())))throw invalid();apply(t,r);if(r.active()!=null)t.setActive(r.active());return typeRepo.save(t);}
 public WealthAssetType requireActive(String code){return typeRepo.findByCodeIgnoreCase(code).filter(WealthAssetType::isActive).orElseThrow(this::invalid);}
 public WealthAssetType requireForAsset(String code,String existingCode){WealthAssetType type=typeRepo.findByCodeIgnoreCase(code).orElseThrow(this::invalid);if(!type.isActive()&&!type.getCode().equalsIgnoreCase(existingCode))throw invalid();return type;}
 private void apply(WealthAssetType t,AssetTypeRequest r){if(StringUtils.isBlank(r.label())||r.label().trim().length()>100)throw invalid();t.setLabel(r.label().trim());t.setDescription(StringUtils.left(StringUtils.trimToNull(r.description()),500));t.setDisplayOrder(r.displayOrder()==null?100:Math.max(0,r.displayOrder()));t.setMarketPricingAllowed(Boolean.TRUE.equals(r.marketPricingAllowed()));}
 private String code(String v){if(StringUtils.isBlank(v))throw invalid();String c=v.trim().toUpperCase(Locale.ROOT);if(!c.matches("[A-Z][A-Z0-9_]{1,39}"))throw invalid();return c;}
 private PMSCustomException invalid(){return new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);}private PMSCustomException notFound(){return new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND);}
}
