package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.ZonedDateTime;

@Table(name = "pms_user_otp")
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserOTP extends BaseCreatorEntity implements Auditable {
    private String contact;
    private String channel;
    @Lob
    private byte[] otp;
    private int attempts = 0;
    private ZonedDateTime otpExpiryTime;
    private boolean verified;



    @Override
    public String toAuditJSON() {
        return "{" +
                "\"contact\":\"" + contact + "\"," +
                "\"channel\":\"" + channel + "\"," +
                "\"otp\":\"******\"," +
                "\"otpAttempts\":" + attempts + "," +
                "\"otpExpiryTime\":" + otpExpiryTime + "," +
                "\"verified\":" + verified + "," +
                "\"active\":" + isActive() + "," +
                "\"createdOn\":\"" + getCreatedOn() + "\"," +
                "\"createdBy\":" + getCreatedBy() +
                "}";
    }

}
