package org.pms.silverocean.service.account.dao;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class AccountSpecifications {

    public static Specification<PaymentAccount> searchAccounts(final Optional<String> landlordEmail, Optional<PaymentChannel> channel, boolean active, boolean verified) {
        List<Specification<PaymentAccount>> specs = Stream.of(
                        landlordEmailLike(landlordEmail),
                        paymentChannelEquals(channel),
                        verifiedTrueOrFalse(verified),
                        activeTrueOrFalse(active))
                .filter(Objects::nonNull)
                .toList();

        return Specification.allOf(specs);
    }

    private static Specification<PaymentAccount> landlordEmailLike(Optional<String> landlordEmail) {
        return landlordEmail.<Specification<PaymentAccount>>map(s -> (document, query, criteriaBuilder) -> {
                    Root<Users> landlord = query.from(Users.class);
                    Predicate joinCondition = criteriaBuilder.equal(document.get("createdBy"), landlord.get("id"));
                    Predicate emailMatch = criteriaBuilder.like(
                            criteriaBuilder.lower(landlord.get("email")),
                            "%" + s.toLowerCase() + "%");
                    return criteriaBuilder.and(joinCondition, emailMatch);
                })
                .orElse(null);
    }

    private static Specification<PaymentAccount> paymentChannelEquals(Optional<PaymentChannel> channel) {
        return channel.<Specification<PaymentAccount>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.equal(document.get("payToUserId"), s))
                .orElse(null);
    }

    private static Specification<PaymentAccount> activeTrueOrFalse(boolean active) {
        return (document, query, criteriaBuilder) ->
                criteriaBuilder.equal(document.get("active"), active);
    }

    private static Specification<PaymentAccount> verifiedTrueOrFalse(boolean verified) {
        return (document, query, criteriaBuilder) ->
                criteriaBuilder.equal(document.get("verified"), verified);
    }
}
