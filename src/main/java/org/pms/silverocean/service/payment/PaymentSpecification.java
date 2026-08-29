package org.pms.silverocean.service.payment;

import jakarta.persistence.criteria.Predicate;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

public class PaymentSpecification {
    public static <T> Specification<T> searchPayment(Optional<String> searchParam) {
        return Specification.allOf(Specification.not(successfulValidations()), Specification.anyOf(transId(searchParam), phoneNumberLike(searchParam), invoiceRef(searchParam)));
    }

    private static <T> Specification<T> transId(Optional<String> transId) {
        return transId.<Specification<T>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("thirdPartyTransId"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }

    private static <T> Specification<T> phoneNumberLike(Optional<String> phone) {
        return phone.<Specification<T>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("customerAccountNumber"), "%" + s.toLowerCase() + "%"))
                .orElse(null);

    }


    private static <T> Specification<T> invoiceRef(Optional<String> ref) {
        return ref.<Specification<T>>map(s -> (document, query, criteriaBuilder) ->
                        criteriaBuilder.like(document.get("billReference"), "%" + s.toLowerCase() + "%"))
                .orElse(null);
    }

    private static <T> Specification<T> successfulValidations() {
        return (root, query, cb) -> {
            Predicate categoryIsValue = cb.equal(root.get("category"), TransactionCategory.PAYMENT_VALIDATION.name());
            Predicate statusIsZero = cb.equal(root.get("status"), TransactionCategory.PAYMENT_VALIDATION.getSuccessString());

            return cb.and(categoryIsValue, statusIsZero);
        };
    }
}
