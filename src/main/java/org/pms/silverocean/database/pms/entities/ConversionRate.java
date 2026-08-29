package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table(name = "pms_conversion_rates", indexes = {
        @Index(name ="idx_conversion_rate_currency", columnList = "currency"),
})
@Entity
@Getter
@Setter
public class ConversionRate extends BaseIDEntity {
    private String currency;
    private BigDecimal rate;
    @LastModifiedDate
    private LocalDateTime lastModifiedDate;
    public ConversionRate() {

    }
    public ConversionRate(String currency) {
        this.currency = currency;
    }


}
