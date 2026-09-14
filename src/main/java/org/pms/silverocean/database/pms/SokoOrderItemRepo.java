package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SokoOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SokoOrderItemRepo extends JpaRepository<SokoOrderItem, Long> {
    List<SokoOrderItem> findAllByOrderIdAndActiveTrueOrderById(long orderId);
    List<SokoOrderItem> findAllByOrderIdInAndActiveTrueOrderByOrderIdAscIdAsc(List<Long> orderIds);
    @org.springframework.data.jpa.repository.Query("select count(i) from SokoOrderItem i join SokoOrder o on o.id=i.orderId where i.productId=:productId and i.active=true and o.active=true and o.status not in ('COMPLETED','CANCELLED','EXPIRED','RETURNED')")
    long countOpenOrdersForProduct(long productId);
}
