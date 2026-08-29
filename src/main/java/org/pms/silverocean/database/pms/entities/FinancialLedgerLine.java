package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;
import java.math.BigDecimal;

@Entity
@Table(name="pms_financial_ledger_line",uniqueConstraints=@UniqueConstraint(name="uk_financial_ledger_line_number",columnNames={"journalId","lineNumber"}),
        indexes={@Index(name="idx_financial_ledger_user_date",columnList="userId,createdOn"),@Index(name="idx_financial_ledger_property_date",columnList="propertyId,createdOn")})
@Getter @NoArgsConstructor
public class FinancialLedgerLine extends BaseIDEntity {
    @Column(nullable=false,updatable=false) private Long journalId;
    @Column(nullable=false,updatable=false) private int lineNumber;
    @Column(nullable=false,updatable=false,length=50) private String accountCode;
    @Column(updatable=false) private Long userId;
    @Column(updatable=false) private Long propertyId;
    @Column(updatable=false) private Long unitId;
    @Column(nullable=false,updatable=false,length=12) private String currency;
    @Column(nullable=false,updatable=false,precision=19,scale=2) private BigDecimal debit;
    @Column(nullable=false,updatable=false,precision=19,scale=2) private BigDecimal credit;
    @Column(updatable=false) private String description;
    public FinancialLedgerLine(Long journalId,int lineNumber,String accountCode,Long userId,Long propertyId,Long unitId,String currency,BigDecimal debit,BigDecimal credit,String description){
        this.journalId=journalId;this.lineNumber=lineNumber;this.accountCode=accountCode;this.userId=userId;this.propertyId=propertyId;this.unitId=unitId;this.currency=currency;this.debit=debit;this.credit=credit;this.description=description;
    }
}
