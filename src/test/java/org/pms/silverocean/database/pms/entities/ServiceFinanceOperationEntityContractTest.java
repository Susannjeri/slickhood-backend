package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceFinanceOperationEntityContractTest {
    @Test
    void providerReferenceUniquenessIsScopedByProviderChannelAndType() {
        Table table = ServiceFinanceOperation.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("pms_sp_finance_operation");
        assertThat(table.uniqueConstraints()).anySatisfy(unique -> {
            assertThat(unique.name()).isEqualTo("uk_sp_finance_provider_channel_type_ref");
            assertThat(Set.of(unique.columnNames())).isEqualTo(Set.of(
                    "provider_user_id", "payment_channel", "operation_type", "provider_reference"));
        });
    }

    @Test
    void operationEvidenceFieldsAreImmutable() {
        assertThat(Arrays.stream(ServiceFinanceOperation.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class))
                .filter(field -> !field.getName().equals("initiatedBy"))
                .allMatch(field -> !field.getAnnotation(Column.class).updatable())).isTrue();

        ServiceFinanceOperation operation = new ServiceFinanceOperation(4L, 9L, "MPESA",
                "REFUND", "RF-4", new BigDecimal("20.00"), "KES", 1L);
        assertThat(operation.getOperationStatus()).isEqualTo("CONFIRMED");
    }
}
