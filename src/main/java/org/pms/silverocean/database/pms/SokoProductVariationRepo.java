package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.SokoProductVariation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SokoProductVariationRepo extends JpaRepository<SokoProductVariation, Long> {
    List<SokoProductVariation> findAllByProductIdAndActiveTrueOrderByNameAscValueAsc(long productId);
    List<SokoProductVariation> findAllByProductIdInAndActiveTrueOrderByProductIdAscNameAscValueAsc(List<Long> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from SokoProductVariation v where v.id=:id and v.productId=:productId and v.active=true")
    Optional<SokoProductVariation> findForUpdate(long id,long productId);
}
