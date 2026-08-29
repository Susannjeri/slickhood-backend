package org.pms.silverocean.service.audit;

import org.pms.silverocean.database.audit.entities.AuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

public class AuditSpecifications {
    public static Specification<AuditLog> searchAuditLog(Optional<String> searchParam) {
        return Specification.anyOf(entityNameLike(searchParam), actionLike(searchParam), valueLike(searchParam), usernameLike(searchParam));
    }

    private static Specification<AuditLog> entityNameLike(Optional<String> entityName) {
        return entityName.<Specification<AuditLog>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("entityName"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<AuditLog> actionLike(Optional<String> action) {
        return action.<Specification<AuditLog>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("action"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<AuditLog> valueLike(Optional<String> value) {
        return value.<Specification<AuditLog>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("value"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<AuditLog> usernameLike(Optional<String> username) {
        return username.<Specification<AuditLog>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("username"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }

//    private static Specification<AuditLog> createdOnLike(Optional<String> createdOn) {
//        return createdOn.<Specification<AuditLog>>map(s -> (document, query, criteriaBuilder) ->
//                        criteriaBuilder.like(document.get("createdOn"), "%" + s.toLowerCase() + "%"))
//                .orElse(null);
//
//    }
}
