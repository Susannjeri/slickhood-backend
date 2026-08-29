package org.pms.silverocean.service.param;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.pms.silverocean.database.pms.entities.Param;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

public class ParamSpecification {
    public static Specification<Param> searchParam(Optional<String> searchParam) {
        return Specification.anyOf(createdByLike(searchParam), nameLike(searchParam)).and(activeTrue(true));
    }

    private static Specification<Param> nameLike(Optional<String> name) {
        return name.<Specification<Param>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("name"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<Param> createdByLike(Optional<String> email) {
        return email.<Specification<Param>>map(s -> (root, query, cb) -> {
            Join<Object, Object> creatorJoin = root.join("creator", JoinType.LEFT);
            return cb.like(cb.lower(creatorJoin.get("email")), "%" + s.toLowerCase() + "%");
        }).orElse(null);
    }

    private static Specification<Param> activeTrue(boolean active) {
        return  (document, query, criteriaBuilder) ->
                criteriaBuilder.equal(document.get("active"), active);
    }
}
