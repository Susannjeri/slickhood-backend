package org.pms.silverocean.service.maintenance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.pms.silverocean.database.pms.entities.MaintenanceWorkOrder;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
public final class MaintenanceModels {
 private MaintenanceModels(){}
 public enum Category{PLUMBING,ELECTRICAL,APPLIANCE,STRUCTURAL,SECURITY,CLEANING,PEST_CONTROL,OTHER}
 public enum Priority{LOW,MEDIUM,HIGH,EMERGENCY}
 public enum Status{OPEN,ACKNOWLEDGED,IN_PROGRESS,COMPLETED,CANCELLED}
 public record Create(@NotNull Long unitId,@NotBlank String title,@NotBlank String description,@NotNull Category category,@NotNull Priority priority){}
 public record Update(@NotNull Status status,Long assignedProviderServiceId,ZonedDateTime scheduledAt,@PositiveOrZero BigDecimal estimatedCost,@PositiveOrZero BigDecimal actualCost,String currency,String resolutionNotes){}
 public record View(Long id,String workOrderNumber,long propertyId,long unitId,long requestedByUserId,Long assignedProviderServiceId,String title,String description,String category,String priority,String status,ZonedDateTime scheduledAt,ZonedDateTime completedAt,BigDecimal estimatedCost,BigDecimal actualCost,String currency,String resolutionNotes,ZonedDateTime createdOn){
  public View(MaintenanceWorkOrder w){this(w.getId(),w.getWorkOrderNumber(),w.getPropertyId(),w.getUnitId(),w.getRequestedByUserId(),w.getAssignedProviderServiceId(),w.getTitle(),w.getDescription(),w.getCategory(),w.getPriority(),w.getStatus(),w.getScheduledAt(),w.getCompletedAt(),w.getEstimatedCost(),w.getActualCost(),w.getCurrency(),w.getResolutionNotes(),w.getCreatedOn());}
 }
}
