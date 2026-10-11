package org.pms.silverocean.database.pms;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.ServiceFinanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceFinanceOperationRepoContractTest {
    @Test
    void repositoryExposesTheGlobalReferenceLookupAndBookingHistory() throws Exception {
        assertThat(JpaRepository.class).isAssignableFrom(ServiceFinanceOperationRepo.class);

        Method referenceLookup = ServiceFinanceOperationRepo.class.getMethod(
                "findByProviderUserIdAndPaymentChannelAndOperationTypeAndProviderReference",
                long.class, String.class, String.class, String.class);
        Method bookingHistory = ServiceFinanceOperationRepo.class.getMethod(
                "findAllByBookingIdOrderByCreatedOnAsc", long.class);

        assertThat(referenceLookup.getReturnType()).isEqualTo(Optional.class);
        assertThat(bookingHistory.getReturnType()).isEqualTo(List.class);
        assertThat(ServiceFinanceOperation.class).isNotNull();
    }
}
