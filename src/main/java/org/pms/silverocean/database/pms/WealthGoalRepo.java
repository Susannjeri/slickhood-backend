package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WealthGoalRepo extends JpaRepository<WealthGoal,Long>{List<WealthGoal> findAllByOwnerUserIdAndActiveTrueOrderByTargetDate(long ownerUserId);}
