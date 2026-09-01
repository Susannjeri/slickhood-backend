package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "pms_help_rate_limit", uniqueConstraints =
        @UniqueConstraint(name = "uk_help_rate_subject_window", columnNames = {"subjectHash", "windowStart"}))
@Getter @Setter
public class HelpRateLimit extends BaseActiveEntity {
    @Column(name = "subject_hash", nullable = false, length = 64)
    private String subjectHash;
    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;
    @Column(name = "request_count", nullable = false)
    private int requestCount;
}
