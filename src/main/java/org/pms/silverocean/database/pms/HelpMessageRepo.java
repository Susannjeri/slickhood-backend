package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.HelpMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HelpMessageRepo extends JpaRepository<HelpMessage, Long> {
    List<HelpMessage> findByConversationIdAndActiveTrueOrderByCreatedOnAsc(long conversationId);
    List<HelpMessage> findByConversationIdAndActiveTrueOrderByCreatedOnDesc(long conversationId, Pageable pageable);
}
