package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SokoOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SokoOrderItemRepo extends JpaRepository<SokoOrderItem, Long> {
    List<SokoOrderItem> findAllByOrderIdAndActiveTrueOrderById(long orderId);
    List<SokoOrderItem> findAllByOrderIdInAndActiveTrueOrderByOrderIdAscIdAsc(List<Long> orderIds);
}
