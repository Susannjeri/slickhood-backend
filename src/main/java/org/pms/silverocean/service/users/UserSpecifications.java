package org.pms.silverocean.service.users;


import org.pms.silverocean.database.pms.entities.Users;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

public class UserSpecifications {
    public static Specification<Users> searchUsers(Optional<String> searchParam) {
        return Specification.anyOf(emailLike(searchParam), nameLike(searchParam));
    }

    private static Specification<Users> nameLike(Optional<String> name) {
        return name.<Specification<Users>>map(s -> (document, query, criteriaBuilder) ->
                                criteriaBuilder.like(document.get("fullName"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<Users> emailLike(Optional<String> email) {
        return email.<Specification<Users>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("email"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }

    private static Specification<Users> activeIsTrue() {
        return (document, query, criteriaBuilder) ->
                criteriaBuilder.equal(document.get("active"), true);

    }
}
