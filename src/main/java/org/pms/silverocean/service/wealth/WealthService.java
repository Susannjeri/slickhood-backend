package org.pms.silverocean.service.wealth;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.currencyexchange.CurrencyConversionService;
import org.pms.silverocean.service.wrappers.IdNameDescDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

import static org.pms.silverocean.service.wealth.WealthModels.*;
import static org.pms.silverocean.service.wealth.WealthRequests.*;

@Service @RequiredArgsConstructor
public class WealthService {
    private static final long MAX_VAULT_BYTES=20L*1024*1024;
    private static final Set<String> ALLOWED_TYPES=Set.of("application/pdf","image/jpeg","image/png",
            "application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final WealthAssetRepo assetRepo; private final WealthValuationRepo valuationRepo;
    private final WealthCashFlowRepo cashFlowRepo; private final WealthLiabilityRepo liabilityRepo;
    private final WealthObligationRepo obligationRepo; private final WealthVaultDocumentRepo vaultRepo;
    private final WealthGoalRepo goalRepo; private final UserDao userDao; private final GarageService garageService;
    private final CurrencyConversionService currencyConversionService;
    private final UnitRepo unitRepo; private final PMSInvoiceRepo invoiceRepo; private final PropertyRepo propertyRepo;
    @Value("${wealth.base.currency:KES}") private String baseCurrency;

    public List<WealthAsset> assets(){return ownedAssets();}
    public List<IdNameDescDTO> propertyOptions(){return propertyRepo.findAllWealthLinkableByUserId(userDao.getUserId());}
    @Transactional public WealthAsset createAsset(AssetRequest r){
        validatePropertyLink(r.propertyId(),null);
        WealthAsset a=new WealthAsset();apply(a,r);a.setOwnerUserId(userDao.getUserId());a.setCreatedBy(userDao.getUserId());a.setActive(true);
        a=assetRepo.save(a); recordValuation(a,r.currentValue(),r.valuationDate(),"OPENING_VALUE","Initial asset value");return a;
    }
    @Transactional public WealthAsset updateAsset(long id,AssetRequest r){WealthAsset a=asset(id);validatePropertyLink(r.propertyId(),id);BigDecimal old=a.getCurrentValue();LocalDate oldDate=a.getValuationDate();apply(a,r);a=assetRepo.save(a);if(old.compareTo(r.currentValue())!=0||!oldDate.equals(r.valuationDate()))recordValuation(a,r.currentValue(),r.valuationDate(),"ASSET_UPDATE","Value updated from asset record");return a;}
    @Transactional public void archiveAsset(long id){WealthAsset a=asset(id);a.setActive(false);assetRepo.save(a);}
    @Transactional public WealthValuation addValuation(long assetId,ValuationRequest r){WealthAsset a=asset(assetId);a.setCurrentValue(r.amount());a.setValuationDate(r.valuationDate());assetRepo.save(a);return recordValuation(a,r.amount(),r.valuationDate(),r.source(),r.notes());}
    public List<WealthValuation> valuations(long assetId){asset(assetId);return valuationRepo.findAllByAssetIdAndActiveTrueOrderByValuationDateDesc(assetId);}
    @Transactional public WealthCashFlow addCashFlow(long assetId,CashFlowRequest r){asset(assetId);WealthCashFlow f=new WealthCashFlow();f.setAssetId(assetId);f.setFlowType(r.flowType());f.setCategory(r.category());f.setAmount(r.amount());f.setEntryDate(r.entryDate());f.setDescription(r.description());f.setRecurring(r.recurring());stamp(f);return cashFlowRepo.save(f);}
    @Transactional public WealthLiability addLiability(long assetId,LiabilityRequest r){asset(assetId);if(r.outstandingPrincipal().compareTo(r.originalPrincipal())>0)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);WealthLiability l=new WealthLiability();l.setAssetId(assetId);l.setLender(r.lender());l.setCurrency(r.currency().toUpperCase());l.setOriginalPrincipal(r.originalPrincipal());l.setOutstandingPrincipal(r.outstandingPrincipal());l.setAnnualInterestRate(r.annualInterestRate());l.setMonthlyPayment(r.monthlyPayment());l.setStartDate(r.startDate());l.setMaturityDate(r.maturityDate());stamp(l);return liabilityRepo.save(l);}
    @Transactional public WealthObligation addObligation(long assetId,ObligationRequest r){asset(assetId);WealthObligation o=new WealthObligation();o.setAssetId(assetId);o.setObligationType(r.obligationType());o.setTitle(r.title());o.setEffectiveDate(r.effectiveDate());o.setDueDate(r.dueDate());o.setExpiryDate(r.expiryDate());o.setAmount(r.amount());o.setCurrency(r.currency()==null?null:r.currency().toUpperCase());o.setReminderDays(Optional.ofNullable(r.reminderDays()).orElse(30));o.setNotes(r.notes());o.setStatus("OPEN");stamp(o);return obligationRepo.save(o);}
    @Transactional public WealthObligation completeObligation(long id){WealthObligation o=obligationRepo.findByIdAndAssetIdInAndActiveTrue(id,assetIds()).orElseThrow(this::notFound);o.setStatus("COMPLETED");return obligationRepo.save(o);}
    @Transactional public WealthGoal addGoal(GoalRequest r){WealthGoal g=new WealthGoal();g.setOwnerUserId(userDao.getUserId());g.setGoalType(r.goalType());g.setName(r.name());g.setTargetAmount(r.targetAmount());g.setCurrency(r.currency().toUpperCase());g.setTargetDate(r.targetDate());g.setStatus("ACTIVE");stamp(g);return goalRepo.save(g);}

    @Transactional public VaultDocumentView upload(long assetId,String category,LocalDate documentDate,LocalDate expiryDate,String notes,MultipartFile file) throws IOException {
        asset(assetId);validateFile(file);String original=Objects.requireNonNull(file.getOriginalFilename());String safe=java.nio.file.Path.of(original).getFileName().toString();if(!safe.equals(original))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        String path="wealth/"+userDao.getUserId()+"/"+assetId+"/"+UUID.randomUUID()+"/";garageService.uploadFile(path,file);String ref=(path+safe).replaceAll("\\s+","_");
        WealthVaultDocument d=new WealthVaultDocument();d.setAssetId(assetId);d.setCategory(category);d.setDisplayName(safe);d.setFileRef(ref);d.setContentType(file.getContentType());d.setFileSize(file.getSize());d.setChecksumSha256(sha256(file.getBytes()));d.setDocumentDate(documentDate);d.setExpiryDate(expiryDate);d.setNotes(notes);stamp(d);d=vaultRepo.save(d);return new VaultDocumentView(d,garageService.getPresignedUrl(ref));
    }
    public List<VaultDocumentView> documents(long assetId){asset(assetId);return vaultRepo.findAllByAssetIdAndActiveTrueOrderByCreatedOnDesc(assetId).stream().map(d->new VaultDocumentView(d,garageService.getPresignedUrl(d.getFileRef()))).toList();}
    public VaultDocumentView document(long id){WealthVaultDocument d=vaultRepo.findByIdAndAssetIdInAndActiveTrue(id,assetIds()).orElseThrow(this::notFound);return new VaultDocumentView(d,garageService.getPresignedUrl(d.getFileRef()));}
    public Dashboard dashboard(int years,BigDecimal valueGrowth,BigDecimal incomeGrowth,BigDecimal expenseGrowth){
        List<WealthAsset> sourceAssets=ownedAssets();List<Long> ids=sourceAssets.stream().map(WealthAsset::getId).toList();LocalDate today=LocalDate.now();
        List<WealthCashFlow> sourceFlows=ids.isEmpty()?List.of():cashFlowRepo.findAllByAssetIdInAndActiveTrueAndEntryDateBetween(ids,today.minusYears(1).plusDays(1),today);
        List<WealthLiability> sourceLiabilities=ids.isEmpty()?List.of():liabilityRepo.findAllByAssetIdInAndActiveTrue(ids);
        List<WealthObligation> obligations=ids.isEmpty()?List.of():obligationRepo.findAllByAssetIdInAndActiveTrueOrderByDueDateAsc(ids);
        Map<Long,String> assetCurrencies=new HashMap<>();sourceAssets.forEach(a->assetCurrencies.put(a.getId(),a.getCurrency()));
        List<WealthAsset> assets=sourceAssets.stream().map(this::baseAsset).toList();
        List<WealthCashFlow> flows=sourceFlows.stream().map(f->baseFlow(f,assetCurrencies.get(f.getAssetId()))).toList();
        List<WealthLiability> liabilities=sourceLiabilities.stream().map(this::baseLiability).toList();
        List<WealthGoal> goals=goalRepo.findAllByOwnerUserIdAndActiveTrueOrderByTargetDate(userDao.getUserId()).stream().map(this::baseGoal).toList();
        return WealthAnalytics.calculate(assets,flows,liabilities,obligations,goals,operatingInputs(sourceAssets),years,valueGrowth,incomeGrowth,expenseGrowth);
    }

    private void apply(WealthAsset a,AssetRequest r){a.setPropertyId(r.propertyId());a.setAssetType(r.assetType().toUpperCase());a.setName(r.name());a.setReference(r.reference());a.setLocation(r.location());a.setCurrency(r.currency().toUpperCase());a.setAcquisitionCost(Optional.ofNullable(r.acquisitionCost()).orElse(BigDecimal.ZERO));a.setAcquisitionDate(r.acquisitionDate());a.setCurrentValue(r.currentValue());a.setValuationDate(r.valuationDate());a.setStatus(r.status()==null?"ACTIVE":r.status().toUpperCase());}
    private void validatePropertyLink(Long propertyId,Long currentAssetId){if(propertyId==null)return;long userId=userDao.getUserId();propertyRepo.findByIdAndStaffOrOwner(propertyId,userId).orElseThrow(this::notFound);assetRepo.findByOwnerUserIdAndPropertyIdAndActiveTrue(userId,propertyId).filter(existing->!Objects.equals(existing.getId(),currentAssetId)).ifPresent(existing->{throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);});}
    private WealthValuation recordValuation(WealthAsset a,BigDecimal amount,LocalDate date,String source,String notes){WealthValuation v=new WealthValuation();v.setAssetId(a.getId());v.setAmount(amount);v.setValuationDate(date);v.setSource(source);v.setNotes(notes);stamp(v);return valuationRepo.save(v);}
    private WealthAsset asset(long id){return assetRepo.findByIdAndOwnerUserIdAndActiveTrue(id,userDao.getUserId()).orElseThrow(this::notFound);}
    private List<WealthAsset> ownedAssets(){return assetRepo.findAllByOwnerUserIdAndActiveTrueOrderByName(userDao.getUserId());}
    private List<Long> assetIds(){return ownedAssets().stream().map(WealthAsset::getId).toList();}
    private <T extends org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity> void stamp(T e){e.setCreatedBy(userDao.getUserId());e.setActive(true);}
    private PMSCustomException notFound(){return new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND);}
    private void validateFile(MultipartFile file){if(file==null||file.isEmpty()||file.getSize()>MAX_VAULT_BYTES||!ALLOWED_TYPES.contains(file.getContentType()))throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);}
    private String sha256(byte[] bytes){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private BigDecimal base(BigDecimal amount,String currency){return currencyConversionService.convert(Optional.ofNullable(amount).orElse(BigDecimal.ZERO),currency,baseCurrency);}
    private WealthAsset baseAsset(WealthAsset source){WealthAsset a=new WealthAsset();a.setId(source.getId());a.setName(source.getName());a.setAssetType(source.getAssetType());a.setCurrency(baseCurrency);a.setCurrentValue(base(source.getCurrentValue(),source.getCurrency()));a.setAcquisitionCost(base(source.getAcquisitionCost(),source.getCurrency()));a.setValuationDate(source.getValuationDate());a.setActive(true);return a;}
    private WealthCashFlow baseFlow(WealthCashFlow source,String currency){WealthCashFlow f=new WealthCashFlow();f.setAssetId(source.getAssetId());f.setFlowType(source.getFlowType());f.setAmount(base(source.getAmount(),currency));f.setActive(true);return f;}
    private WealthLiability baseLiability(WealthLiability source){WealthLiability l=new WealthLiability();l.setAssetId(source.getAssetId());l.setOutstandingPrincipal(base(source.getOutstandingPrincipal(),source.getCurrency()));l.setActive(true);return l;}
    private WealthGoal baseGoal(WealthGoal source){WealthGoal g=new WealthGoal();g.setId(source.getId());g.setName(source.getName());g.setGoalType(source.getGoalType());g.setTargetAmount(base(source.getTargetAmount(),source.getCurrency()));g.setCurrency(baseCurrency);g.setTargetDate(source.getTargetDate());g.setStatus(source.getStatus());g.setActive(true);return g;}
    private Map<Long,OperatingInput> operatingInputs(List<WealthAsset> assets){
        List<Long> propertyIds=assets.stream().map(WealthAsset::getPropertyId).filter(Objects::nonNull).distinct().toList();if(propertyIds.isEmpty())return Map.of();
        Map<Long,List<Unit>> units=unitRepo.findAllByPropertyIdInAndActiveTrue(propertyIds).stream().collect(java.util.stream.Collectors.groupingBy(Unit::getPropertyId));
        Map<Long,List<PMSInvoice>> invoices=invoiceRepo.findAllByPropertyIdInAndActiveTrueAndPaidFalse(propertyIds).stream().collect(java.util.stream.Collectors.groupingBy(PMSInvoice::getPropertyId));
        Map<Long,OperatingInput> result=new HashMap<>();for(WealthAsset asset:assets){if(asset.getPropertyId()==null)continue;List<Unit> propertyUnits=units.getOrDefault(asset.getPropertyId(),List.of());BigDecimal arrears=invoices.getOrDefault(asset.getPropertyId(),List.of()).stream().map(i->base(BigDecimal.valueOf(i.getPendingAmount()>0?i.getPendingAmount():i.getAmount()),i.getCurrency())).reduce(BigDecimal.ZERO,BigDecimal::add);result.put(asset.getId(),new OperatingInput(propertyUnits.size(),(int)propertyUnits.stream().filter(Unit::isOccupied).count(),arrears));}return result;
    }
}
