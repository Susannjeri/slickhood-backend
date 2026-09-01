package org.pms.silverocean.service.soko;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class SokoRequests {
    private SokoRequests() {}

    public record StoreUpsert(
            @NotBlank @Size(max=160) String name,
            @Size(max=1000) String description,
            @Size(max=30) String phoneNumber,
            @Size(max=500) String address,
            Double latitude,
            Double longitude,
            @DecimalMin("1.00") BigDecimal serviceRadiusKm,
            boolean pickupEnabled,
            boolean deliveryEnabled,
            @DecimalMin("0.00") BigDecimal deliveryFee,
            @NotBlank @Size(min=3,max=3) String currency,
            Long paymentAccountId) {}

    public record ProductUpsert(
            @NotNull Long storeId,
            @NotBlank @Size(max=180) String name,
            @Size(max=1500) String description,
            @NotBlank @Size(max=80) String category,
            @NotBlank @Size(max=40) String unit,
            @NotNull @DecimalMin("0.01") BigDecimal price,
            @Min(0) int stockQuantity,
            @Size(max=800) String imageUrl) {}

    public record CheckoutItem(@NotNull Long productId, @Min(1) @Max(10_000) int quantity) {}

    public record Checkout(
            @NotNull Long storeId,
            @NotEmpty @Size(max=50) List<@Valid CheckoutItem> items,
            @NotBlank @Pattern(regexp="(?i)DELIVERY|PICKUP") String deliveryMethod,
            @Size(max=500) String deliveryAddress,
            @NotBlank @Size(max=30) String customerPhone,
            @Size(max=1000) String notes,
            Long destinationUnitId) {}

    public record Dispatch(
            Long riderId,
            @Size(max=150) String courierName,
            @Size(max=30) String courierPhone,
            @Size(max=20) String vehiclePlate,
            @NotNull LocalDateTime expectedArrivalTime) {}

    public record RiderUpsert(
            @NotNull Long storeId,
            @NotBlank @Size(max=30) String riderType,
            @NotBlank @Size(max=150) String displayName,
            @NotBlank @Size(max=30) String phoneNumber,
            @Size(max=180) String email,
            @Size(max=60) String vehicleType,
            @Size(max=20) String vehiclePlate,
            @Size(max=1000) String notes) {}

    public record DeliveryConfirmation(@NotBlank @Pattern(regexp="\\d{6}") String code,@Size(max=160) String recipientName,@Size(max=500) String proofReference) { public DeliveryConfirmation(String code){this(code,null,null);} }
    public record Cancellation(@NotBlank @Size(max=1000) String reason) {}
    public enum FinanceType { REFUND, SETTLEMENT }
    public enum FinanceStatus { REQUESTED, PROCESSING, CONFIRMED, FAILED }
    public record FinanceUpdate(@NotNull FinanceType type,@NotNull FinanceStatus status,@NotNull @DecimalMin("0.01") BigDecimal amount,@Size(max=120) String providerReference) {}
    public record ModerationDecision(@NotBlank @Pattern(regexp="APPROVE|REJECT|SUSPEND|REACTIVATE") String decision,@Size(max=1000) String reason) {}
}
