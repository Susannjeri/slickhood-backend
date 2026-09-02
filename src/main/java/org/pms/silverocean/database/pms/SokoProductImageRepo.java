package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SokoProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SokoProductImageRepo extends JpaRepository<SokoProductImage, Long> {
    List<SokoProductImage> findAllByProductIdInAndActiveTrueOrderByProductIdAscDisplayOrderAsc(List<Long> productIds);
    List<SokoProductImage> findAllByProductIdAndActiveTrueOrderByDisplayOrderAsc(long productId);
}
