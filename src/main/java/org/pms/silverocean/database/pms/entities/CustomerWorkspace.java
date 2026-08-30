package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.teamaccess.TeamBusinessArea;

@Entity @Table(name="pms_customer_workspace",uniqueConstraints=@UniqueConstraint(name="uk_workspace_owner_area",columnNames={"ownerUserId","businessArea"})) @Getter @Setter
public class CustomerWorkspace extends BaseCreatorEntity implements Auditable {
    private long ownerUserId;
    @Enumerated(EnumType.STRING) @Column(length=40,nullable=false) private TeamBusinessArea businessArea;
    @Column(length=160,nullable=false) private String name;
    @Override public String toAuditJSON(){return "{\"id\":"+getId()+",\"ownerUserId\":"+ownerUserId+",\"businessArea\":\""+businessArea+"\",\"active\":"+isActive()+"}";}
}
