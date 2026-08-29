package org.pms.silverocean.service.sales;

import jakarta.transaction.Transactional;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.SaleTransactionRepo;
import org.pms.silverocean.database.pms.SaleMilestoneRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.SaleTransaction;
import org.pms.silverocean.database.pms.entities.SaleMilestone;
import org.pms.silverocean.common.PMSUtils;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.estate.EstateService;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Service
public class SalesService {
    private final SaleTransactionRepo saleRepo; private final PropertyRepo propertyRepo;
    private final UnitRepo unitRepo; private final UserDao userDao; private final EstateService estateService;private final SaleMilestoneRepo milestoneRepo;
    private static final Map<SaleStatus, EnumSet<SaleStatus>> TRANSITIONS = Map.of(
            SaleStatus.LEAD, EnumSet.of(SaleStatus.VIEWING, SaleStatus.OFFERED, SaleStatus.CANCELLED),
            SaleStatus.VIEWING, EnumSet.of(SaleStatus.OFFERED, SaleStatus.CANCELLED),
            SaleStatus.OFFERED, EnumSet.of(SaleStatus.RESERVED, SaleStatus.CANCELLED),
            SaleStatus.RESERVED, EnumSet.of(SaleStatus.DUE_DILIGENCE, SaleStatus.CANCELLED),
            SaleStatus.DUE_DILIGENCE, EnumSet.of(SaleStatus.AGREEMENT, SaleStatus.CANCELLED),
            SaleStatus.AGREEMENT, EnumSet.of(SaleStatus.COMPLETION, SaleStatus.CANCELLED),
            SaleStatus.COMPLETION, EnumSet.of(SaleStatus.COMPLETED, SaleStatus.CANCELLED));

    public SalesService(SaleTransactionRepo saleRepo, PropertyRepo propertyRepo, UnitRepo unitRepo, UserDao userDao, EstateService estateService,SaleMilestoneRepo milestoneRepo) {
        this.saleRepo=saleRepo; this.propertyRepo=propertyRepo; this.unitRepo=unitRepo; this.userDao=userDao; this.estateService=estateService;this.milestoneRepo=milestoneRepo;
    }

    @Transactional
    public SaleTransaction create(CreateSaleRequest request) {
        long userId=userDao.getUserId(); requireSaleProperty(request.propertyId(), userId);
        userDao.findById(request.buyerUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        if(request.unitId()!=null) unitRepo.findById(request.unitId()).filter(u->u.isActive()&&u.getPropertyId()==request.propertyId())
                .orElseThrow(()->new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        SaleTransaction sale=new SaleTransaction(); sale.setPropertyId(request.propertyId()); sale.setUnitId(request.unitId());
        sale.setSalesAgentUserId(userId); sale.setBuyerUserId(request.buyerUserId()); sale.setStatus(SaleStatus.LEAD);
        sale.setAskingPrice(request.askingPrice()); sale.setCurrency(request.currency()); sale.setNotes(request.notes());
        sale.setCreatedBy(userId); sale.setActive(true); return saleRepo.save(sale);
    }

    public List<SaleTransaction> list() {
        long userId=userDao.getUserId(); return switch(userDao.getActiveRole()) {
            case BUYER -> saleRepo.findAllByBuyerUserIdAndActiveTrueOrderByCreatedOnDesc(userId);
            case SALES_AGENT -> saleRepo.findAllBySalesAgentUserIdAndActiveTrueOrderByCreatedOnDesc(userId);
            case LANDLORD -> saleRepo.findAllByPropertyOwner(userId);
            case SUPER_ADMIN -> saleRepo.findAll();
            default -> List.of();
        };
    }

    @Transactional
    public SaleTransaction update(long id, UpdateSaleRequest request) {
        long userId=userDao.getUserId();
        SaleTransaction sale=saleRepo.findByIdAndSalesAgentUserIdAndActiveTrue(id,userId)
                .orElseThrow(()->new PMSCustomException(ResponseCode.SALE_NOT_FOUND));
        requireMilestones(sale,request.status());transition(sale,request.status());
        if(request.offerAmount()!=null) sale.setOfferAmount(request.offerAmount());
        if(request.notes()!=null) sale.setNotes(request.notes());
        if(request.status()==SaleStatus.COMPLETED){ sale.setCompletedAt(LocalDateTime.now()); saleRepo.save(sale); estateService.transferFromSale(sale.getPropertyId(),sale.getUnitId(),sale.getBuyerUserId(),sale.getId()); }
        return saleRepo.save(sale);
    }

    @Transactional public SaleMilestone addMilestone(long saleId,SaleMilestoneModels.Create request){long userId=userDao.getUserId();SaleTransaction sale=saleRepo.findByIdAndSalesAgentUserIdAndActiveTrue(saleId,userId).orElseThrow(()->new PMSCustomException(ResponseCode.SALE_NOT_FOUND));if(request.type()==SaleMilestoneModels.Type.ESCROW_FUNDED&&request.status()==SaleMilestoneModels.Status.COMPLETED&&(request.amount()==null||StringUtils.isBlank(request.externalReference())))throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);if(request.status()==SaleMilestoneModels.Status.COMPLETED&&request.type()!=SaleMilestoneModels.Type.ESCROW_FUNDED&&request.evidenceDocumentId()==null)throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);SaleMilestone m=new SaleMilestone(saleId,request.type().name(),request.status().name(),request.amount(),sale.getCurrency(),StringUtils.left(StringUtils.trimToNull(request.externalReference()),120),request.evidenceDocumentId(),StringUtils.left(StringUtils.trimToNull(request.notes()),1000),java.time.ZonedDateTime.now(PMSUtils.getZoneId()),userId);return milestoneRepo.save(m);}
    public List<SaleMilestone> milestones(long saleId){long userId=userDao.getUserId();boolean visible=saleRepo.findByIdAndSalesAgentUserIdAndActiveTrue(saleId,userId).isPresent()||saleRepo.findByIdAndBuyerUserIdAndActiveTrue(saleId,userId).isPresent()||userDao.hasRole(PMSRole.SUPER_ADMIN);if(!visible)throw new PMSCustomException(ResponseCode.SALE_NOT_FOUND);return milestoneRepo.findAllBySaleIdOrderByOccurredAtAsc(saleId);}
    private void requireMilestones(SaleTransaction sale,SaleStatus next){if(next==SaleStatus.AGREEMENT)require(sale,"DUE_DILIGENCE_CHECK");if(next==SaleStatus.COMPLETION){require(sale,"AGREEMENT_SIGNED");require(sale,"ESCROW_FUNDED");}if(next==SaleStatus.COMPLETED){require(sale,"TRANSFER_REGISTERED");require(sale,"HANDOVER_COMPLETED");}}
    private void require(SaleTransaction sale,String type){if(!milestoneRepo.existsBySaleIdAndMilestoneTypeAndStatus(sale.getId(),type,"COMPLETED"))throw new PMSCustomException(ResponseCode.SALE_INVALID_TRANSITION);}

    @Transactional
    public SaleTransaction acceptOffer(long id) {
        SaleTransaction sale=saleRepo.findByIdAndBuyerUserIdAndActiveTrue(id,userDao.getUserId())
                .orElseThrow(()->new PMSCustomException(ResponseCode.SALE_NOT_FOUND));
        transition(sale,SaleStatus.RESERVED); sale.setOfferAcceptedAt(LocalDateTime.now()); return saleRepo.save(sale);
    }

    private void transition(SaleTransaction sale, SaleStatus next) {
        if(!TRANSITIONS.getOrDefault(sale.getStatus(),EnumSet.noneOf(SaleStatus.class)).contains(next))
            throw new PMSCustomException(ResponseCode.SALE_INVALID_TRANSITION);
        sale.setStatus(next);
    }

    private Property requireSaleProperty(long propertyId,long userId){ PMSRole role=userDao.getActiveRole();
        return (role==PMSRole.LANDLORD?propertyRepo.findByIdAndCreatedByAndActiveTrue(propertyId,userId)
                :propertyRepo.findByIdAndManagerRole(propertyId,userId,role.name())).orElseThrow(()->new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND)); }
}
