package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Table(name="pms_login_per_ip_logs", indexes = @Index(columnList = "ipaddress, username"))
@Entity
@Getter @Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class LoginPerIPLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ipaddress;
    private String username;
    private int attemptsCount;
    private boolean blocked;
    private Date lastLoginAttempt;
    private String country;
    private String city;
}
