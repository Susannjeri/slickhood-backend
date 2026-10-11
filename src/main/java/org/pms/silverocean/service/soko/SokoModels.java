package org.pms.silverocean.service.soko;

import org.pms.silverocean.database.pms.entities.SokoOrder;
import org.pms.silverocean.database.pms.entities.SokoOrderItem;
import org.pms.silverocean.database.pms.entities.SokoProduct;
import org.pms.silverocean.database.pms.entities.SokoRider;
import org.pms.silverocean.database.pms.entities.SokoStore;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public final class SokoModels {
    private SokoModels() {}

    /** Public catalogue representation. Never expose entity ownership, moderation or payment-routing fields. */
    public record PublicProduct(long id, long storeId, String name, String description, String category, String unit,
                                BigDecimal price, String currency, int stockQuantity, String imageUrl,
                                String status, String variationsJson) {
        public static PublicProduct from(SokoProduct product) {
            return new PublicProduct(product.getId(), product.getStoreId(), product.getName(), product.getDescription(),
                    product.getCategory(), product.getUnit(), product.getPrice(), product.getCurrency(),
                    product.getStockQuantity(), product.getImageUrl(), product.getStatus(), product.getVariationsJson());
        }
    }

    /** Customer-visible shop details. Internal owner and receiving-account identifiers stay private. */
    public record PublicStore(long id, String name, String description, String storePhoneNumber, String address,
                              Double latitude, Double longitude, BigDecimal serviceRadiusKm,
                              boolean pickupEnabled, boolean deliveryEnabled, BigDecimal deliveryFee,
                              String currency) {
        public static PublicStore from(SokoStore store) {
            return new PublicStore(store.getId(), store.getName(), store.getDescription(), store.getPhoneNumber(),
                    store.getAddress(), store.getLatitude(), store.getLongitude(), store.getServiceRadiusKm(),
                    store.isPickupEnabled(), store.isDeliveryEnabled(), store.getDeliveryFee(), store.getCurrency());
        }
    }

    public record CatalogProduct(PublicProduct product, String storeName, String storeAddress, String storePhoneNumber,
                                 boolean deliveryEnabled, boolean pickupEnabled, Double distanceKm,
                                 BigDecimal serviceRadiusKm, List<String> imageUrls, BigDecimal deliveryFee) {}
    /** Public directory entry. Exact coordinates, contacts and all ownership/payment fields stay private. */
    public record SellerSummary(long id, String name, String address, boolean pickupEnabled,
                                boolean deliveryEnabled, BigDecimal deliveryFee, String currency) {
        public static SellerSummary from(SokoStore store) {
            return new SellerSummary(store.getId(),store.getName(),store.getAddress(),store.isPickupEnabled(),
                    store.isDeliveryEnabled(),store.getDeliveryFee()==null?BigDecimal.ZERO:store.getDeliveryFee(),
                    store.getCurrency());
        }
    }
    /** An authenticated customer's system-linked, server-authoritative delivery destination. */
    public record DeliveryDestination(long unitId, long propertyId, String label, String address,
                                      double latitude, double longitude, String source, boolean preferred) {}
    public record ProductImages(long productId, List<String> imageUrls) {}
    public record StoreDetail(PublicStore store, List<PublicProduct> products) {}
    /** Seller-facing rider view. OTP material, internal user links and moderation notes never leave the service. */
    public record RiderSummary(long id,long storeId,String riderType,String displayName,String phoneNumber,
                               String nationalIdNumber,String vehicleType,String vehiclePlate,String status,
                               String availability,boolean phoneConfirmed,String phoneConfirmationStatus,
                               ZonedDateTime phoneConfirmedAt,boolean verified,int completedDeliveries) {
        public static RiderSummary from(SokoRider rider) {
            return new RiderSummary(rider.getId(),rider.getStoreId(),rider.getRiderType(),rider.getDisplayName(),
                    rider.getPhoneNumber(),rider.getNationalIdNumber(),rider.getVehicleType(),rider.getVehiclePlate(),
                    rider.getStatus(),rider.getAvailability(),rider.isPhoneConfirmed(),
                    rider.getPhoneConfirmationStatus(),rider.getPhoneConfirmationConfirmedAt(),rider.isVerified(),
                    rider.getCompletedDeliveries());
        }
    }
    public record RiderVerificationResult(RiderSummary rider, String confirmationStatus, String message) {}
    /** Buyer/merchant order view. Persistence, routing and recovery-control metadata stay server-side. */
    public record OrderView(long id, String orderNumber, long storeId, String status, String paymentStatus,
                            String invoiceRef, String deliveryMethod, String deliveryAddress,
                            Double deliveryLatitude, Double deliveryLongitude, String customerPhone, String notes,
                            Long destinationUnitId, BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal total,
                            String currency, ZonedDateTime placedAt, ZonedDateTime confirmedAt,
                            ZonedDateTime dispatchedAt, ZonedDateTime completedAt, Long riderId,
                            String courierName, String courierPhone, String courierVehiclePlate,
                            boolean deliveryCodeVerified, ZonedDateTime reservationExpiresAt,
                            ZonedDateTime cancelledAt, String cancellationReason,
                            String refundStatus, BigDecimal refundedAmount, String deliveryRecipientName,
                            boolean deliveryProofUploaded,
                            ZonedDateTime deliveryProofAt, ZonedDateTime expectedArrivalAt, ZonedDateTime assignedAt,
                            ZonedDateTime assignmentAcceptedAt, ZonedDateTime collectedAt,
                            ZonedDateTime deliveryFailedAt, ZonedDateTime returnedAt, String deliveryExceptionReason,
                            ZonedDateTime deliveryRecoveryRequestedAt) {
        public static OrderView from(SokoOrder order) {
            return from(order,true);
        }
        public static OrderView from(SokoOrder order,boolean showCustomerDeliveryDetails) {
            return new OrderView(order.getId(),order.getOrderNumber(),order.getStoreId(),order.getStatus(),
                    order.getPaymentStatus(),order.getInvoiceRef(),order.getDeliveryMethod(),
                    showCustomerDeliveryDetails?order.getDeliveryAddress():null,
                    showCustomerDeliveryDetails?order.getDeliveryLatitude():null,
                    showCustomerDeliveryDetails?order.getDeliveryLongitude():null,
                    showCustomerDeliveryDetails?order.getCustomerPhone():null,
                    showCustomerDeliveryDetails?order.getNotes():null,
                    showCustomerDeliveryDetails?order.getDestinationUnitId():null,
                    order.getSubtotal(),order.getDeliveryFee(),order.getTotal(),
                    order.getCurrency(),order.getPlacedAt(),order.getConfirmedAt(),order.getDispatchedAt(),
                    order.getCompletedAt(),order.getRiderId(),order.getCourierName(),order.getCourierPhone(),
                    order.getCourierVehiclePlate(),order.isDeliveryCodeVerified(),order.getReservationExpiresAt(),
                    order.getCancelledAt(),order.getCancellationReason(),
                    order.getRefundStatus(),order.getRefundedAmount(),order.getDeliveryRecipientName(),
                    order.getDeliveryProofReference()!=null,order.getDeliveryProofAt(),
                    order.getExpectedArrivalAt(),order.getAssignedAt(),order.getAssignmentAcceptedAt(),order.getCollectedAt(),
                    order.getDeliveryFailedAt(),order.getReturnedAt(),order.getDeliveryExceptionReason(),
                    order.getDeliveryRecoveryRequestedAt());
        }
    }
    public record OrderItemView(Long id, long productId, String productName, Long variationId,
                                String variationName, String variationValue, String unit,
                                BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        public static OrderItemView from(SokoOrderItem item) {
            return new OrderItemView(item.getId(),item.getProductId(),item.getProductName(),item.getVariationId(),
                    item.getVariationName(),item.getVariationValue(),item.getUnit(),item.getUnitPrice(),
                    item.getQuantity(),item.getLineTotal());
        }
    }
    public record OrderDetail(OrderView order, String storeName, String storeAddress, String storePhoneNumber,
                              Double storeLatitude, Double storeLongitude,
                              Long paymentAccountId, String paymentChannel, Long invoiceId,
                              List<OrderItemView> items) {}
    /** Least-privilege Finance work item; customer contact and delivery secrets are intentionally omitted. */
    public record RefundQueueItem(long orderId,String orderNumber,String storeName,String invoiceRef,
                                  String orderStatus,String paymentStatus,String refundStatus,String currency,
                                  BigDecimal orderTotal,BigDecimal refundedAmount,BigDecimal refundRequestedAmount,
                                  BigDecimal remainingRefundableAmount,String reason,ZonedDateTime requestedAt,
                                  java.time.LocalDateTime lastUpdatedAt) {}
    /** Mutation receipt for Finance. Customer, delivery, rider and receiving-account data are intentionally absent. */
    public record FinanceAction(long orderId,String orderNumber,String invoiceRef,String operationType,
                                String operationStatus,String currency,BigDecimal amount,String providerReference,
                                String paymentStatus,String refundStatus,String settlementStatus,
                                java.time.LocalDateTime updatedAt) {
        public static FinanceAction from(SokoOrder order,SokoRequests.FinanceType type) {
            boolean refund=type==SokoRequests.FinanceType.REFUND;
            String status=refund?order.getRefundStatus():order.getSettlementStatus();
            BigDecimal amount=refund
                    ?("CONFIRMED".equals(status)?order.getRefundedAmount():order.getRefundRequestedAmount())
                    :order.getSettledAmount();
            String reference=refund?order.getRefundReference():order.getSettlementReference();
            return new FinanceAction(order.getId(),order.getOrderNumber(),order.getInvoiceRef(),type.name(),status,
                    order.getCurrency(),amount,reference,order.getPaymentStatus(),order.getRefundStatus(),
                    order.getSettlementStatus(),order.getLastModifiedDate());
        }
    }
    /** Least-privilege delivery view: riders never receive payment, settlement or recovery-control fields. */
    public record RiderOrder(long id, String orderNumber, long storeId, String status, String deliveryMethod,
                             String deliveryAddress, Double deliveryLatitude, Double deliveryLongitude,
                             String customerPhone, String deliveryNotes, Long destinationUnitId,
                             Long riderId, String courierName, String courierPhone, String courierVehiclePlate,
                             boolean deliveryProofUploaded,
                             ZonedDateTime placedAt, ZonedDateTime confirmedAt, ZonedDateTime expectedArrivalAt,
                             ZonedDateTime assignedAt, ZonedDateTime assignmentAcceptedAt, ZonedDateTime collectedAt,
                             ZonedDateTime dispatchedAt, ZonedDateTime deliveryFailedAt, ZonedDateTime returnedAt,
                             String deliveryExceptionReason, ZonedDateTime completedAt) {
        public static RiderOrder from(SokoOrder order) {
            boolean showDeliveryDetails=order.getAssignmentAcceptedAt()!=null
                    && ("ASSIGNMENT_ACCEPTED".equals(order.getStatus())||"DISPATCHED".equals(order.getStatus()));
            return new RiderOrder(order.getId(), order.getOrderNumber(), order.getStoreId(), order.getStatus(),
                    order.getDeliveryMethod(), showDeliveryDetails?order.getDeliveryAddress():null,
                    showDeliveryDetails?order.getDeliveryLatitude():null,
                    showDeliveryDetails?order.getDeliveryLongitude():null,
                    showDeliveryDetails?order.getCustomerPhone():null,
                    showDeliveryDetails?order.getNotes():null,
                    showDeliveryDetails?order.getDestinationUnitId():null,
                    order.getRiderId(), order.getCourierName(), order.getCourierPhone(),
                    order.getCourierVehiclePlate(), order.getDeliveryProofReference()!=null, order.getPlacedAt(), order.getConfirmedAt(), order.getExpectedArrivalAt(),
                    order.getAssignedAt(), order.getAssignmentAcceptedAt(), order.getCollectedAt(), order.getDispatchedAt(),
                    order.getDeliveryFailedAt(), order.getReturnedAt(), order.getDeliveryExceptionReason(), order.getCompletedAt());
        }
    }
    public record RiderItem(Long id, long productId, String productName, Long variationId,
                            String variationName, String variationValue, String unit, int quantity) {
        public static RiderItem from(SokoOrderItem item) {
            return new RiderItem(item.getId(), item.getProductId(), item.getProductName(), item.getVariationId(),
                    item.getVariationName(), item.getVariationValue(), item.getUnit(), item.getQuantity());
        }
    }
    public record RiderAssignment(RiderOrder order, String storeName, String storeAddress, String storePhoneNumber,
                                  Double storeLatitude, Double storeLongitude,
                                  List<RiderItem> items) {}
    /** Mutation acknowledgement shared by linked riders and merchant-managed riders. */
    public record OrderAction(long orderId, String orderNumber, String status, boolean deliveryProofUploaded,
                              ZonedDateTime assignedAt, ZonedDateTime assignmentAcceptedAt,
                              ZonedDateTime collectedAt, ZonedDateTime dispatchedAt,
                              ZonedDateTime deliveryFailedAt, ZonedDateTime returnedAt,
                              ZonedDateTime completedAt) {
        public static OrderAction from(SokoOrder order) {
            return new OrderAction(order.getId(),order.getOrderNumber(),order.getStatus(),
                    order.getDeliveryProofReference()!=null,order.getAssignedAt(),order.getAssignmentAcceptedAt(),
                    order.getCollectedAt(),order.getDispatchedAt(),order.getDeliveryFailedAt(),order.getReturnedAt(),
                    order.getCompletedAt());
        }
    }
    public record PublicRiderItem(String name, String unit, int quantity) {
        public static PublicRiderItem from(SokoOrderItem item) {
            return new PublicRiderItem(item.getProductName(), item.getUnit(), item.getQuantity());
        }
    }
    public record PublicRiderDelivery(String address, Double latitude, Double longitude,
                                      String customerPhone) {}
    /**
     * Token-scoped rider view. Delivery contact and coordinates are absent until
     * the rider has personally accepted the assignment.
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record PublicRiderAssignment(String orderReference, String status, String shopName,
                                        String pickupAddress, ZonedDateTime expectedArrivalAt,
                                        int itemCount, List<PublicRiderItem> items,
                                        boolean deliveryProofUploaded,
                                        PublicRiderDelivery delivery) {}
    public record RiderAssignmentDecision(String orderReference, String status) {}
    public record AssignmentLinkDelivery(String orderReference, String status,
                                         ZonedDateTime expiresAt, String message) {}
    /** Moderation view. Rider identity documents, direct contact and private notes stay outside the admin API. */
    public record AdminRiderSummary(long id,long storeId,String riderType,String displayName,String maskedPhoneNumber,
                                    String vehicleType,String vehiclePlate,String status,String availability,
                                    boolean phoneConfirmed,boolean verified,int completedDeliveries,
                                    ZonedDateTime createdAt,ZonedDateTime phoneConfirmedAt,ZonedDateTime verifiedAt,
                                    java.time.LocalDateTime updatedAt) {
        public static AdminRiderSummary from(SokoRider rider) {
            String phone=rider.getPhoneNumber();
            String maskedPhone=phone==null?null:"******"+phone.substring(Math.max(0,phone.length()-4));
            return new AdminRiderSummary(rider.getId(),rider.getStoreId(),rider.getRiderType(),rider.getDisplayName(),
                    maskedPhone,rider.getVehicleType(),rider.getVehiclePlate(),rider.getStatus(),rider.getAvailability(),
                    rider.isPhoneConfirmed(),rider.isVerified(),rider.getCompletedDeliveries(),rider.getCreatedOn(),
                    rider.getPhoneConfirmationConfirmedAt(),rider.getVerifiedAt(),rider.getLastModifiedDate());
        }
    }
    /** Operational moderation view. Buyer identity, destination, payment routing, codes and proof stay private. */
    public record AdminOrderSummary(long id,String orderNumber,long storeId,String storeName,
                                    String status,String paymentStatus,String deliveryMethod,
                                    BigDecimal subtotal,BigDecimal deliveryFee,BigDecimal total,String currency,
                                    boolean deliveryCodeVerified,ZonedDateTime deliveryRecoveryRequestedAt,
                                    ZonedDateTime reservationExpiresAt,ZonedDateTime placedAt,ZonedDateTime confirmedAt,
                                    ZonedDateTime assignedAt,ZonedDateTime dispatchedAt,ZonedDateTime completedAt,
                                    ZonedDateTime cancelledAt,ZonedDateTime deliveryFailedAt,ZonedDateTime returnedAt,
                                    java.time.LocalDateTime updatedAt) {
        public static AdminOrderSummary from(SokoOrder order) {
            return new AdminOrderSummary(order.getId(),order.getOrderNumber(),order.getStoreId(),
                    order.getStoreNameSnapshot(),order.getStatus(),order.getPaymentStatus(),order.getDeliveryMethod(),
                    order.getSubtotal(),order.getDeliveryFee(),order.getTotal(),order.getCurrency(),
                    order.isDeliveryCodeVerified(),order.getDeliveryRecoveryRequestedAt(),order.getReservationExpiresAt(),
                    order.getPlacedAt(),order.getConfirmedAt(),order.getAssignedAt(),
                    order.getDispatchedAt(),order.getCompletedAt(),order.getCancelledAt(),order.getDeliveryFailedAt(),
                    order.getReturnedAt(),order.getLastModifiedDate());
        }
    }
    public record AdminSummary(long stores,long pendingStores,long publishedStores,long products,long publishedProducts,long orders,long activeOrders) {}
}
