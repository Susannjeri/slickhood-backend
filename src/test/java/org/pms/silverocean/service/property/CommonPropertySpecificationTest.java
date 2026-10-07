package org.pms.silverocean.service.property;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommonPropertySpecificationTest {

    @ParameterizedTest
    @EnumSource(value = PMSRole.class, names = {"LANDLORD", "ESTATE_MANAGER", "SALES_AGENT"})
    void ownerRolesUseTheCreatorPredicateWithoutDistinctOrAuthorizationSubqueries(PMSRole role) {
        @SuppressWarnings("unchecked")
        Root<Unit> root = mock(Root.class);
        @SuppressWarnings("unchecked")
        CriteriaQuery<Object> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        Path<Object> createdBy = mock(Path.class);
        Predicate ownerPredicate = mock(Predicate.class);
        when(root.<Object>get("createdBy")).thenReturn(createdBy);
        when(criteriaBuilder.equal(createdBy, 7L)).thenReturn(ownerPredicate);

        Predicate result = CommonPropertySpecification
                .<Unit>accessibleForActiveRole(7L, role, null)
                .toPredicate(root, query, criteriaBuilder);

        assertSame(ownerPredicate, result);
        verify(query, never()).distinct(anyBoolean());
        verify(query, never()).subquery(Long.class);
    }
}
