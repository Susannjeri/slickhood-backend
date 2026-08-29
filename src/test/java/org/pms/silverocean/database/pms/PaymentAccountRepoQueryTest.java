package org.pms.silverocean.database.pms;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAccountRepoQueryTest {

    @Test
    void propertyAccountQueryScopesBothThePropertyAndCurrentTenant() {
        Method method = Arrays.stream(PaymentAccountRepo.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("listAccountsByProperty"))
                .findFirst()
                .orElseThrow();

        String query = method.getAnnotation(Query.class).value().replaceAll("\\s+", " ");

        assertThat(query)
                .contains("pa2.propertyId=:propertyId")
                .contains("pa2.active")
                .contains("u.propertyId=:propertyId")
                .contains("ut.userId=:userId")
                .contains("ut.active");
    }
}
