package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.HelpConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;

public interface HelpConversationRepo extends JpaRepository<HelpConversation, Long> {
    Page<HelpConversation> findByUserIdAndActiveTrueOrderByLastMessageAtDesc(long userId, Pageable pageable);
    Page<HelpConversation> findByStatusInAndActiveTrueOrderByPriorityRankDescWaitingSinceAsc(Collection<String> statuses, Pageable pageable);
    Optional<HelpConversation> findByIdAndUserIdAndActiveTrue(long id, long userId);
    Optional<HelpConversation> findByIdAndActiveTrue(long id);
    Optional<HelpConversation> findByTicketNumberAndGuestTokenHashAndActiveTrue(String ticketNumber, String guestTokenHash);
    Optional<HelpConversation> findByGuestTokenHashAndActiveTrue(String guestTokenHash);
    boolean existsByTicketNumber(String ticketNumber);
    java.util.List<HelpConversation> findTop100ByStatusInAndActiveTrueAndSlaDueAtBeforeAndSlaBreachedAtIsNullOrderBySlaDueAtAsc(
            Collection<String> statuses, java.time.LocalDateTime now);
    long countByStatusInAndActiveTrue(Collection<String> statuses);
    long countByStatusAndAssignedToUserIdIsNullAndActiveTrue(String status);
    long countBySlaBreachedAtIsNotNullAndStatusInAndActiveTrue(Collection<String> statuses);
    long countByStatusAndActiveTrue(String status);
}
