package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="pms_wealth_goal",indexes=@Index(name="idx_wealth_goal_owner",columnList="ownerUserId,status"))
@Getter @Setter @NoArgsConstructor
public class WealthGoal extends BaseCreatorEntity {
    @Column(nullable=false) private long ownerUserId;
    @Column(nullable=false,length=40) private String goalType;
    @Column(nullable=false,length=160) private String name;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal targetAmount;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false) private LocalDate targetDate;
    @Column(nullable=false,length=30) private String status;
}
