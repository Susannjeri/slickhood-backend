package org.pms.silverocean.service.soko;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
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
            @DecimalMin("1.00") @DecimalMax("100.00") BigDecimal serviceRadiusKm,
            boolean pickupEnabled,
            boolean deliveryEnabled,
            @DecimalMin(value="0.00",message="Delivery fee cannot be negative.")
            @DecimalMax(value="1000000.00",message="Delivery fee cannot exceed KES 1,000,000.")
            @Digits(integer=7,fraction=2,message="Delivery fee may have at most two decimal places.") BigDecimal deliveryFee,
            @NotBlank @Size(min=3,max=3) String currency,
            Long paymentAccountId) {}

    public record ProductUpsert(
            @NotNull Long storeId,
            @NotBlank @Size(max=180) String name,
            @Size(max=1500) String description,
            @NotBlank @Size(max=80) String category,
            @NotBlank @Size(max=40) String unit,
            @NotNull @DecimalMin(value="0.01",message="Product price must be at least KES 0.01.")
            @DecimalMax(value="10000000.00",message="Product price cannot exceed KES 10,000,000.")
            @Digits(integer=8,fraction=2,message="Product price may have at most two decimal places.") BigDecimal price,
            @Min(value=0,message="Product stock cannot be negative.")
            @Max(value=1000000,message="Product stock cannot exceed 1,000,000 units.") int stockQuantity,
            @Size(max=800) String imageUrl,
            @Size(max=30) List<@Valid ProductVariation> variations) {
        public ProductUpsert(Long storeId,String name,String description,String category,String unit,BigDecimal price,int stockQuantity,String imageUrl){this(storeId,name,description,category,unit,price,stockQuantity,imageUrl,List.of());}
    }

    public record ProductVariation(Long id,
                                   @NotBlank @Size(max=80) String name,
                                   @NotBlank @Size(max=120) String value,
                                   @DecimalMin(value="0.00",message="Variation price adjustment cannot be negative.")
                                   @DecimalMax(value="10000000.00",message="Variation price adjustment cannot exceed KES 10,000,000.")
                                   @Digits(integer=8,fraction=2,message="Variation price adjustment may have at most two decimal places.") BigDecimal priceAdjustment,
                                   @NotNull @Min(value=0,message="Variation stock cannot be negative.")
                                   @Max(value=1000000,message="Variation stock cannot exceed 1,000,000 units.") Integer stockQuantity) {
        public ProductVariation(String name,String value,BigDecimal priceAdjustment,Integer stockQuantity){this(null,name,value,priceAdjustment,stockQuantity);}
    }

    public record CheckoutItem(@NotNull Long productId, @Min(1) @Max(100) int quantity, Long variationId) {
        public CheckoutItem(Long productId,int quantity){this(productId,quantity,null);}
    }

    public record Checkout(
            @NotNull Long storeId,
            @NotEmpty @Size(max=25) List<@Valid CheckoutItem> items,
            @NotBlank @Pattern(regexp="(?i)DELIVERY|PICKUP") String deliveryMethod,
            @Size(max=500) String deliveryAddress,
            @NotBlank @Size(max=30) String customerPhone,
            @Size(max=1000) String notes,
            Long destinationUnitId,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double deliveryLatitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double deliveryLongitude) {
        public Checkout(Long storeId,List<CheckoutItem> items,String deliveryMethod,String deliveryAddress,
                        String customerPhone,String notes,Long destinationUnitId){
            this(storeId,items,deliveryMethod,deliveryAddress,customerPhone,notes,destinationUnitId,null,null);
        }
    }

    public record Dispatch(
            Long riderId,
            @Size(max=150) String courierName,
            @Size(max=30) String courierPhone,
            @Size(max=20) String vehiclePlate,
            LocalDateTime expectedArrivalTime) {}

    public record RiderUpsert(
            @NotNull Long storeId,
            @NotBlank @Size(max=30) String riderType,
            @NotBlank @Size(max=150) String displayName,
            @NotBlank @Size(max=30) String phoneNumber,
            @NotBlank(message="Enter the rider ID or company registration number.")
            @Size(max=40,message="The rider ID or company registration number must be 40 characters or fewer.")
            @Pattern(regexp="[A-Za-z0-9-]+",message="Use only letters, numbers and hyphens for the rider ID or company registration number.")
            String nationalIdNumber,
            @Size(max=180) String email,
            @Size(max=60) String vehicleType,
            @Size(max=20) String vehiclePlate,
            @Size(max=1000) String notes) {}

    public record RiderVerificationConfirm(
            @NotBlank(message="Enter the six-digit rider confirmation code.")
            @Pattern(regexp="\\d{6}",message="The rider confirmation code must contain six digits.")
            String code) {}

    public record DeliveryConfirmation(@NotBlank @Pattern(regexp="\\d{6}") String code,@Size(max=160) String recipientName,@Size(max=500) String proofReference) { public DeliveryConfirmation(String code){this(code,null,null);} }
    public record PickupConfirmation(
            @NotBlank(message="Enter the buyer's six-digit pickup code.")
            @Pattern(regexp="\\d{6}",message="The pickup code must contain six digits.")
            String code,
            @Size(max=160) String recipientName) {
        public PickupConfirmation(String code){this(code,null);}
    }
    public record Cancellation(@NotBlank @Size(max=1000) String reason) {}
    public enum FinanceType { REFUND, SETTLEMENT }
    public enum FinanceStatus { REQUESTED, PROCESSING, CONFIRMED, FAILED }
    public record FinanceUpdate(@NotNull FinanceType type,@NotNull FinanceStatus status,
                                @NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2,message="Finance amount may have at most two decimal places.") BigDecimal amount,
                                @Size(max=120) String providerReference) {}
    public record ModerationDecision(@NotBlank @Pattern(regexp="APPROVE|REJECT|SUSPEND|REACTIVATE") String decision,@Size(max=1000) String reason) {}
    public record RiderDecision(@NotBlank @Pattern(regexp="VERIFY|ACTIVATE|SUSPEND|REJECT") String decision,
                                @Size(max=1000) String reason) {}
    public record DeliveryException(@NotBlank @Size(max=1000) String reason) {}
    public record CodeReissue(@NotBlank @Size(max=1000) String reason) {}
    public record DeliveryCodeRecoveryConfirm(@NotBlank @Pattern(regexp="\\d{6}") String otp) {}
}
