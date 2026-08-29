package org.pms.silverocean.service.communityfund;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.database.pms.entities.CommunityFund;
import org.pms.silverocean.database.pms.entities.CommunityFundContribution;
import org.pms.silverocean.database.pms.entities.CommunityFundExpenditure;
import org.pms.silverocean.database.pms.entities.CommunityFundTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CommunityFundModels {
    private CommunityFundModels() {}

    public record CreateFundRequest(
            @NotNull Long propertyId,
            @NotBlank @Size(max=180) String name,
            @NotBlank String fundType,
            @NotBlank String contributorScope,
            @NotBlank @Size(max=2000) String description,
            @NotBlank @Size(min=3,max=3) String currency,
            @NotNull @DecimalMin("0.00") BigDecimal targetAmount,
            @NotNull @DecimalMin("0.00") BigDecimal defaultContribution,
            @NotNull @FutureOrPresent LocalDate opensOn,
            @NotNull LocalDate dueDate,
            LocalDate closesOn,
            @NotNull Long paymentAccountId,
            boolean dualApprovalRequired) {}

    public record PledgeRequest(@NotNull @DecimalMin("1.00") BigDecimal amount) {}

    public record ExpenditureRequest(
            @NotBlank @Size(max=1000) String purpose,
            @NotBlank @Size(max=40) String category,
            @NotNull @DecimalMin("1.00") BigDecimal amount,
            @NotBlank @Size(max=40) String beneficiaryType,
            Long beneficiaryUserId,
            @NotBlank @Size(max=200) String beneficiaryName,
            @Size(max=120) String beneficiaryReference,
            @Size(max=800) String evidenceFileRef) {}

    public record RejectRequest(@NotBlank @Size(max=1000) String reason) {}
    public record DisbursementRequest(@NotBlank @Size(max=120) String paymentReference,
                                      @Size(max=800) String evidenceFileRef) {}

    public record PaymentAccountView(Long id,String name,String channel,boolean active,boolean verified) {}

    public record FundDashboard(CommunityFund fund,PaymentAccountView paymentAccount,
                                BigDecimal assessed,BigDecimal collected,BigDecimal committed,
                                BigDecimal spent,BigDecimal available,int contributorCount,int paidContributorCount,
                                List<CommunityFundContribution> myContributions,
                                List<CommunityFundContribution> contributions,
                                List<CommunityFundExpenditure> expenditures,
                                List<CommunityFundTransaction> transactions,boolean managerView) {}
}
