package org.pms.silverocean.service.property;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.CustomPropertyTypeRepo;
import org.pms.silverocean.database.pms.entities.CustomPropertyType;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class CustomPropertyTypeService {
    private final CustomPropertyTypeRepo repo;private final UserDao users;private final AuditLogService audit;
    public record Create(@NotBlank @Pattern(regexp="CUSTOM_[A-Z0-9_]{1,48}") String code,@NotBlank @Size(max=160) String name,@Size(max=1000) String description,@NotNull PMSPropertyCategory category,@NotEmpty Set<@NotNull PMSUnitTypes> unitTypes,@NotBlank @Size(max=1000) String reason){}
    public record Edit(@NotBlank @Size(max=160) String name,@Size(max=1000) String description,boolean active,@NotEmpty Set<@NotNull PMSUnitTypes> unitTypes,@Min(0) long version,@NotBlank @Size(max=1000) String reason){}
    public List<CustomPropertyType> all(){return repo.findAllByOrderByNameAsc();}
    public CustomPropertyType get(String code){return repo.findByCode(code).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_FIELD_DATA));}
    @Transactional public CustomPropertyType create(Create r){admin();if(repo.existsByCode(r.code()))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);var t=new CustomPropertyType();t.setCode(r.code());t.setActive(true);t.setCreatedBy(users.getUserId());t.setName(r.name().trim());t.setDescription(r.description());t.setCategory(r.category());t.setUnitTypes(new HashSet<>(r.unitTypes()));repo.saveAndFlush(t);audit.createAuditLog(t,"PROPERTY_TYPE_CREATE",r.reason(),true);return t;}
    @Transactional public CustomPropertyType edit(String code,Edit r){admin();var t=repo.findForUpdate(code).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_FIELD_DATA));if(t.getVersion()!=r.version())throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);String before=t.toAuditJSON();t.setName(r.name().trim());t.setDescription(r.description());t.setActive(r.active());t.setUnitTypes(new HashSet<>(r.unitTypes()));repo.saveAndFlush(t);audit.createAuditLog(t,"PROPERTY_TYPE_UPDATE",r.reason()+"; before="+before,true);return t;}
    @Transactional public void mappings(String code,Set<PMSUnitTypes> types,Set<PMSUnitTypes> baseline){admin();var t=repo.findForUpdate(code).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_FIELD_DATA));if(!t.isActive()||types==null||types.isEmpty()||baseline==null||!t.getUnitTypes().equals(baseline))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);String before=t.toAuditJSON();t.setUnitTypes(new HashSet<>(types));repo.saveAndFlush(t);audit.createAuditLog(t,"PROPERTY_UNIT_TYPE_UPDATE","before="+before,true);}
    private void admin(){if(!users.hasRole(PMSRole.SUPER_ADMIN))throw new PMSCustomException(ResponseCode.INVALID_ROLE);}
}
