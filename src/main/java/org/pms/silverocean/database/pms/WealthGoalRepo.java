package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface WealthGoalRepo extends JpaRepository<WealthGoal,Long>{
 List<WealthGoal> findAllByOwnerUserIdAndActiveTrueOrderByTargetDate(long ownerUserId);
 Optional<WealthGoal> findByIdAndOwnerUserIdAndActiveTrue(long id,long ownerUserId);
 long countByOwnerUserIdAndActiveTrue(long ownerUserId);
}
