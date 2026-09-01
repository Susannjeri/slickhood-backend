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
import org.pms.silverocean.service.wealth.vault.VaultMalwareScanner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.pms.silverocean.service.wealth.WealthModels.*;
import static org.pms.silverocean.service.wealth.WealthRequests.*;

@Service @RequiredArgsConstructor
public class WealthService {
    private static final long MAX_VAULT_BYTES=20L*1024*1024;
    private static final Set<String> ALLOWED_TYPES=Set.of("application/pdf","image/jpeg","image/png",
            "application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final Set<String> VAULT_CATEGORIES=Set.of("WILL","TRUST","TRUST_DEED","POWER_OF_ATTORNEY","BENEFICIARY_NOMINATION","TITLE_DOCUMENT","TITLE_DEED","SALE_AGREEMENT","LEASE","SHARE_CERTIFICATE","INSURANCE_POLICY","PENSION_STATEMENT","TAX_RECORD","VALUATION","APPROVAL","WARRANTY","LOAN","RECEIPT","OTHER");
    private final WealthAssetRepo assetRepo; private final WealthValuationRepo valuationRepo;
    private final WealthCashFlowRepo cashFlowRepo; private final WealthLiabilityRepo liabilityRepo;
    private final WealthObligationRepo obligationRepo; private final WealthVaultDocumentRepo vaultRepo;
    private final WealthGoalRepo goalRepo; private final UserDao userDao; private final GarageService garageService;
    private final CurrencyConversionService currencyConversionService;
    private final VaultMalwareScanner malwareScanner;
    private final WealthAdminService wealthAdminService;
    private final UnitRepo unitRepo; private final PMSInvoiceRepo invoiceRepo; private final PropertyRepo propertyRepo;
    @Value("${wealth.base.currency:KES}") private String baseCurrency;

    public List<AssetView> assets(){return ownedAssets().stream().map(AssetView::new).toList();}
    public List<IdNameDescDTO> propertyOptions(){return propertyRepo.findAllWealthLinkableByUserId(userDao.getUserId());}
    @Transactional public AssetView createAsset(AssetRequest r){
        validateAssetRequest(r);validatePropertyLink(r.propertyId(),null);
        WealthAsset a=new WealthAsset();apply(a,r);a.setOwnerUserId(userDao.getUserId());a.setCreatedBy(userDao.getUserId());a.setActive(true);
        a=assetRepo.save(a); recordValuation(a,r.currentValue(),r.valuationDate(),"OPENING_VALUE","Initial asset value");return new AssetView(a);
    }
    @Transactional public AssetView updateAsset(long id,AssetRequest r){validateAssetRequest(r);WealthAsset a=asset(id);validatePropertyLink(r.propertyId(),id);BigDecimal old=a.getCurrentValue();LocalDate oldDate=a.getValuationDate();apply(a,r);a=assetRepo.save(a);if(old.compareTo(r.currentValue())!=0||!oldDate.equals(r.valuationDate()))recordValuation(a,r.currentValue(),r.valuationDate(),"ASSET_UPDATE","Value updated from asset record");return new AssetView(a);}
    @Transactional public void archiveAsset(long id){WealthAsset a=asset(id);a.setActive(false);assetRepo.save(a);}
    @Transactional public WealthValuation addValuation(long assetId,ValuationRequest r){if(r.valuationDate().isAfter(LocalDate.now()))throw invalid();WealthAsset a=asset(assetId);a.setCurrentValue(r.amount());a.setValuationDate(r.valuationDate());assetRepo.save(a);return recordValuation(a,r.amount(),r.valuationDate(),r.source(),r.notes());}
    public List<WealthValuation> valuations(long assetId){asset(assetId);return valuationRepo.findAllByAssetIdAndActiveTrueOrderByValuationDateDesc(assetId);}
    @Transactional public WealthCashFlow addCashFlow(long assetId,CashFlowRequest r){asset(assetId);if(r.entryDate().isAfter(LocalDate.now()))throw invalid();WealthCashFlow f=new WealthCashFlow();f.setAssetId(assetId);apply(f,r);stamp(f);return cashFlowRepo.save(f);}
    @Transactional public WealthCashFlow updateCashFlow(long id,CashFlowRequest r){if(r.entryDate().isAfter(LocalDate.now()))throw invalid();WealthCashFlow f=cashFlow(id);apply(f,r);return cashFlowRepo.save(f);}
    @Transactional public void archiveCashFlow(long id){WealthCashFlow f=cashFlow(id);f.setActive(false);cashFlowRepo.save(f);}
    @Transactional public WealthLiability addLiability(long assetId,LiabilityRequest r){asset(assetId);validateLiability(r.originalPrincipal(),r.outstandingPrincipal(),r.startDate(),r.maturityDate());WealthLiability l=new WealthLiability();l.setAssetId(assetId);l.setLender(r.lender().trim());l.setCurrency(r.currency().toUpperCase());l.setOriginalPrincipal(r.originalPrincipal());l.setOutstandingPrincipal(r.outstandingPrincipal());l.setAnnualInterestRate(r.annualInterestRate());l.setMonthlyPayment(r.monthlyPayment());l.setStartDate(r.startDate());l.setMaturityDate(r.maturityDate());stamp(l);return liabilityRepo.save(l);}
    @Transactional public WealthLiability updateLiabilityBalance(long id,LiabilityBalanceRequest r){WealthLiability l=liability(id);validateLiability(l.getOriginalPrincipal(),r.outstandingPrincipal(),l.getStartDate(),r.maturityDate());l.setOutstandingPrincipal(r.outstandingPrincipal());l.setMonthlyPayment(r.monthlyPayment());l.setMaturityDate(r.maturityDate());return liabilityRepo.save(l);}
    @Transactional public void archiveLiability(long id){WealthLiability l=liability(id);l.setActive(false);liabilityRepo.save(l);}
    @Transactional public WealthObligation addObligation(long assetId,ObligationRequest r){asset(assetId);validateObligation(r);WealthObligation o=new WealthObligation();o.setAssetId(assetId);apply(o,r);o.setStatus("OPEN");stamp(o);return obligationRepo.save(o);}
    @Transactional public WealthObligation updateObligation(long id,ObligationRequest r){validateObligation(r);WealthObligation o=obligation(id);apply(o,r);return obligationRepo.save(o);}
    @Transactional public WealthObligation completeObligation(long id){WealthObligation o=obligationRepo.findByIdAndAssetIdInAndActiveTrue(id,assetIds()).orElseThrow(this::notFound);o.setStatus("COMPLETED");return obligationRepo.save(o);}
    @Transactional public WealthObligation reopenObligation(long id){WealthObligation o=obligation(id);o.setStatus("OPEN");return obligationRepo.save(o);}
    @Transactional public void archiveObligation(long id){WealthObligation o=obligation(id);o.setActive(false);obligationRepo.save(o);}
    @Transactional public WealthGoal addGoal(GoalRequest r){WealthGoal g=new WealthGoal();g.setOwnerUserId(userDao.getUserId());apply(g,r);g.setStatus("ACTIVE");stamp(g);return goalRepo.save(g);}
    @Transactional public WealthGoal updateGoal(long id,GoalRequest r){WealthGoal g=goal(id);apply(g,r);return goalRepo.save(g);}
    @Transactional public void archiveGoal(long id){WealthGoal g=goal(id);g.setActive(false);goalRepo.save(g);}

    @Transactional public VaultDocumentView upload(long assetId,String category,LocalDate documentDate,LocalDate expiryDate,String notes,MultipartFile file) throws IOException {return upload((Long)assetId,category,documentDate,expiryDate,notes,file);}
    @Transactional public VaultDocumentView upload(Long assetId,String category,LocalDate documentDate,LocalDate expiryDate,String notes,MultipartFile file) throws IOException {
        if(assetId!=null)asset(assetId);String normalized=category==null?null:category.trim().toUpperCase();if(!VAULT_CATEGORIES.contains(normalized))throw invalid();
        if((documentDate!=null&&documentDate.isAfter(LocalDate.now()))||(expiryDate!=null&&documentDate!=null&&expiryDate.isBefore(documentDate)))throw invalid();
        byte[] bytes=file==null?null:file.getBytes();validateFile(file,bytes);VaultMalwareScanner.Result scan=malwareScanner.scan(bytes);if(scan==VaultMalwareScanner.Result.INFECTED||(scan==VaultMalwareScanner.Result.UNAVAILABLE&&malwareScanner.required()))throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);String original=Objects.requireNonNull(file.getOriginalFilename());String safe=java.nio.file.Path.of(original).getFileName().toString();if(!safe.equals(original)||safe.length()>255)throw invalid();
        String extension=safe.contains(".")?safe.substring(safe.lastIndexOf('.')).toLowerCase(Locale.ROOT):"";long owner=userDao.getUserId();String ref="wealth/"+owner+"/vault/"+UUID.randomUUID()+extension;garageService.uploadBytes(ref,bytes,file.getContentType());
        WealthVaultDocument d=new WealthVaultDocument();d.setOwnerUserId(owner);d.setAssetId(assetId);d.setCategory(normalized);d.setDisplayName(safe);d.setFileRef(ref);d.setContentType(file.getContentType());d.setFileSize(file.getSize());d.setChecksumSha256(sha256(bytes));d.setDocumentDate(documentDate);d.setExpiryDate(expiryDate);d.setNotes(notes==null?null:notes.trim());stamp(d);d=vaultRepo.save(d);return vaultView(d,null);
    }
    public List<VaultDocumentView> documents(long assetId){asset(assetId);return vaultRepo.findTop200ByAssetIdAndActiveTrueOrderByCreatedOnDesc(assetId).stream().map(d->vaultView(d,null)).toList();}
    public List<VaultDocumentView> documents(){return vaultRepo.findTop200ByOwnerUserIdAndActiveTrueOrderByCreatedOnDesc(userDao.getUserId()).stream().map(d->vaultView(d,null)).toList();}
    public VaultDocumentView document(long id){WealthVaultDocument d=vaultRepo.findByIdAndOwnerUserIdAndActiveTrue(id,userDao.getUserId()).orElseThrow(this::notFound);return vaultView(d,garageService.getPresignedUrl(d.getFileRef()));}
    @Transactional public void archiveDocument(long id){WealthVaultDocument d=vaultRepo.findByIdAndOwnerUserIdAndActiveTrue(id,userDao.getUserId()).orElseThrow(this::notFound);d.setActive(false);vaultRepo.save(d);}
    public AssetLedger ledger(long assetId){asset(assetId);return new AssetLedger(valuations(assetId),cashFlowRepo.findAllByAssetIdAndActiveTrueOrderByEntryDateDesc(assetId),liabilityRepo.findAllByAssetIdAndActiveTrueOrderByIdDesc(assetId),obligationRepo.findAllByAssetIdAndActiveTrueOrderByDueDateAsc(assetId),documents(assetId));}
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
        Dashboard calculated=WealthAnalytics.calculate(assets,flows,liabilities,obligations,goals,operatingInputs(sourceAssets),years,valueGrowth,incomeGrowth,expenseGrowth);
        AdvisorProfile advisor=advisor(sourceAssets,sourceFlows,goals);return new Dashboard(calculated.summary(),calculated.assets(),calculated.obligations(),calculated.goals(),calculated.goalProgress(),calculated.insights(),calculated.projection(),advisor);
    }

    private void apply(WealthAsset a,AssetRequest r){validateAssetRequest(r);String type=r.assetType().trim().toUpperCase();WealthAssetType configuredType=wealthAdminService.requireForAsset(type,a.getAssetType());String pricing=Optional.ofNullable(r.pricingMode()).orElse("MANUAL").trim().toUpperCase();if("MARKET".equals(pricing)&&(!configuredType.isMarketPricingAllowed()||r.instrumentSymbol()==null||r.instrumentSymbol().isBlank()||r.exchangeCode()==null||r.exchangeCode().isBlank()||r.quantity()==null))throw invalid();a.setPropertyId(r.propertyId());a.setAssetType(type);a.setName(r.name().trim());a.setReference(trim(r.reference()));a.setLocation(trim(r.location()));a.setCurrency(r.currency().toUpperCase());BigDecimal cost=r.acquisitionCost();if(cost==null&&r.quantity()!=null&&r.averageUnitCost()!=null)cost=r.quantity().multiply(r.averageUnitCost());a.setAcquisitionCost(Optional.ofNullable(cost).orElse(BigDecimal.ZERO));a.setAcquisitionDate(r.acquisitionDate());a.setCurrentValue(r.currentValue());a.setValuationDate(r.valuationDate());a.setStatus(r.status()==null?"ACTIVE":r.status().toUpperCase());a.setExchangeCode(upper(r.exchangeCode()));a.setInstrumentSymbol(upper(r.instrumentSymbol()));a.setQuantity(r.quantity());a.setAverageUnitCost(r.averageUnitCost());a.setPricingMode(pricing);if(!"MARKET".equals(pricing)){a.setMarketPrice(null);a.setQuoteProvider(null);a.setQuoteStatus(null);a.setQuoteAsOf(null);}}
    private void apply(WealthCashFlow f,CashFlowRequest r){f.setFlowType(r.flowType());f.setCategory(r.category().trim().toUpperCase());f.setAmount(r.amount());f.setEntryDate(r.entryDate());f.setDescription(trim(r.description()));f.setRecurring(r.recurring());}
    private void apply(WealthObligation o,ObligationRequest r){o.setObligationType(r.obligationType().trim().toUpperCase());o.setTitle(r.title().trim());o.setEffectiveDate(r.effectiveDate());o.setDueDate(r.dueDate());o.setExpiryDate(r.expiryDate());o.setAmount(r.amount());o.setCurrency(r.currency()==null?null:r.currency().toUpperCase());o.setReminderDays(Optional.ofNullable(r.reminderDays()).orElse(30));o.setNotes(trim(r.notes()));}
    private void apply(WealthGoal g,GoalRequest r){g.setGoalType(r.goalType().trim().toUpperCase());g.setName(r.name().trim());g.setTargetAmount(r.targetAmount());g.setCurrency(r.currency().toUpperCase());g.setTargetDate(r.targetDate());}
    private void validatePropertyLink(Long propertyId,Long currentAssetId){if(propertyId==null)return;long userId=userDao.getUserId();propertyRepo.findByIdAndStaffOrOwner(propertyId,userId).orElseThrow(this::notFound);assetRepo.findByOwnerUserIdAndPropertyIdAndActiveTrue(userId,propertyId).filter(existing->!Objects.equals(existing.getId(),currentAssetId)).ifPresent(existing->{throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);});}
    private WealthValuation recordValuation(WealthAsset a,BigDecimal amount,LocalDate date,String source,String notes){WealthValuation v=new WealthValuation();v.setAssetId(a.getId());v.setAmount(amount);v.setValuationDate(date);v.setSource(source);v.setNotes(notes);stamp(v);return valuationRepo.save(v);}
    private WealthAsset asset(long id){return assetRepo.findByIdAndOwnerUserIdAndActiveTrue(id,userDao.getUserId()).orElseThrow(this::notFound);}
    private WealthCashFlow cashFlow(long id){return cashFlowRepo.findByIdAndAssetIdInAndActiveTrue(id,assetIds()).orElseThrow(this::notFound);}
    private WealthLiability liability(long id){return liabilityRepo.findByIdAndAssetIdInAndActiveTrue(id,assetIds()).orElseThrow(this::notFound);}
    private WealthObligation obligation(long id){return obligationRepo.findByIdAndAssetIdInAndActiveTrue(id,assetIds()).orElseThrow(this::notFound);}
    private WealthGoal goal(long id){return goalRepo.findByIdAndOwnerUserIdAndActiveTrue(id,userDao.getUserId()).orElseThrow(this::notFound);}
    private List<WealthAsset> ownedAssets(){return assetRepo.findAllByOwnerUserIdAndActiveTrueOrderByName(userDao.getUserId());}
    private List<Long> assetIds(){return ownedAssets().stream().map(WealthAsset::getId).toList();}
    private <T extends org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity> void stamp(T e){e.setCreatedBy(userDao.getUserId());e.setActive(true);}
    private PMSCustomException notFound(){return new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND);}
    private PMSCustomException invalid(){return new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);}
    private void validateFile(MultipartFile file,byte[] bytes){if(file==null||file.isEmpty()||file.getSize()>MAX_VAULT_BYTES||!ALLOWED_TYPES.contains(file.getContentType())||!signatureMatches(file.getContentType(),bytes))throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);}
    private VaultDocumentView vaultView(WealthVaultDocument document,String downloadUrl){return new VaultDocumentView(new VaultDocumentMetadata(document),downloadUrl);}
    private boolean signatureMatches(String type,byte[] b){if(b==null||b.length<4)return false;return switch(type){case "application/pdf"->b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F';case "image/png"->(b[0]&255)==137&&b[1]=='P'&&b[2]=='N'&&b[3]=='G';case "image/jpeg"->(b[0]&255)==255&&(b[1]&255)==216;case "application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"->b[0]=='P'&&b[1]=='K';case "application/msword","application/vnd.ms-excel"->(b[0]&255)==208&&(b[1]&255)==207&&(b[2]&255)==17&&(b[3]&255)==224;default->false;};}
    private void validateAssetRequest(AssetRequest r){if(r.valuationDate().isAfter(LocalDate.now())||(r.acquisitionDate()!=null&&r.acquisitionDate().isAfter(LocalDate.now())))throw invalid();}
    private void validateLiability(BigDecimal original,BigDecimal outstanding,LocalDate start,LocalDate maturity){if(outstanding.compareTo(original)>0||(start!=null&&maturity!=null&&maturity.isBefore(start)))throw invalid();}
    private void validateObligation(ObligationRequest r){if(r.dueDate()==null&&r.expiryDate()==null)throw invalid();if(r.effectiveDate()!=null&&r.dueDate()!=null&&r.dueDate().isBefore(r.effectiveDate()))throw invalid();if(r.effectiveDate()!=null&&r.expiryDate()!=null&&r.expiryDate().isBefore(r.effectiveDate()))throw invalid();}
    private String normalizeCategory(String value){String normalized=Optional.ofNullable(value).orElse("").trim().toUpperCase().replaceAll("[^A-Z0-9_]+","_");if(normalized.isBlank()||normalized.length()>50)throw invalid();return normalized;}
    private String trim(String value){return value==null?null:value.trim();}
    private String upper(String value){return value==null||value.isBlank()?null:value.trim().toUpperCase(Locale.ROOT);}
    private AdvisorProfile advisor(List<WealthAsset> assets,List<WealthCashFlow> flows,List<WealthGoal> goals){long owner=userDao.getUserId();boolean will=vaultRepo.existsByOwnerUserIdAndCategoryAndActiveTrue(owner,"WILL"),trust=vaultRepo.existsByOwnerUserIdAndCategoryAndActiveTrue(owner,"TRUST_DEED");int stale=(int)assets.stream().filter(a->a.getValuationDate()==null||ChronoUnit.DAYS.between(a.getValuationDate(),LocalDate.now())>("MARKET".equals(a.getPricingMode())?7:365)).count();int score=assets.isEmpty()?0:30;score+=assets.stream().allMatch(a->a.getAcquisitionCost()!=null)?15:0;score+=stale==0&&!assets.isEmpty()?20:0;score+=flows.isEmpty()?0:15;score+=goals.isEmpty()?0:10;score+=will?5:0;score+=trust?5:0;List<String> actions=new ArrayList<>();if(assets.isEmpty())actions.add("Add your first asset to establish a net-worth baseline.");if(stale>0)actions.add("Refresh "+stale+" stale asset valuation"+(stale==1?".":"s."));if(flows.isEmpty()&&!assets.isEmpty())actions.add("Record income and expenses to understand portfolio cash flow.");if(goals.isEmpty())actions.add("Set a wealth goal so progress can be measured.");if(!will)actions.add("Store your current will or note that estate planning is pending.");if(!trust)actions.add("Add trust documents if a trust forms part of your estate plan.");if(actions.isEmpty())actions.add("Your records are current; review beneficiaries and valuations periodically.");String headline=score>=85?"Your wealth records are in strong shape":score>=55?"Your wealth picture is taking shape":"Build a clearer picture of your wealth";return new AdvisorProfile(Math.min(score,100),headline,actions,(int)assets.stream().filter(a->"MARKET".equals(a.getPricingMode())).count(),stale,will,trust);}
    private String sha256(byte[] bytes){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private BigDecimal base(BigDecimal amount,String currency){return currencyConversionService.convert(Optional.ofNullable(amount).orElse(BigDecimal.ZERO),currency,baseCurrency);}
    private WealthAsset baseAsset(WealthAsset source){WealthAsset a=new WealthAsset();a.setId(source.getId());a.setName(source.getName());a.setAssetType(source.getAssetType());a.setCurrency(baseCurrency);a.setCurrentValue(base(source.getCurrentValue(),source.getCurrency()));a.setAcquisitionCost(base(source.getAcquisitionCost(),source.getCurrency()));a.setValuationDate(source.getValuationDate());a.setActive(true);return a;}
    private WealthCashFlow baseFlow(WealthCashFlow source,String currency){WealthCashFlow f=new WealthCashFlow();f.setAssetId(source.getAssetId());f.setFlowType(source.getFlowType());f.setAmount(base(source.getAmount(),currency));f.setActive(true);return f;}
    private WealthLiability baseLiability(WealthLiability source){WealthLiability l=new WealthLiability();l.setAssetId(source.getAssetId());l.setOutstandingPrincipal(base(source.getOutstandingPrincipal(),source.getCurrency()));l.setMonthlyPayment(base(source.getMonthlyPayment(),source.getCurrency()));l.setActive(true);return l;}
    private WealthGoal baseGoal(WealthGoal source){WealthGoal g=new WealthGoal();g.setId(source.getId());g.setName(source.getName());g.setGoalType(source.getGoalType());g.setTargetAmount(base(source.getTargetAmount(),source.getCurrency()));g.setCurrency(baseCurrency);g.setTargetDate(source.getTargetDate());g.setStatus(source.getStatus());g.setActive(true);return g;}
    private Map<Long,OperatingInput> operatingInputs(List<WealthAsset> assets){
        List<Long> propertyIds=assets.stream().map(WealthAsset::getPropertyId).filter(Objects::nonNull).distinct().toList();if(propertyIds.isEmpty())return Map.of();
        Map<Long,List<Unit>> units=unitRepo.findAllByPropertyIdInAndActiveTrue(propertyIds).stream().collect(java.util.stream.Collectors.groupingBy(Unit::getPropertyId));
        Map<Long,List<PMSInvoice>> invoices=invoiceRepo.findAllByPropertyIdInAndActiveTrueAndPaidFalse(propertyIds).stream().collect(java.util.stream.Collectors.groupingBy(PMSInvoice::getPropertyId));
        Map<Long,OperatingInput> result=new HashMap<>();for(WealthAsset asset:assets){if(asset.getPropertyId()==null)continue;List<Unit> propertyUnits=units.getOrDefault(asset.getPropertyId(),List.of());BigDecimal arrears=invoices.getOrDefault(asset.getPropertyId(),List.of()).stream().map(i->base(BigDecimal.valueOf(i.getPendingAmount()>0?i.getPendingAmount():i.getAmount()),i.getCurrency())).reduce(BigDecimal.ZERO,BigDecimal::add);result.put(asset.getId(),new OperatingInput(propertyUnits.size(),(int)propertyUnits.stream().filter(Unit::isOccupied).count(),arrears));}return result;
    }
}
