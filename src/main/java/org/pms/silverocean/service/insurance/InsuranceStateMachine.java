package org.pms.silverocean.service.insurance;

import java.util.Map;
import java.util.Set;

/**
 * Central definition of legal insurance lifecycle transitions.
 * Persisted values remain strings for migration compatibility, but no workflow
 * may invent or bypass a transition locally.
 */
final class InsuranceStateMachine {
    private static final Set<String> QUOTEABLE_CASES = Set.of(
            "SUBMITTED", "ADVISER_ASSIGNED", "INFORMATION_REQUIRED", "QUOTED");
    private static final Set<String> CUSTOMER_WITHDRAWABLE_CASES = Set.of(
            "SUBMITTED", "ADVISER_ASSIGNED", "INFORMATION_REQUIRED", "QUOTED");
    private static final Map<String, Set<String>> CASE_TRANSITIONS = Map.of(
            "SUBMITTED", Set.of("INFORMATION_REQUIRED", "WITHDRAWN"),
            "ADVISER_ASSIGNED", Set.of("INFORMATION_REQUIRED", "WITHDRAWN"),
            "INFORMATION_REQUIRED", Set.of("ADVISER_ASSIGNED", "WITHDRAWN"),
            "QUOTED", Set.of("WITHDRAWN"));
    private static final Map<String, Set<String>> CLAIM_TRANSITIONS = Map.of(
            "SUBMITTED", Set.of("ACKNOWLEDGED", "DOCS_REQUIRED"),
            "ACKNOWLEDGED", Set.of("DOCS_REQUIRED", "SENT_TO_INSURER"),
            "DOCS_REQUIRED", Set.of("SENT_TO_INSURER"),
            "SENT_TO_INSURER", Set.of("ASSESSED", "APPROVED", "DECLINED"),
            "ASSESSED", Set.of("APPROVED", "DECLINED"),
            "APPROVED", Set.of("SETTLED"),
            "DECLINED", Set.of("CLOSED"),
            "SETTLED", Set.of("CLOSED"));
    private static final Map<String, Set<String>> RENEWAL_TRANSITIONS = Map.of(
            "UPCOMING", Set.of("CONTACTED"),
            "CONTACTED", Set.of("RENEWAL_QUOTED", "LAPSED"),
            "RENEWAL_QUOTED", Set.of("ACCEPTED", "LAPSED"),
            "ACCEPTED", Set.of("PAID", "LAPSED"),
            "PAID", Set.of("RENEWED"));

    private InsuranceStateMachine() {
    }

    static boolean canReceiveQuote(String status) {
        return QUOTEABLE_CASES.contains(status);
    }

    static boolean canCustomerWithdraw(String status) {
        return CUSTOMER_WITHDRAWABLE_CASES.contains(status);
    }

    static boolean canMoveCase(String from, String to) {
        return CASE_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    static boolean canMoveClaim(String from, String to) {
        return CLAIM_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    static boolean canMoveRenewal(String from, String to) {
        return RENEWAL_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
