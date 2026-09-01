package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.HelpRateLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
public interface HelpRateLimitRepo extends JpaRepository<HelpRateLimit, Long> {
    @Modifying
    @Query(value = "INSERT INTO pms_help_rate_limit " +
            "(uuid, created_on, active, subject_hash, window_start, request_count) " +
            "VALUES (UUID_TO_BIN(UUID()), NOW(6), 1, :subjectHash, :windowStart, 1) " +
            "ON DUPLICATE KEY UPDATE request_count = request_count + 1", nativeQuery = true)
    int increment(String subjectHash, LocalDateTime windowStart);

    @Query("select r.requestCount from HelpRateLimit r where r.subjectHash=:subjectHash and r.windowStart=:windowStart")
    Integer requestCount(String subjectHash, LocalDateTime windowStart);

    @Modifying
    @Query("delete from HelpRateLimit r where r.windowStart < :before")
    int deleteExpired(LocalDateTime before);
}
