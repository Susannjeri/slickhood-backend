package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.InsurancePaymentConfiguration;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsurancePaymentConfigurationRepo extends JpaRepository<InsurancePaymentConfiguration, Long> {
    List<InsurancePaymentConfiguration> findByCompanyIdOrderByPaymentChannelAscVersionDesc(Long companyId);
    List<InsurancePaymentConfiguration> findByCompanyIdAndActiveTrueOrderByPaymentChannelAsc(Long companyId);
    List<InsurancePaymentConfiguration> findByCompanyIdAndPaymentChannelAndActiveTrue(Long companyId, PaymentChannel channel);
    long countByCompanyIdAndPaymentChannel(Long companyId, PaymentChannel channel);
}
