package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.MaintenanceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceAttachmentRepo extends JpaRepository<MaintenanceAttachment, Long> {
    List<MaintenanceAttachment> findAllByWorkOrderIdAndActiveTrueOrderByCreatedOnAsc(long workOrderId);
    List<MaintenanceAttachment> findAllByWorkOrderIdInAndActiveTrueOrderByCreatedOnAsc(List<Long> workOrderIds);
    long countByWorkOrderIdAndActiveTrue(long workOrderId);
}
