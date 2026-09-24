package org.pms.silverocean.service.maintenance;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.MaintenanceWorkOrderRepo;
import org.pms.silverocean.database.pms.MaintenanceAttachmentRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.MaintenanceWorkOrder;
import org.pms.silverocean.database.pms.entities.MaintenanceAttachment;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.filestorage.UploadMalwarePolicy;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class MaintenanceService {
 private static final long MAX_ATTACHMENT_BYTES=10L*1024*1024;
 private static final Set<String> ATTACHMENT_TYPES=Set.of("application/pdf","image/jpeg","image/png");
 private final MaintenanceWorkOrderRepo orders;private final MaintenanceAttachmentRepo attachments;private final UnitRepo units;private final UserDao users;private final GarageService garage;private final UploadMalwarePolicy malwarePolicy;
 @Transactional public MaintenanceModels.View create(MaintenanceModels.Create request){long userId=users.getUserId();units.findByIdAndStaffOrOwnerOrTenant(request.unitId(),userId).orElseThrow(()->new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS));Unit unit=units.findById(request.unitId()).orElseThrow(()->new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));MaintenanceWorkOrder w=new MaintenanceWorkOrder();w.setWorkOrderNumber("WO-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());w.setPropertyId(unit.getPropertyId());w.setUnitId(unit.getId());w.setRequestedByUserId(userId);w.setCreatedBy(userId);w.setTitle(request.title().trim());w.setDescription(request.description().trim());w.setCategory(request.category().name());w.setPriority(request.priority().name());w.setStatus(MaintenanceModels.Status.OPEN.name());w.setCurrency(unit.getCurrency());w.setActive(true);return new MaintenanceModels.View(orders.save(w));}
 @Transactional(readOnly=true) public List<MaintenanceModels.View> list(long unitId){return orders.findAccessibleByUnit(unitId,users.getUserId()).stream().map(MaintenanceModels.View::new).toList();}
 @Transactional public MaintenanceModels.AttachmentView uploadAttachment(long id,MaintenanceModels.AttachmentCategory category,MultipartFile file)throws IOException{long userId=users.getUserId();MaintenanceWorkOrder w=orders.findAccessible(id,userId).orElseThrow(()->new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));boolean staff=units.findByIdAndStaffOrOwner(w.getUnitId(),userId).isPresent()||orders.isAssignedProvider(id,userId);if(!staff&&!Set.of(MaintenanceModels.AttachmentCategory.REQUEST_EVIDENCE,MaintenanceModels.AttachmentCategory.PROGRESS_EVIDENCE).contains(category))throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);if(Set.of(MaintenanceModels.Status.COMPLETED.name(),MaintenanceModels.Status.CANCELLED.name()).contains(w.getStatus())&&category!=MaintenanceModels.AttachmentCategory.COMPLETION_EVIDENCE)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);if(attachments.countByWorkOrderIdAndActiveTrue(id)>=20)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);byte[] bytes=validate(file);String type=file.getContentType().toLowerCase(Locale.ROOT);String name=safeName(file.getOriginalFilename());String extension="application/pdf".equals(type)?".pdf":"image/png".equals(type)?".png":".jpg";String ref="maintenance/"+w.getPropertyId()+"/"+w.getId()+"/"+UUID.randomUUID()+extension;garage.uploadBytes(ref,bytes,type);MaintenanceAttachment a=new MaintenanceAttachment();a.setWorkOrderId(id);a.setCategory(category.name());a.setDisplayName(name);a.setFileRef(ref);a.setContentType(type);a.setFileSize(bytes.length);a.setChecksumSha256(hash(bytes));a.setUploadedByUserId(userId);a.setCreatedBy(userId);a.setActive(true);return view(attachments.save(a));}
 @Transactional(readOnly=true) public List<MaintenanceModels.AttachmentView> attachments(long id){orders.findAccessible(id,users.getUserId()).orElseThrow(()->new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));return attachments.findAllByWorkOrderIdAndActiveTrueOrderByCreatedOnAsc(id).stream().map(this::view).toList();}
 @Transactional public MaintenanceModels.View update(long id,MaintenanceModels.Update request){long userId=users.getUserId();MaintenanceWorkOrder w=orders.findAccessible(id,userId).orElseThrow(()->new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));boolean staff=units.findByIdAndStaffOrOwner(w.getUnitId(),userId).isPresent();if(!staff&&(request.status()!=MaintenanceModels.Status.CANCELLED||w.getRequestedByUserId()!=userId))throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);MaintenanceModels.Status current=MaintenanceModels.Status.valueOf(w.getStatus());if(!allowed(current,request.status()))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);w.setStatus(request.status().name());w.setAssignedProviderServiceId(request.assignedProviderServiceId());w.setScheduledAt(request.scheduledAt());w.setEstimatedCost(request.estimatedCost());w.setActualCost(request.actualCost());if(StringUtils.isNotBlank(request.currency()))w.setCurrency(request.currency().trim().toUpperCase());w.setResolutionNotes(StringUtils.trimToNull(request.resolutionNotes()));if(request.status()==MaintenanceModels.Status.COMPLETED)w.setCompletedAt(ZonedDateTime.now(PMSUtils.getZoneId()));return new MaintenanceModels.View(orders.save(w));}
 private boolean allowed(MaintenanceModels.Status from,MaintenanceModels.Status to){if(from==to)return true;return switch(from){case OPEN->Set.of(MaintenanceModels.Status.ACKNOWLEDGED,MaintenanceModels.Status.CANCELLED).contains(to);case ACKNOWLEDGED->Set.of(MaintenanceModels.Status.IN_PROGRESS,MaintenanceModels.Status.CANCELLED).contains(to);case IN_PROGRESS->Set.of(MaintenanceModels.Status.COMPLETED,MaintenanceModels.Status.CANCELLED).contains(to);case COMPLETED,CANCELLED->false;};}
 private MaintenanceModels.AttachmentView view(MaintenanceAttachment a){return new MaintenanceModels.AttachmentView(a.getId(),a.getWorkOrderId(),a.getCategory(),a.getDisplayName(),a.getContentType(),a.getFileSize(),a.getChecksumSha256(),a.getUploadedByUserId(),a.getCreatedOn(),garage.getPresignedUrlForStoredObject(a.getFileRef()));}
 private byte[] validate(MultipartFile file)throws IOException{if(file==null||file.isEmpty()||file.getSize()>MAX_ATTACHMENT_BYTES)throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);String type=StringUtils.defaultString(file.getContentType()).toLowerCase(Locale.ROOT);if(!ATTACHMENT_TYPES.contains(type))throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);byte[] bytes=file.getBytes();boolean valid="application/pdf".equals(type)?bytes.length>4&&bytes[0]=='%'&&bytes[1]=='P'&&bytes[2]=='D'&&bytes[3]=='F':"image/png".equals(type)?bytes.length>8&&(bytes[0]&255)==0x89&&bytes[1]=='P'&&bytes[2]=='N'&&bytes[3]=='G':bytes.length>3&&(bytes[0]&255)==0xff&&(bytes[1]&255)==0xd8;if(!valid)throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);malwarePolicy.requireSafe(bytes);return bytes;}
 private String safeName(String supplied){String name=StringUtils.defaultIfBlank(supplied,"maintenance-evidence").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[^A-Za-z0-9._ -]","_");return name.length()>255?name.substring(name.length()-255):name;}
 private String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception exception){throw new IllegalStateException(exception);}}
}
